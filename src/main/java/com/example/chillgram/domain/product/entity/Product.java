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

import java.math.BigDecimal;
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

    // UI Requirements: Price field
    @Column("price")
    private BigDecimal price;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreatedBy
    @Column("created_by")
    private String createdBy;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
