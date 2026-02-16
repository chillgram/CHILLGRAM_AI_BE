package com.example.chillgram.domain.ai.dto;

import com.example.chillgram.domain.advertising.dto.AdGuidesRequest;
import com.example.chillgram.domain.advertising.dto.AdTrendsResponse;
import com.example.chillgram.domain.product.entity.Product;

import com.example.chillgram.domain.project.entity.Project;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record AdGuideAiRequest(
        long productId,
        String productName,
        LocalDate baseDate,
        String projectTitle,
        String adGoal,
        String requestText,
        List<String> selectedKeywords,
        String adFocus,
        List<String> trendKeywords,
        List<String> hashtags,
        String styleSummary,
        // [New Fields] - Moved to end for compatibility
        Integer adMessageFocus,
        Integer adMessageTarget,
        String description,
        String trendString,
        List<String> reviews
) {
    public AdGuideAiRequest {
        productName = safe(productName);
        projectTitle = safe(projectTitle);
        adGoal = safe(adGoal);
        requestText = safe(requestText);
        adFocus = safe(adFocus);
        styleSummary = safe(styleSummary);

        selectedKeywords = safeList(selectedKeywords);
        trendKeywords = safeList(trendKeywords);
        hashtags = safeList(hashtags);

        baseDate = Objects.requireNonNullElseGet(baseDate, LocalDate::now);

        // New fields safe handling
        description = safe(description);
        trendString = safe(trendString);
        reviews = (reviews == null) ? List.of() : reviews;
    }

    public static AdGuideAiRequest from(
            long productId,
            Product product,
            LocalDate date,
            AdGuidesRequest req,
            AdTrendsResponse trends
    ) {
        List<String> trendKeywordNames = (trends == null || trends.trendKeywords() == null)
                ? List.of()
                : trends.trendKeywords().stream()
                .map(AdTrendsResponse.TrendKeyword::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        return new AdGuideAiRequest(
                productId,
                product != null ? product.getName() : null,
                date,
                req != null ? req.title() : null,
                // Existing fields first
                req != null ? req.adGoal() : null,
                req != null ? req.requestText() : null,
                req != null ? req.selectedKeywords() : null,
                req != null ? req.adFocus() : null,
                trendKeywordNames,
                trends != null ? trends.hashtags() : null,
                trends != null ? trends.styleSummary() : null,
                // New fields at end (null for legacy flow)
                null, null, null, null, null
        );
    }

    public static AdGuideAiRequest of(
            Project project,
            Product product,
            AdGuideRequest req
    ) {
        return new AdGuideAiRequest(
                product.getId(),
                product.getName(),
                LocalDate.now(),
                project.getTitle(),
                // Existing fields (null for new flow)
                null, null, null, null, null, null, null,
                // New fields
                project.getAdMessageFocus(),
                project.getAdMessageTarget(),
                project.getDescription(),
                req.trend(),
                req.reviews()
        );
    }

    private static List<String> safeList(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
