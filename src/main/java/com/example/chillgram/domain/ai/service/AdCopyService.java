package com.example.chillgram.domain.ai.service;

import com.example.chillgram.domain.ai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

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
    private final ChatClient.Builder chatClientBuilder;

    public AdCopyService(
            org.springframework.beans.factory.ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.chatClientBuilder = chatClientBuilderProvider.getIfAvailable();
    }

    /**
     * 1단계: 키워드 기반 가이드라인 5개 생성
     */
    public GuidelineResponse generateGuidelines(GuidelineRequest request) {
        if (chatClientBuilder == null) {
            throw new RuntimeException("AI 기능이 비활성화되어 있습니다. (API Key 누락)");
        }
        ChatClient chatClient = chatClientBuilder.build();

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

    /**
     * 2단계: 가이드라인 기반 최종 카피 및 프롬프트 생성
     */
    public FinalCopyResponse generateFinalCopy(FinalCopyRequest request) {
        if (chatClientBuilder == null) {
            throw new RuntimeException("AI 기능이 비활성화되어 있습니다. (API Key 누락)");
        }
        ChatClient chatClient = chatClientBuilder.build();

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
        // 모든 마크다운 굵게 표시(**) 제거하여 파싱 방해 요소 제거
        String cleanResponse = response.replaceAll("\\*\\*", "");

        String copy = extractByTag(cleanResponse, "COPY");
        String shortform = extractByTag(cleanResponse, "SHORTFORM");
        String banner = extractByTag(cleanResponse, "BANNER");
        String sns = extractByTag(cleanResponse, "SNS");
        String reason = extractByTag(cleanResponse, "REASON");

        // 만약 태그 파싱에 실패했다면 (LLM이 형식을 안 지킨 경우) 폴백 로직
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
}
