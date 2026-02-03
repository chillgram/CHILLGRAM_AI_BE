package com.example.chillgram.domain.qa.dto;

import com.example.chillgram.domain.qa.entity.QaQuestion;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QaListResponse {
    private Long questionId;
    private Long categoryId; // UI에서 매핑하거나 추후 조인
    private String title;
    private String body; // 본문 미리보기용
    private String status;
    private Integer viewCount;
    private Long createdBy; // 작성자 ID
    private String createdByName; // 작성자 이름 (추가)
    private LocalDateTime createdAt;
    // private Integer answerCount; // 답변 수는 별도 쿼리 필요

    public static QaListResponse from(QaQuestion question) {
        return QaListResponse.builder()
                .questionId(question.getQuestionId())
                .categoryId(question.getCategoryId())
                .title(question.getTitle())
                .body(question.getBody())
                .status(question.getStatus())
                .createdAt(question.getCreatedAt())
                .build();
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }
}
