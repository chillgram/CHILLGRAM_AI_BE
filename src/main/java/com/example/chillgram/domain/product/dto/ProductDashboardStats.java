package com.example.chillgram.domain.product.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductDashboardStats {
    private Long totalCount;
    private Long activeCount;
    private Long inactiveCount;
}
