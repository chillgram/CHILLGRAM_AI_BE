package com.example.chillgram.domain.project.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("project_image_attachment")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImageAttachment {

    @Id
    @Column("attachment_id")
    private Long id;

    @Column("project_id")
    private Long projectId;

    @Column("file_url")
    private String fileUrl;

    @Column("file_name")
    private String fileName;

    @Column("file_size")
    private Long fileSize;

    @Column("mime_type")
    private String mimeType;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
