package com.example.chillgram.domain.advertising.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class AdGenLogRepository {

    private final DatabaseClient db;

    public AdGenLogRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Long> save(Long companyId, Long userId, Long productId, String adCopy, String guideline,
            String selectionReason) {
        return db
                .sql("""
                        INSERT INTO ad_gen_log (company_id, user_id, product_id, ad_copy, guideline, selection_reason, created_at)
                        VALUES (:companyId, :userId, :productId, :adCopy, :guideline, :selectionReason, NOW())
                        RETURNING log_id
                        """)
                .bind("companyId", companyId)
                .bind("userId", userId)
                .bind("productId", productId)
                .bind("adCopy", adCopy == null ? "" : adCopy)
                .bind("guideline", guideline == null ? "" : guideline)
                .bind("selectionReason", selectionReason == null ? "" : selectionReason)
                .map((row, meta) -> row.get("log_id", Long.class))
                .one();
    }
}
