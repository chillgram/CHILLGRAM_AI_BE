package com.example.chillgram.domain.ai.service;

import com.example.chillgram.domain.ai.dto.AdGuideAiRequest;
import com.example.chillgram.domain.ai.dto.AdGuideAiResponse;
import com.example.chillgram.domain.ai.dto.FinalCopyRequest;
import com.example.chillgram.domain.ai.dto.FinalCopyResponse;
import com.example.chillgram.domain.ai.dto.GuidelineRequest;
import com.example.chillgram.domain.ai.dto.GuidelineResponse;
import com.example.chillgram.domain.ai.dto.VisualGuideOption;
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
                모든 내용은 반드시 한국어로 작성해야 합니다.
                반드시 지정된 포맷을 지키고, 설명이나 생각 과정 등 부가적인 텍스트는 출력하지 마세요.

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
                request.styleSummary());

        String response = chatClient.prompt()
                .system("당신은 한국어만 사용하는 AI입니다. 모든 응답을 반드시 한국어로 작성하세요. 영어로 응답하지 마세요.")
                .user(prompt).call().content();
        log.info("광고 가이드라인 생성 응답: {}", response);

        return parseGuideSections(response);
    }

    private AdGuideAiResponse parseGuideSections(String response) {
        String clean = (response == null ? "" : response).replaceAll("\\*\\*", "");
        String body = clean;

        Matcher range = Pattern.compile("(?s)\\[GUIDE_START\\](.*?)\\[GUIDE_END\\]").matcher(clean);
        if (range.find())
            body = range.group(1);

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

                총 5개를 작성하세요. 모든 내용은 반드시 한국어로 작성하세요.
                """.formatted(request.keyword(), request.productName());

        String response = chatClient.prompt()
                .system("당신은 한국어만 사용하는 AI입니다. 모든 응답을 반드시 한국어로 작성하세요. 영어로 응답하지 마세요.")
                .user(prompt).call().content();
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

        String response = chatClient.prompt()
                .system("당신은 한국어만 사용하는 AI입니다. SHORTFORM과 BANNER 프롬프트를 제외한 모든 응답을 한국어로 작성하세요.")
                .user(prompt).call().content();
        log.info("최종 카피 생성 응답: {}", response);

        return parseFinalResponse(request.productName(), request.selectedConcept(), response);
    }

    private List<GuidelineResponse.Guideline> parseGuidelines(String response) {
        List<GuidelineResponse.Guideline> guidelines = new ArrayList<>();
        String[] blocks = response.split("\\[가이드라인 시작\\]");

        for (String block : blocks) {
            String cleanBlock = block.split("\\[가이드라인 끝\\]")[0];
            if (cleanBlock.trim().length() < 10)
                continue;

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

        if (copy.isEmpty())
            copy = extractSectionFallback(cleanResponse, "광고 카피");
        if (shortform.isEmpty())
            shortform = extractSectionFallback(cleanResponse, "숏폼 프롬프트");
        if (banner.isEmpty())
            banner = extractSectionFallback(cleanResponse, "배너 프롬프트");
        if (sns.isEmpty())
            sns = extractSectionFallback(cleanResponse, "SNS 캡션");
        if (reason.isEmpty())
            reason = extractSectionFallback(cleanResponse, "선정 이유");

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

    // =========================================================
    // 신규: 1단계 비주얼 가이드라인 생성 (5개 옵션)
    // =========================================================

    public Mono<List<VisualGuideOption>> generateVisualGuidesMono(AdGuideAiRequest req) {
        return Mono.fromCallable(() -> {
            checkAiEnabled();
            String prompt = buildWeightedPrompt(req)
                    + "\n\n[출력 포맷]\n반드시 아래 형식을 지키고, 모든 값은 한국어로 작성하세요. 생각 과정은 생략하세요.\n\n[OPTION 1]\n제품: ...\n장소: ...\n역동적 효과: ...\n글자 재질: ...\n스타일: ...\n(위 포맷으로 5개 옵션 작성)";

            log.info("Visual Guides Prompt: {}", prompt);
            String response = chatClient.prompt()
                    .system("당신은 한국어만 사용하는 AI입니다. 모든 응답을 반드시 한국어로 작성하세요. 영어로 응답하지 마세요.")
                    .user(prompt).call().content();
            log.info("Visual Guides Response: {}", response);

            return parseVisualGuides(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // =========================================================
    // 신규: 2단계 광고 카피 베리에이션 생성
    // =========================================================

    public Mono<List<String>> generateCopyVariationsMono(VisualGuideOption option, Integer target) {
        return Mono.fromCallable(() -> {
            checkAiEnabled();
            String prompt = buildCopyPrompt(option, target);

            log.info("Copy Variations Prompt: {}", prompt);
            String response = chatClient.prompt()
                    .system("당신은 한국어만 사용하는 AI입니다. 모든 응답을 반드시 한국어로 작성하세요. 영어로 응답하지 마세요.")
                    .user(prompt).call().content();
            log.info("Copy Variations Response: {}", response);

            return parseCopyVariations(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // =========================================================
    // Helper Methods
    // =========================================================

    private String buildWeightedPrompt(AdGuideAiRequest req) {
        String emphasisInstruction;
        int focus = req.adMessageFocus() != null ? req.adMessageFocus() : 2; // Default to balance

        if (focus <= 1) { // 0, 1: 트렌드 중심
            emphasisInstruction = """
                        [중요] **트렌드 정보**를 80% 비중으로 반영하세요.
                        제품의 실제 후기는 20%만 참고하여 자연스럽게 녹여내세요.
                        최신 유행하는 밈(Meme)이나 챌린지 스타일을 적극 차용하세요.
                    """;
        } else if (focus >= 3) { // 3, 4: 제품/리뷰 중심
            emphasisInstruction = """
                        [중요] **고객 리뷰(제품 특징)**를 80% 비중으로 반영하세요.
                        트렌드는 20%만 사용하여 톤앤매너를 맞추는 정도로만 활용하세요.
                        리뷰에서 언급된 구체적인 효능, 맛, 장점을 강력하게 어필하세요.
                    """;
        } else { // 2: 균형
            emphasisInstruction = """
                        [중요] 트렌드와 고객 리뷰를 50:50으로 균형 있게 반영하세요.
                    """;
        }

        return """
                    당신은 비주얼 디렉터입니다.
                    아래 지침에 따라 5개의 서로 다른 광고 가이드라인을 작성하세요.
                    모든 필드(제품, 장소, 효과, 재질, 스타일)는 반드시 한국어로 작성해야 합니다.

                    %s

                    [입력 데이터]
                    - 제품명: %s
                    - 요청사항: %s
                    - 트렌드: %s
                    - 리뷰:
                    %s

                    각 옵션은 '[OPTION N]'으로 시작해야 하며, 서론이나 결론 없이 지정된 데이터만 출력하세요.
                """.formatted(
                emphasisInstruction,
                req.productName(),
                req.description(),
                req.trendString(),
                req.reviews() != null ? String.join("\n", req.reviews()) : "");
    }

    private String buildCopyPrompt(VisualGuideOption option, Integer target) {
        return """
                    아래 비주얼 가이드라인에 어울리는 광고 카피 5개를 작성하세요.
                    모든 카피는 반드시 한국어로 작성되어야 합니다.
                    서론(Thinking process 등)은 절대 포함하지 말고 [COPY 1] ~ [COPY 5] 내용만 출력하세요.

                    [비주얼 가이드라인]
                    - 제품: %s
                    - 장소: %s
                    - 효과: %s
                    - 스타일: %s

                    [카피 목표]: %s
                """.formatted(
                option.product(),
                option.place(),
                option.effect(),
                option.style(),
                resolveTarget(target));
    }

    private String resolveTarget(Integer target) {
        if (target == null)
            return "구매 전환 유도";
        return switch (target) {
            case 0 -> "브랜드 인지도 확산 및 노출 극대화 (Viral)";
            case 1 -> "소비자의 공감대 형성 및 감성적 연결 (Empathy)";
            case 2 -> "혜택 및 보상 강조를 통한 이목 집중 (Benefit)";
            case 3 -> "이벤트 참여 및 댓글/공유 유도 (Engagement)";
            case 4 -> "즉각적인 구매 전환 및 행동 유도 (Conversion)";
            default -> "구매 전환 유도";
        };
    }

    private List<VisualGuideOption> parseVisualGuides(String response) {
        List<VisualGuideOption> options = new ArrayList<>();
        String clean = (response == null ? "" : response).replaceAll("\\*\\*", "");
        String[] blocks = clean.split("\\[OPTION\\s+\\d+\\]");

        int id = 1;
        for (String block : blocks) {
            if (block.isBlank())
                continue;
            String product = extractGuideField(block, "제품");
            String place = extractGuideField(block, "장소");
            String effect = extractGuideField(block, "역동적 효과");
            String texture = extractGuideField(block, "글자 재질");
            String style = extractGuideField(block, "스타일");

            if (!product.isEmpty() || !place.isEmpty()) {
                options.add(new VisualGuideOption(id++, product, place, effect, texture, style));
            }
        }

        // Fallback: 파싱 실패 시 raw 텍스트를 넣지 않고 빈 리스트 반환
        if (options.isEmpty()) {
            log.warn("Visual guide parsing failed. Raw response: {}", clean);
        }
        return options;
    }

    private String extractGuideField(String block, String key) {
        Pattern p = Pattern.compile("(?s)(?:" + key + ")\\s*:\\s*(.*?)(?=\\n(?:제품|장소|역동적 효과|글자 재질|스타일)\\s*:|$)");
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1).trim() : "";
    }

    private List<String> parseCopyVariations(String response) {
        if (response == null)
            return List.of();
        String clean = response.replaceAll("\\*\\*", "");

        List<String> copies = new ArrayList<>();
        Matcher m = Pattern.compile("\\[COPY\\s+\\d+\\]\\s*(.*?)(?=\\[COPY|$)", Pattern.DOTALL).matcher(clean);

        while (m.find()) {
            copies.add(m.group(1).trim());
        }

        if (copies.isEmpty()) {
            // Fallback: 줄바꿈으로 시도 (thinking 잔여 텍스트 필터링)
            String[] lines = clean.split("\n");
            for (String line : lines) {
                String l = line.replaceAll("^\\d+[.\\)]\\s*", "").replaceAll("^-\\s*", "").trim();
                if (!l.isBlank() && l.length() > 5 && !l.toLowerCase().startsWith("okay") && !l.toLowerCase().startsWith("my thought"))
                    copies.add(l);
            }
            if (copies.isEmpty()) {
                log.warn("Copy variation parsing failed. Raw response: {}", clean);
            }
        }

        return copies;
    }
}
