package com.example.chillgram.domain.qa.dto;

import com.example.chillgram.domain.qa.entity.QaQuestion;

import java.time.LocalDateTime;

public record QaWriteResponse(
        Long questionId,
        String title,
        String status,
        LocalDateTime createdAt) {
    public static QaWriteResponse from(QaQuestion question) {
        return new QaWriteResponse(
                question.getQuestionId(),
                question.getTitle(),
                question.getStatus(),
                question.getCreatedAt());
    }
}
