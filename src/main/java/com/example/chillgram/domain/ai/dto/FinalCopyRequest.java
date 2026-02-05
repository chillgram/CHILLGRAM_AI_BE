package com.example.chillgram.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 2단계: 최종 카피 생성 요청
 */
public record FinalCopyRequest(
        @NotBlank(message = "상품명은 필수입니다") String productName,
        @NotBlank(message = "키워드는 필수입니다") String keyword,
        String selectedConcept,
        String selectedDescription,
        String tone) {
    public FinalCopyRequest {
        if (tone == null)
            tone = "친근한";
    }
}
