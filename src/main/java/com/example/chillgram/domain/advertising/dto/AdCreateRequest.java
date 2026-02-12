package com.example.chillgram.domain.advertising.dto;

import com.example.chillgram.domain.ai.dto.FinalCopyResponse;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "광고 생성 요청 DTO")
public record AdCreateRequest(
        @Schema(description = "제품명", example = "칠그램 AI") @NotBlank(message = "제품명은 필수입니다.") String productName,

        @Schema(description = "프로젝트 타입", example = "AD", defaultValue = "AD") String projectType, // AD, GUIDE etc.

        @Schema(description = "프로젝트 제목", example = "여름 시즌 광고") @NotBlank(message = "프로젝트 제목은 필수입니다.") String projectTitle,

        @Schema(description = "광고 목표", example = "브랜드 인지도 상승") @NotBlank(message = "광고 목표는 필수입니다.") String adGoal,

        @Schema(description = "요청 사항", example = "밝고 경쾌한 분위기로 만들어주세요.") @NotBlank(message = "요청 사항은 필수입니다.") String requestText,

        @Schema(description = "선택된 키워드 목록", example = "[\"AI\", \"마케팅\"]") List<String> selectedKeywords,

        @Schema(description = "광고 소구점 (0: 트렌드, 1: 제품강조)", example = "1") @NotNull(message = "광고 소구점은 필수입니다.") Integer adFocus,

        @Schema(description = "타겟 메시지 (0: 인지, 1: 공감)", example = "0") @NotNull(message = "타겟 메시지는 필수입니다.") Integer adMessageTarget,

        @Schema(description = "기준 날짜 (YYYY-MM-DD)", example = "2024-02-12") String baseDate,

        @Schema(description = "배너 사이즈 (1:1, 16:9, 9:16)", example = "1:1") String bannerSize,

        @Schema(description = "플랫폼 (INSTAGRAM, YOUTUBE etc.)", example = "INSTAGRAM") String platform,

        @Schema(description = "선택된 가이드 ID", example = "guide_123") String selectedGuideId,

        @Schema(description = "선택된 가이드 객체") JsonNode selectedGuide,

        @Schema(description = "선택된 카피 ID", example = "copy_456") String selectedCopyId,

        @Schema(description = "선택된 카피 객체") FinalCopyResponse selectedCopy,

        @Schema(description = "생성할 콘텐츠 타입 목록 (BANNER, SNS, VIDEO, SHORT)", example = "[\"BANNER\", \"SNS\"]") List<String> selectedTypes) {
    public AdCreateRequest {
        selectedKeywords = selectedKeywords == null ? List.of() : List.copyOf(selectedKeywords);
        selectedTypes = selectedTypes == null ? List.of() : List.copyOf(selectedTypes);
    }
}
