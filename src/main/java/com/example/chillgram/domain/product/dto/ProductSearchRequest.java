package com.example.chillgram.domain.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProductSearchRequest {

    @Min(value = 0, message = "Page number must be 0 or greater")
    private int page = 0;

    @Positive(message = "Page size must be positive")
    private int size = 10;

    private String search = "";

    private String status = "ALL"; // ALL, ACTIVE, INACTIVE (Optional for now)
}
