package com.example.chillgram.domain.qa.dto;

import com.example.chillgram.domain.qa.entity.QaQuestion;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "QnA 작성/수정 응답")

public record QaWriteResponse(
        @Schema(description = "질문 ID") Long questionId,
        @Schema(description = "제목") String title,
        @Schema(description = "상태") String status,
        @Schema(description = "작성일시") LocalDateTime createdAt,
        @Schema(description = "이미지 URL (GCS Public URL)") String gcsImageUrl) {
    public static QaWriteResponse from(QaQuestion question) {
        return new QaWriteResponse(
                question.getQuestionId(),
                question.getTitle(),
                question.getStatus(),
                question.getCreatedAt(),
                question.getGcsImageUrl());
    }
}
