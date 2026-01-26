package com.example.chillgram.domain.qa.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("qa_question_attachment")
public class QaQuestionAttachment {

    @Id
    @Column("attachment_id")
    private Long attachmentId;

    @Column("question_id")
    private Long questionId;

    @Column("file_url")
    private String fileUrl;

    @Column("mime_type")
    private String mimeType;

    @Column("file_size")
    private Long fileSize;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
