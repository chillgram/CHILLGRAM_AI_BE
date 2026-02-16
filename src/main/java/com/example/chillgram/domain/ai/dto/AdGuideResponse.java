package com.example.chillgram.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdGuideResponse(
    @Schema(description = "프로젝트 ID", example = "101")
    Long projectId,
    @Schema(description = "생성된 비주얼 가이드라인 옵션 (5개)")
    List<VisualGuideOption> options
) {}
