package com.example.chillgram.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "패키지 목업 생성 작업 응답")
public record PackageMockupResponse(
                @Schema(description = "생성된 작업 ID") UUID jobId,
                @Schema(description = "연결된 프로젝트 ID") Long projectId,
                @Schema(description = "1차 미리보기 URL (GCS)") String previewUrl) {
}
