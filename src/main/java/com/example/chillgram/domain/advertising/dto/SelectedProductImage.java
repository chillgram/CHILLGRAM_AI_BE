package com.example.chillgram.domain.advertising.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "BASIC에서 선택한 제품 이미지 정보")
public record SelectedProductImage(
        @Schema(description = "후보 ID", example = "c1") @NotBlank String candidateId,
        @Schema(description = "선택된 이미지 URL(https)", example = "https://storage.googleapis.com/bucket/tmp/basic/xxx.png")
        @NotBlank String url,
        @Schema(description = "후보 메타 정보") JsonNode meta,
        @Schema(description = "프리뷰 jobId", example = "4c272fee-43bb-4828-b730-ae9f83e97970") String previewJobId
) {}