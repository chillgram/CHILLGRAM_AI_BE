package com.example.chillgram.domain.qa.dto;

import com.example.chillgram.domain.qa.entity.QaAnswer;
import com.example.chillgram.domain.qa.entity.QaQuestion;
import com.example.chillgram.domain.qa.entity.QaQuestionAttachment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class QaDetailResponse {
    private Long questionId;
    private Long categoryId;
    private Long companyId;
    private String title;
    private String body;
    private String status;
    private Integer viewCount;
    private Long createdBy;
    private String createdByName; // 질문 작성자 이름 추가
    private LocalDateTime createdAt;

    // 첨부파일 정보 리스트
    private List<AttachmentDto> attachments;

    // 답변 목록 (추가됨)
    private List<AnswerDto> answers;
    private Integer answerCount;

    @Getter
    @Builder
    public static class AttachmentDto {
        private Long attachmentId;
        private String fileUrl;
        private String mimeType;
        private Long fileSize;

        public static AttachmentDto from(QaQuestionAttachment attachment) {
            return AttachmentDto.builder()
                    .attachmentId(attachment.getAttachmentId())
                    .fileUrl(attachment.getFileUrl())
                    .mimeType(attachment.getMimeType())
                    .fileSize(attachment.getFileSize())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class AnswerDto {
        private Long answerId;
        private Long companyId;
        private Long answeredBy;
        private String answeredByName; // 답변 작성자 이름 추가
        private String body;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public void setAnsweredByName(String answeredByName) {
            this.answeredByName = answeredByName;
        }

        public static AnswerDto from(QaAnswer answer) {
            return AnswerDto.builder()
                    .answerId(answer.getAnswerId())
                    .companyId(answer.getCompanyId())
                    .answeredBy(answer.getAnsweredBy())
                    .body(answer.getBody())
                    .createdAt(answer.getCreatedAt())
                    .updatedAt(answer.getUpdatedAt())
                    .build();
        }
    }

    public static QaDetailResponse from(QaQuestion question,
            List<QaQuestionAttachment> attachments,
            List<QaAnswer> answers) {
        return QaDetailResponse.builder()
                .questionId(question.getQuestionId())
                .categoryId(question.getCategoryId())
                .companyId(question.getCompanyId())
                .title(question.getTitle())
                .body(question.getBody())
                .status(question.getStatus())
                .viewCount(question.getViewCount())
                .createdBy(question.getCreatedBy())
                // createdByName은 Service에서 채워야 함
                .createdAt(question.getCreatedAt())
                .attachments(attachments != null
                        ? attachments.stream().map(AttachmentDto::from).collect(Collectors.toList())
                        : Collections.emptyList())
                .answers(answers != null
                        ? answers.stream().map(AnswerDto::from).collect(Collectors.toList())
                        : Collections.emptyList())
                .answerCount(answers != null ? answers.size() : 0)
                .build();
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public void setAnswers(List<AnswerDto> answers) {
        this.answers = answers;
    }
}
