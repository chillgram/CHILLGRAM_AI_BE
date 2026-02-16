package com.example.chillgram.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VisualGuideOption(
    @Schema(description = "옵션 ID", example = "1")
    int optionId,
    @Schema(description = "제품 특징", example = "매운맛 강조")
    String product,
    @Schema(description = "배경 장소", example = "화려한 야경")
    String place,
    @Schema(description = "역동적 효과", example = "불꽃이 튀는 효과")
    String effect,
    @Schema(description = "텍스트 재질", example = "금속 질감")
    String texture,
    @Schema(description = "전체 스타일", example = "사이버펑크")
    String style
) {}
