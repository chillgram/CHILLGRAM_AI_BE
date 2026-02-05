package com.example.chillgram.domain.advertising.dto;

import java.time.LocalDate;
import java.util.List;

public record AdGuidesResponse(
        long productId,
        String productName,
        LocalDate baseDate,
        String title,
        String adGoal,
        String requestText,
        List<String> selectedKeywords,
        String adFocus,
        List<GuideSection> sections
) {
    public record GuideSection(String section, String content) {}
}