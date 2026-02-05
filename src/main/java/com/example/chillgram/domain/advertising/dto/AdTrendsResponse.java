package com.example.chillgram.domain.advertising.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 광고 트렌드 분석(이벤트 기반) UI 응답 DTO
 */
public record AdTrendsResponse(
        long productId,
        LocalDate baseDate,
        List<TrendKeyword> trendKeywords,
        List<String> hashtags,
        String styleSummary
) {
    public record TrendKeyword(String name, String description) {}
}