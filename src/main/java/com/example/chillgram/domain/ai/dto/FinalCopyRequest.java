package com.example.chillgram.domain.ai.dto;

import java.util.Map;

/**
 * 2단계: 최종 카피 생성 요청
 */
public record FinalCopyRequest(
        String selectedGuideId,
        Map<String, Object> selectedGuideline) {
}
