package com.example.chillgram.domain.product.dto;

import com.example.chillgram.domain.product.entity.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private Long companyId;
    private String name;
    private String category;
    private String description;
    private BigDecimal price;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .companyId(product.getCompanyId())
                .name(product.getName())
                .category(product.getCategory())
                .description(product.getDescription())
                .price(product.getPrice())
                .isActive(product.getIsActive())
                .createdBy(product.getCreatedBy())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
