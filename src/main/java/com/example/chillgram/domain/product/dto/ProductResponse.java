package com.example.chillgram.domain.product.dto;

import com.example.chillgram.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private Long companyId;
    private String name;
    private String category;
    private String description;
    @Schema(description = "리뷰 URL")
    private String reviewUrl;

    private Boolean isActive;
    private Long createdBy;
    private String createdByName; // Added for enrichment
    private String companyName; // Added for enrichment
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .companyId(product.getCompanyId())
                .name(product.getName())
                .category(product.getCategory())
                .description(product.getDescription())
                .reviewUrl(product.getReviewUrl())

                .isActive(product.getIsActive())
                .createdBy(product.getCreatedBy())
                // createdByName and companyName should be set by the service
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
