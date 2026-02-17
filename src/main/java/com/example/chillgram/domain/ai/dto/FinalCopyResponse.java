package com.example.chillgram.domain.ai.dto;

import java.util.List;

/**
 * 2단계: 최종 카피 생성 응답 (2-Step Flow)
 */
public record FinalCopyResponse(
        String recommendedCopyId,
        List<CopyOption> copies) {
    public record CopyOption(
            String id,
            String title,
            String body) {
    }
}
