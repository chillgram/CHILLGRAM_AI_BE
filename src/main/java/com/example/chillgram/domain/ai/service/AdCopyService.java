package com.example.chillgram.domain.ai.service;

import com.example.chillgram.domain.ai.dto.AdGuideAiRequest;
import com.example.chillgram.domain.ai.dto.AdGuideAiResponse;
import com.example.chillgram.domain.ai.dto.FinalCopyRequest;
import com.example.chillgram.domain.ai.dto.FinalCopyResponse;
import com.example.chillgram.domain.ai.dto.GuidelineRequest;
import com.example.chillgram.domain.ai.dto.GuidelineResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemini 기반 광고 가이드라인 및 최종 카피 생성 서비스
 */
@Slf4j
@Service
public class AdCopyService {

    private final ChatClient chatClient;

    public AdCopyService(ObjectProvider<ChatClient> chatClientProvider) {
        this.chatClient = chatClientProvider.getIfAvailable();
    }

    private void checkAiEnabled() {
        if (this.chatClient == null) {
            log.error("========== AI 서비스 호출 시도 실패 ==========");
            throw new IllegalStateException("현재 AI 서비스가 비활성화되어 있습니다. 설정(API Key 등)을 확인해주세요.");
        }
    }

    // =========================================================
    // 신규: 광고 가이드라인 생성 (ad-guides 전용)
    // =========================================================

    /**
     * WebFlux에서 안전하게 쓰기 위한 Reactive 래퍼
     * - ChatClient 호출이 블로킹될 수 있으므로 boundedElastic로 분리
     */
    public Mono<AdGuideAiResponse> generateAdGuidesMono(AdGuideAiRequest request) {
        return Mono.fromCallable(() -> generateAdGuides(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 광고 가이드라인 생성(동기)
     * - GUIDE_START/END 포맷 강제
     * - 섹션/내용 파싱하여 구조화 반환
     */
    public AdGuideAiResponse generateAdGuides(AdGuideAiRequest request) {
        checkAiEnabled();

        String prompt = """
                당신은 한국 시장의 퍼포먼스 마케터이자 카피라이터입니다.
                아래 입력을 바탕으로 '광고 가이드라인'을 작성하세요.
                반드시 지정된 포맷을 지키세요. 포맷을 어기면 실패로 간주됩니다.

                [GUIDE_START]
                섹션: 핵심 메시지
                내용: (1문장 + 서브 메시지 3개)
                섹션: 타깃/상황
                내용: (2~3줄)
                섹션: 톤&매너(Do/Don't)
                내용: (Do 3개 / Don't 3개)
                섹션: 금지표현/리스크 체크
                내용: (체크리스트 형태)
                섹션: 플랫폼별 변형(인스타/배너/쇼츠)
                내용: (각 3줄씩)
                섹션: A/B 테스트 아이디어(가설 포함)
                내용: (5개)
                [GUIDE_END]

                [입력]
                - productId: %d
                - 상품명: %s
                - baseDate: %s
                - 프로젝트 제목: %s
                - 광고 목표: %s
                - 요청문: %s
                - 선택 키워드: %s
                - 광고 초점: %s

                [트렌드]
                - 트렌드 키워드: %s
                - 해시태그: %s
                - 스타일 요약: %s
                """.formatted(
                request.productId(),
                request.productName(),
                request.baseDate(),
                request.projectTitle(),
                request.adGoal(),
                request.requestText(),
                request.selectedKeywords(),
                request.adFocus(),
                request.trendKeywords(),
                request.hashtags(),
                request.styleSummary()
        );

        String response = chatClient.prompt().user(prompt).call().content();
        log.info("광고 가이드라인 생성 응답: {}", response);

        return parseGuideSections(response);
    }

    private AdGuideAiResponse parseGuideSections(String response) {
        String clean = (response == null ? "" : response).replaceAll("\\*\\*", "");
        String body = clean;

        Matcher range = Pattern.compile("(?s)\\[GUIDE_START\\](.*?)\\[GUIDE_END\\]").matcher(clean);
        if (range.find()) body = range.group(1);

        List<AdGuideAiResponse.Section> sections = new ArrayList<>();

        Pattern p = Pattern.compile("(?s)섹션\\s*:\\s*(.*?)\\n\\s*내용\\s*:\\s*(.*?)(?=\\n\\s*섹션\\s*:|$)");
        Matcher m = p.matcher(body);

        while (m.find()) {
            String section = m.group(1).trim();
            String content = m.group(2).trim();
            if (!section.isBlank() && !content.isBlank()) {
                sections.add(new AdGuideAiResponse.Section(section, content));
            }
        }

        if (sections.isEmpty()) {
            sections.add(new AdGuideAiResponse.Section("가이드라인", clean.trim()));
        }

        return new AdGuideAiResponse(sections);
    }

    // =========================================================
    // 기존: 1단계 키워드 기반 가이드라인 5개 생성
    // =========================================================

    public GuidelineResponse generateGuidelines(GuidelineRequest request) {
        checkAiEnabled();

        String prompt = """
                당신은 마케팅 전략가입니다.
                현재 최신 트렌드를 이끄는 키워드인 '%s'와(과) 우리 상품 '%s'을(를) 창의적으로 연결하여 5가지 다른 컨셉 가이드라인을 제안하세요.

                각 가이드라인은 다음 형식을 정확히 지켜주세요:
                [가이드라인 시작]
                ID: (1~5 숫자만)
                컨셉: (트렌드 키워드를 반영한 짧고 강렬한 컨셉명)
                설명: (이 트렌드를 상품에 어떻게 접목했는지 전략 설명 1~2문장)
                톤: (친근한, 전문적인, 유머러스한 등)
                [가이드라인 끝]

                총 5개를 작성하세요.
                """.formatted(request.keyword(), request.productName());

        String response = chatClient.prompt().user(prompt).call().content();
        log.info("가이드라인 생성 응답: {}", response);

        List<GuidelineResponse.Guideline> guidelines = parseGuidelines(response);
        return new GuidelineResponse(request.keyword(), guidelines);
    }

    // =========================================================
    // 기존: 2단계 최종 카피 생성
    // =========================================================

    public FinalCopyResponse generateFinalCopy(FinalCopyRequest request) {
        checkAiEnabled();

        String prompt = """
                당신은 전문 카피라이터이자 AI 이미지/비디오 프롬프트 엔지니어입니다.

                상품명: %s
                키워드: %s
                선택된 컨셉: %s
                컨셉 설명: %s
                전체적인 톤: %s

                위 정보를 바탕으로 다음 항목들을 생성하세요.
                각 항목은 반드시 지정된 구분자 사이에 내용을 작성하세요.

                [COPY] (15자 이내의 임팩트 있는 문구) [/COPY]
                [SHORTFORM] (영상 묘사 프롬프트, 영어로 작성) [/SHORTFORM]
                [BANNER] (이미지 묘사 프롬프트, 영어로 작성) [/BANNER]
                [SNS] (이모지가 포함된 인스타그램 스타일 문구 및 해시태그) [/SNS]
                [REASON] (이 가이드라인이 현재 트렌드와 제품을 연결하는 데 있어 왜 가장 효과적인지, 그리고 마케팅적으로 어떤 강점이 있는지에 대한 전략적 분석) [/REASON]
                """.formatted(
                request.productName(),
                request.keyword(),
                request.selectedConcept(),
                request.selectedDescription(),
                request.tone());

        String response = chatClient.prompt().user(prompt).call().content();
        log.info("최종 카피 생성 응답: {}", response);

        return parseFinalResponse(request.productName(), request.selectedConcept(), response);
    }

    private List<GuidelineResponse.Guideline> parseGuidelines(String response) {
        List<GuidelineResponse.Guideline> guidelines = new ArrayList<>();
        String[] blocks = response.split("\\[가이드라인 시작\\]");

        for (String block : blocks) {
            String cleanBlock = block.split("\\[가이드라인 끝\\]")[0];
            if (cleanBlock.trim().length() < 10) continue;

            try {
                int id = Integer.parseInt(extractValue(cleanBlock, "ID").replaceAll("[^0-9]", ""));
                String concept = extractValue(cleanBlock, "컨셉");
                String description = extractValue(cleanBlock, "설명");
                String tone = extractValue(cleanBlock, "톤");

                guidelines.add(new GuidelineResponse.Guideline(id, concept, description, tone));
            } catch (Exception e) {
                log.warn("가이드라인 블록 파싱 실패: {}", e.getMessage());
            }
        }
        return guidelines;
    }

    private FinalCopyResponse parseFinalResponse(String productName, String concept, String response) {
        String cleanResponse = response.replaceAll("\\*\\*", "");

        String copy = extractByTag(cleanResponse, "COPY");
        String shortform = extractByTag(cleanResponse, "SHORTFORM");
        String banner = extractByTag(cleanResponse, "BANNER");
        String sns = extractByTag(cleanResponse, "SNS");
        String reason = extractByTag(cleanResponse, "REASON");

        if (copy.isEmpty()) copy = extractSectionFallback(cleanResponse, "광고 카피");
        if (shortform.isEmpty()) shortform = extractSectionFallback(cleanResponse, "숏폼 프롬프트");
        if (banner.isEmpty()) banner = extractSectionFallback(cleanResponse, "배너 프롬프트");
        if (sns.isEmpty()) sns = extractSectionFallback(cleanResponse, "SNS 캡션");
        if (reason.isEmpty()) reason = extractSectionFallback(cleanResponse, "선정 이유");

        return FinalCopyResponse.of(productName, concept, copy, shortform, banner, sns, reason);
    }

    private String extractByTag(String text, String tag) {
        Pattern pattern = Pattern.compile("(?s)\\[" + tag + "\\](.*?)\\[/" + tag + "\\]");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractSectionFallback(String text, String sectionName) {
        Pattern pattern = Pattern.compile("(?i)" + sectionName + ":?\\s*(.*?)(?=\\n\\d\\.|\\n\\[|\\n\\n|$)",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim().replaceAll("^[:\\s]+", "") : "";
    }

    private String extractValue(String block, String key) {
        Pattern pattern = Pattern.compile("(?i)" + key + ":?\\s*([^\n]+)");
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1).trim().replaceAll("\\*\\*", "") : "";
    }
}
