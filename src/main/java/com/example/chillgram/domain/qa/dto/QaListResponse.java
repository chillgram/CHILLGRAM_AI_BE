package com.example.chillgram.domain.qa.dto;

import com.example.chillgram.domain.qa.entity.QaQuestion;
import lombok.Builder;
import lombok.Getter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "QnA 목록 응답")

@Getter
@Builder
public class QaListResponse {
    @Schema(description = "질문 ID")
    private Long questionId;
    @Schema(description = "카테고리 ID")
    private Long categoryId; // UI에서 매핑하거나 추후 조인
    @Schema(description = "제목")
    private String title;
    @Schema(description = "본문 요약")
    private String body; // 본문 미리보기용
    @Schema(description = "상태 (WAITING, ANSWERED)")
    private String status;
    @Schema(description = "조회수")
    private Integer viewCount;
    @Schema(description = "작성자 ID")
    private Long createdBy; // 작성자 ID
    @Schema(description = "작성자 이름")
    private String createdByName; // 작성자 이름 (추가)
    @Schema(description = "작성일시")
    private LocalDateTime createdAt;
    @Schema(description = "썸네일 이미지 URL (GCS Public URL)")
    private String gcsImageUrl; // 썸네일용 이미지 URL 추가
    // private Integer answerCount; // 답변 수는 별도 쿼리 필요

    public static QaListResponse from(QaQuestion question) {
        return QaListResponse.builder()
                .questionId(question.getQuestionId())
                .categoryId(question.getCategoryId())
                .title(question.getTitle())
                .body(question.getBody())
                .status(question.getStatus())
                .viewCount(question.getViewCount())
                .createdBy(question.getCreatedBy())
                .createdAt(question.getCreatedAt())
                .gcsImageUrl(question.getGcsImageUrl())
                .build();
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }
}
