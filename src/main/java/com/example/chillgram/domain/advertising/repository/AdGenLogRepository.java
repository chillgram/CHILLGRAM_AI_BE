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
                        INSERT INTO ad_generation_log (company_id, user_id, product_id, ad_copy, guideline, selection_reason, created_at)
                        VALUES ($1, $2, $3, $4, $5, $6, NOW())
                        RETURNING log_id
                        """)
                .bind(0, companyId)
                .bind(1, userId)
                .bind(2, productId)
                .bind(3, adCopy == null ? "" : adCopy)
                .bind(4, guideline == null ? "" : guideline)
                .bind(5, selectionReason == null ? "" : selectionReason)
                .map((row, meta) -> row.get("log_id", Long.class))
                .one();
    }
}
