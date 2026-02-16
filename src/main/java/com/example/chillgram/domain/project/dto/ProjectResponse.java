package com.example.chillgram.domain.project.dto;

import com.example.chillgram.domain.project.entity.Project;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long projectId,
        String title,
        String type,
        String status,
        Integer adMessageFocus,
        Integer adMessageTarget,
        Long contentCount,
        LocalDateTime createdAt,
        String userImgGcsUrl) {
    public static ProjectResponse of(Project project, Long contentCount) {
        return new ProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getProjectType() != null ? project.getProjectType().name() : null,
                project.getStatus(),
                project.getAdMessageFocus(),
                project.getAdMessageTarget(),
                contentCount,
                project.getCreatedAt(),
                project.getUserImgGcsUrl());
    }
}
