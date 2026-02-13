package com.example.chillgram.domain.content.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("content_asset")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAsset {

    @Id
    @Column("asset_id")
    private Long id;

    @Column("content_id")
    private Long contentId;

    @Column("asset_type")
    private String assetType;

    @Column("file_url")
    private String fileUrl;

    @Column("thumb_url")
    private String thumbUrl;

    @Column("mime_type")
    private String mimeType;

    @Column("file_size")
    private Long fileSize;

    @Column("width")
    private Integer width;

    @Column("height")
    private Integer height;

    @Column("duration_ms")
    private Integer durationMs;

    @Column("sort_order")
    private Integer sortOrder;

    @Column("created_at")
    private LocalDateTime createdAt;
}
