package com.example.chillgram.domain.qa.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("qa_question")
public class QaQuestion {

    @Id
    @Column("question_id")
    private Long questionId;

    @Column("company_id")
    private Long companyId;

    @Column("category_id")
    private Long categoryId;

    @Column("created_by")
    private Long createdBy;

    private String title;

    private String body;

    private String status;

    @Column("view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("answered_at")
    private LocalDateTime answeredAt;

    @Column("gcs_image_url")
    private String gcsImageUrl;
}
