package com.example.chillgram.domain.advertising.engine;


import com.example.chillgram.domain.advertising.dto.AdTrendsResponse;
import com.example.chillgram.domain.advertising.repository.EventCalendarRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 광고 트렌드 규칙 엔진
 * - 서비스는 흐름(조회/조립)만 담당하고, 키워드/해시태그/스타일 생성은 엔진으로 위임
 */
public interface TrendRuleEngine {

    TrendResult analyze(long productId, LocalDate baseDate, List<EventCalendarRepository.EventRow> events);

    record TrendResult(
            List<TrendKeyword> trendKeywords,
            List<String> hashtags,
            String styleSummary
    ) {}

    record TrendKeyword(String name, String description) {}
}
