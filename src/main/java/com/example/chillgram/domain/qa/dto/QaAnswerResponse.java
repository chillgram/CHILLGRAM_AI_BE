package com.example.chillgram.domain.qa.dto;

import com.example.chillgram.domain.qa.entity.QaAnswer;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QaAnswerResponse {
    private Long answerId;
    private Long questionId;
    private Long companyId;
    private Long answeredBy;
    private String body;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static QaAnswerResponse from(QaAnswer answer) {
        return QaAnswerResponse.builder()
                .answerId(answer.getAnswerId())
                .questionId(answer.getQuestionId())
                .companyId(answer.getCompanyId())
                .answeredBy(answer.getAnsweredBy())
                .body(answer.getBody())
                .createdAt(answer.getCreatedAt())
                .updatedAt(answer.getUpdatedAt())
                .build();
    }
}
