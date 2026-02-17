package com.example.chillgram.domain.advertising.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("ad_gen_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdGenLog {

    @Id
    @Column("log_id")
    private Long logId;

    @Column("company_id")
    private Long companyId;

    @Column("user_id")
    private Long userId;

    @Column("product_id")
    private Long productId;

    @Column("ad_copy")
    private String adCopy;

    @Column("guideline")
    private String guideline;

    @Column("selection_reason")
    private String selectionReason;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
