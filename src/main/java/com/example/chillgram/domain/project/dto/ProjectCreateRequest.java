package com.example.chillgram.domain.project.dto;

import com.example.chillgram.domain.project.entity.Project;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank(message = "프로젝트 이름은 필수입니다") String title,

        String description,

        @Min(0) @Max(4) Integer adMessageFocus,

        @Min(0) @Max(4) Integer adMessageTarget,

        String projectType, // "AD" or "DESIGN"

        String userImgGcsUrl) {
    public Project toEntity(Long productId, Long companyId, Long userId) {
        return Project.builder()
                .productId(productId)
                .companyId(companyId)
                .title(title)
                .description(description)
                .adMessageFocus(adMessageFocus != null ? adMessageFocus : 0)
                .adMessageTarget(adMessageTarget)
                .projectType(parseProjectType())
                .status("ACTIVE")
                .createdBy(userId)
                .userImgGcsUrl(userImgGcsUrl)
                .build();
    }

    private Project.ProjectType parseProjectType() {
        if (projectType == null || projectType.isBlank()) {
            return Project.ProjectType.AD; // 기본값
        }
        try {
            return Project.ProjectType.valueOf(projectType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Project.ProjectType.AD;
        }
    }
}
