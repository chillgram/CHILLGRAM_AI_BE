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
        Integer adMessageFocus,        // 0: 트렌드 중심 ~ 4: 제품 특징(리뷰) 중심
        Integer adMessageTarget,       // 0: 인지, 1: 공감, 2: 보상, 3: 참여, 4: 행동
        String reviewText,             // 제품 리뷰 요약 (3줄)
        List<String> trendKeywords,
        List<String> hashtags,
        String styleSummary,
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
        reviewText = safe(reviewText);
        styleSummary = safe(styleSummary);

        adMessageFocus = adMessageFocus == null ? 2 : Math.clamp(adMessageFocus, 0, 4);
        adMessageTarget = adMessageTarget == null ? 0 : Math.clamp(adMessageTarget, 0, 4);

        selectedKeywords = safeList(selectedKeywords);
        trendKeywords = safeList(trendKeywords);
        hashtags = safeList(hashtags);

        baseDate = Objects.requireNonNullElseGet(baseDate, LocalDate::now);

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
                req != null ? req.adGoal() : null,
                req != null ? req.requestText() : null,
                req != null ? req.selectedKeywords() : null,
                req != null ? req.adFocus() : null,
                req != null ? req.adMessageFocus() : null,
                req != null ? req.adMessageTarget() : null,
                req != null ? req.reviewText() : null,
                trendKeywordNames,
                trends != null ? trends.hashtags() : null,
                trends != null ? trends.styleSummary() : null,
                null, null, null
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
                null, null, null, null,
                project.getAdMessageFocus(),
                project.getAdMessageTarget(),
                null,
                null, null, null,
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
