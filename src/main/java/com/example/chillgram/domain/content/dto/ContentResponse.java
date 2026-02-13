package com.example.chillgram.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "콘텐츠 응답 DTO")
public record ContentResponse(
        @Schema(description = "콘텐츠 ID") Long contentId,

        @Schema(description = "소속 회사 ID") Long companyId,

        @Schema(description = "제품 ID") Long productId,

        @Schema(description = "프로젝트 ID") Long projectId,

        @Schema(description = "콘텐츠 타입 (IMAGE, VIDEO 등)") String contentType,

        @Schema(description = "플랫폼") String platform,

        @Schema(description = "제목") String title,

        @Schema(description = "본문") String body,

        @Schema(description = "상태") String status,

        @Schema(description = "태그") String tags,

        @Schema(description = "조회수") Long viewCount,

        @Schema(description = "좋아요 수") Long likeCount,

        @Schema(description = "공유 수") Long shareCount,

        @Schema(description = "배너 비율 코드") Integer bannerRatio,

        @Schema(description = "생성일시") LocalDateTime createdAt,

        @Schema(description = "수정일시") LocalDateTime updatedAt) {
}
