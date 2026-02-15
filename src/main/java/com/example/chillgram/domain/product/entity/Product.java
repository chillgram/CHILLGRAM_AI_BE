package com.example.chillgram.domain.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("product")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @Column("product_id")
    private Long id;

    @Column("company_id")
    private Long companyId;

    @Column("name")
    private String name;

    @Column("category")
    private String category;

    @Column("description")
    private String description;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = false;

    @Column("review_url")
    private String reviewUrl;

    @CreatedBy
    @Column("created_by")
    private Long createdBy;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 엔티티 필드 업데이트 (null이 아닌 값만 반영)
     */
    public Product update(com.example.chillgram.domain.product.dto.ProductUpdateRequest request) {
        if (request.getName() != null) {
            this.name = request.getName();
        }
        if (request.getCategory() != null) {
            this.category = request.getCategory();
        }
        if (request.getDescription() != null) {
            this.description = request.getDescription();
        }

        if (request.getIsActive() != null) {
            this.isActive = request.getIsActive();
        }
        if (request.getReviewUrl() != null) {
            this.reviewUrl = request.getReviewUrl();
        }
        this.updatedAt = LocalDateTime.now();
        return this;
    }
}
