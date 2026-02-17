package com.example.chillgram.domain.advertising.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdGuidesResponse(
        String recommendedGuideId,
        List<GuidelineOption> guides) {
    public record GuidelineOption(
            String id,
            String title,
            String summary,
            String badge,
            int score,
            String rationale,
            Map<String, Object> key_points) {
    }
}
