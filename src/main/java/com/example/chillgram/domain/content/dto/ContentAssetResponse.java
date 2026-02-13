package com.example.chillgram.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "콘텐츠 에셋 응답 DTO")
public record ContentAssetResponse(
        @Schema(description = "에셋 ID") Long assetId,

        @Schema(description = "콘텐츠 ID") Long contentId,

        @Schema(description = "에셋 타입 (PRIMARY, THUMBNAIL)") String assetType,

        @Schema(description = "파일 URL") String fileUrl,

        @Schema(description = "썸네일 URL") String thumbUrl,

        @Schema(description = "MIME 타입") String mimeType,

        @Schema(description = "파일 크기 (bytes)") Long fileSize,

        @Schema(description = "가로(px)") Integer width,

        @Schema(description = "세로(px)") Integer height,

        @Schema(description = "영상 길이(ms)") Integer durationMs,

        @Schema(description = "정렬 순서") Integer sortOrder,

        @Schema(description = "생성일시") LocalDateTime createdAt) {
}
