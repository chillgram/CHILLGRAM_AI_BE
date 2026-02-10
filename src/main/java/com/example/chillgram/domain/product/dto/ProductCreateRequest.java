package com.example.chillgram.domain.product.dto;

import com.example.chillgram.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제품 등록 요청 DTO
 * POST /api/products
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    /**
     * 제품명 (필수)
     */
    @NotBlank(message = "제품명은 필수입니다")
    private String name;

    /**
     * 카테고리 (선택)
     */
    private String category;

    /**
     * 제품 설명 (선택)
     */
    private String description;

    /**
     * 활성화 여부 (기본값: true)
     */
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 리뷰 URL (선택)
     */
    @Schema(description = "리뷰 URL")
    @URL(message = "유효한 URL 형식이 아닙니다")
    private String reviewUrl;

    /**
     * DTO -> Entity 변환
     */
    public Product toEntity(Long companyId, Long createdBy) {
        return Product.builder()
                .companyId(companyId)
                .name(this.name)
                .category(this.category)
                .description(this.description)
                .reviewUrl(this.reviewUrl)
                .isActive(this.isActive != null ? this.isActive : true)
                .createdBy(createdBy)
                .build();
    }
}
