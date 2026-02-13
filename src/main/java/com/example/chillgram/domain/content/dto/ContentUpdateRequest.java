package com.example.chillgram.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "콘텐츠 수정 요청 DTO")
public record ContentUpdateRequest(
        @Schema(description = "콘텐츠 제목", example = "여름 시즌 배너") String title,

        @Schema(description = "콘텐츠 본문", example = "밝고 경쾌한 분위기의 배너") String body,

        @Schema(description = "상태 (DRAFT, ACTIVE, ARCHIVED)", example = "ACTIVE") String status,

        @Schema(description = "태그 (쉼표 구분)", example = "AI,마케팅,여름") String tags,

        @Schema(description = "플랫폼 (INSTAGRAM, FACEBOOK 등)", example = "INSTAGRAM") String platform) {
}
