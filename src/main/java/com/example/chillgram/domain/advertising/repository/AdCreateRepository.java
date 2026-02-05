package com.example.chillgram.domain.advertising.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class AdCreateRepository {

    private final DatabaseClient db;

    public AdCreateRepository(DatabaseClient db) {
        this.db = db;
    }

    // product_id로 company_id 조회 (FK/무결성 때문에 필수)
    public Mono<Long> findCompanyIdByProductId(long productId) {
        String sql = """
            select company_id
            from product
            where product_id = $1
            """;

        return db.sql(sql)
                .bind(0, productId)
                .map((row, meta) -> row.get("company_id", Long.class))
                .one();
    }

    // project insert + project_id 반환
    public Mono<Long> insertProject(
            long companyId,
            long productId,
            String title,
            String description
    ) {
        String sql = """
            insert into project (
                company_id, product_id, project_type,
                title, description,
                status,
                created_by, created_at, updated_at
            )
            values (
                $1, $2, 'AD',
                $3, $4,
                'ACTIVE',
                null, now(), now()
            )
            returning project_id
            """;

        return db.sql(sql)
                .bind(0, companyId)
                .bind(1, productId)
                .bind(2, title)
                .bind(3, description)
                .map((row, meta) -> row.get("project_id", Long.class))
                .one();
    }

    // content insert + content_id 반환
    public Mono<Long> insertContent(
            long companyId,
            long productId,
            long projectId,
            String contentType, // IMAGE/VIDEO/TEXT
            String platform,    // INSTAGRAM/FACEBOOK/YOUTUBE
            String title,
            String body,
            String tags
    ) {
        String sql = """
            insert into content (
                company_id, product_id, project_id,
                content_type, platform,
                title, body,
                status, tags,
                view_count, like_count, share_count,
                social_post_id, published_at,
                created_by, created_at, updated_at
            )
            values (
                $1, $2, $3,
                $4, $5,
                $6, $7,
                'DRAFT', $8,
                0, 0, 0,
                null, null,
                null, now(), now()
            )
            returning content_id
            """;

        return db.sql(sql)
                .bind(0, companyId)
                .bind(1, productId)
                .bind(2, projectId)
                .bind(3, contentType)
                .bind(4, platform)
                .bind(5, title)
                .bind(6, body)
                .bind(7, tags)
                .map((row, meta) -> row.get("content_id", Long.class))
                .one();
    }
}
