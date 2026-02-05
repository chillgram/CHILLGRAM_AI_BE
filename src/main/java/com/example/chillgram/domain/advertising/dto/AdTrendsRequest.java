package com.example.chillgram.domain.advertising.dto;

import java.time.LocalDate;

/**
 * 광고 트렌드 분석 요청 DTO
 */
public record AdTrendsRequest(
        Long productId,
        LocalDate baseDate,
        Integer limit
) {}