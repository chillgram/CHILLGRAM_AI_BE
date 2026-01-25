package com.example.chillgram.domain.ai.dto;

/**
 * 2단계: 최종 카피 생성 응답
 */
public record FinalCopyResponse(
        String productName,
        String selectedConcept,
        String finalCopy,
        String shortformPrompt,
        String bannerPrompt,
        String snsPrompt,
        String selectionReason) {
    public static FinalCopyResponse of(
            String productName,
            String concept,
            String copy,
            String shortform,
            String banner,
            String sns,
            String reason) {
        return new FinalCopyResponse(
                productName, concept, copy, shortform, banner, sns, reason);
    }
}
