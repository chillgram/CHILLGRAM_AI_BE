package com.example.chillgram.domain.content.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("content")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Content {

    @Id
    @Column("content_id")
    private Long id;

    @Column("company_id")
    private Long companyId;

    @Column("product_id")
    private Long productId;

    @Column("project_id")
    private Long projectId;

    @Column("content_type")
    private String contentType;

    @Column("platform")
    private String platform;

    @Column("title")
    private String title;

    @Column("body")
    private String body;

    @Column("status")
    private String status;

    @Column("tags")
    private String tags;

    @Column("view_count")
    private Long viewCount;

    @Column("like_count")
    private Long likeCount;

    @Column("share_count")
    private Long shareCount;

    @Column("social_post_id")
    private Long socialPostId;

    @Column("published_at")
    private LocalDateTime publishedAt;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("banner_ratio")
    private Integer bannerRatio;

    @Column("gcs_img_url")
    private String gcsImgUrl;

    @Column("mockup_img_url")
    private String mockupImgUrl;

    public void update(String title, String body, String status, String tags, String platform) {
        if (title != null)
            this.title = title;
        if (body != null)
            this.body = body;
        if (status != null)
            this.status = status;
        if (tags != null)
            this.tags = tags;
        if (platform != null)
            this.platform = platform;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateMockup(String generatedImgUrl) {
        this.gcsImgUrl = generatedImgUrl; // [Fix] 생성된 목업을 gcs_img_url에 저장 (사용자 요구사항)
        this.status = "COMPLETED";
        this.updatedAt = LocalDateTime.now();
    }

    public void updateMockupFailed() {
        this.status = "FAILED";
        this.updatedAt = LocalDateTime.now();
    }
}
