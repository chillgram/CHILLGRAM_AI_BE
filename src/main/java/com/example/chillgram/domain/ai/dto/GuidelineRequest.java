package com.example.chillgram.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 1단계: 가이드라인 생성 요청
 */
public record GuidelineRequest(
        @NotBlank(message = "상품명은 필수입니다") String productName,
        @NotBlank(message = "키워드는 필수입니다") String keyword) {
}
