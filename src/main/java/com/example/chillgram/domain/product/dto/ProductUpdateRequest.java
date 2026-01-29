package com.example.chillgram.domain.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 제품 수정 요청 DTO
 * PUT /api/products/{id}
 * 
 * 모든 필드가 Optional - null이면 기존 값 유지
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    /**
     * 제품명 (null이면 기존 값 유지)
     */
    private String name;

    /**
     * 카테고리 (null이면 기존 값 유지)
     */
    private String category;

    /**
     * 제품 설명 (null이면 기존 값 유지)
     */
    private String description;

    /**
     * 가격 (null이면 기존 값 유지)
     */
    private BigDecimal price;

    /**
     * 활성화 여부 (null이면 기존 값 유지)
     */
    private Boolean isActive;
}
