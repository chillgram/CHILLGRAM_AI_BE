package com.example.chillgram.domain.ai.dto;

import java.util.List;

/**
 * 1단계: 가이드라인 생성 응답
 */
public record GuidelineResponse(
        String keyword,
        List<Guideline> guidelines) {
    public record Guideline(
            int id,
            String concept,
            String description,
            String tone) {
    }
}
