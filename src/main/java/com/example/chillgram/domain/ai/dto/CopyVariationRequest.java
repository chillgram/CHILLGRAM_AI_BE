package com.example.chillgram.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CopyVariationRequest(
    @Schema(description = "사용자가 선택한 비주얼 가이드라인 옵션")
    VisualGuideOption selectedOption
) {}
