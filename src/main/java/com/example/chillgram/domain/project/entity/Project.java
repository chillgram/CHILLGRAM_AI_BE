package com.example.chillgram.domain.project.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("project")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @Column("project_id")
    private Long id;

    @Column("company_id")
    private Long companyId;

    @Column("product_id")
    private Long productId;

    @Column("project_type")
    private ProjectType projectType;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("ad_message_focus")
    private Integer adMessageFocus; // 0: 트렌드 ~ 4: 제품 특징

    @Column("ad_message_target")
    private Integer adMessageTarget; // 0: 인지 ~ 4: 행동

    @Column("status")
    private String status; // 또는 Enum

    @CreatedBy
    @Column("created_by")
    private Long createdBy;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("userimg_gcs_url")
    private String userImgGcsUrl;

    @Column("dieline_gcs_url")
    private String dielineGcsUrl;

    @Column("mockup_result_url")
    private String mockupResultUrl;

    public void applyDieline(String url) {
        this.dielineGcsUrl = url;
        this.updatedAt = LocalDateTime.now();
    }

    public void applyMockupResult(String url) {
        this.mockupResultUrl = url;
        this.updatedAt = LocalDateTime.now();
    }

    public enum ProjectType {
        AD, // 광고
        DESIGN // 도안
    }

    public enum ProjectStatus {
        ACTIVE,
        ARCHIVED
    }
}
