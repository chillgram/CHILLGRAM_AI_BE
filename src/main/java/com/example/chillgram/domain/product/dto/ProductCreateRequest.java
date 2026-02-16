package com.example.chillgram.domain.product.dto;

import com.example.chillgram.domain.product.entity.Product;
import jakarta.validation.constraints.NotBlank;

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
     * 활성화 여부 (기본값: false)
     */
    @Builder.Default
    private Boolean isActive = false;

    /**
     * DTO -> Entity 변환
     */
    public Product toEntity(Long companyId, Long createdBy) {
        return Product.builder()
                .companyId(companyId)
                .name(this.name)
                .category(this.category)
                .description(this.description)
                .isActive(this.isActive != null ? this.isActive : false)
                .createdBy(createdBy)
                .build();
    }
}
