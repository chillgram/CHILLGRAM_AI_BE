package com.example.chillgram.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdGuideRequest(
    @Schema(description = "트렌드 정보", example = "MZ세대 매운맛 챌린지 유행")
    String trend,

    @Schema(description = "고객 리뷰 (최대 3개)")
    List<String> reviews
) {
    public AdGuideRequest {
        trend = (trend == null) ? "" : trend.trim();
        reviews = (reviews == null) ? List.of() : reviews;
    }
}
