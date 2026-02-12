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
    public Mono<Long> insertProject(long companyId, long productId, String title, String desc) {
        return db.sql("""
            insert into project (company_id, product_id, project_type, title, description, status, created_at, updated_at)
            values ($1,$2,'AD',$3,$4,'ACTIVE',now(),now())
            returning project_id
        """)
                .bind(0, companyId)
                .bind(1, productId)
                .bind(2, title)
                .bind(3, desc)
                .map((r,m)->r.get("project_id",Long.class))
                .one();
    }

    // content insert + content_id 반환
    public Mono<Long> insertContent(long companyId,long productId,long projectId,
                                    String contentType,String platform,
                                    String title,String body,String tags) {
        return db.sql("""
            insert into content (
                company_id, product_id, project_id,
                content_type, platform,
                title, body, status, tags,
                view_count, like_count, share_count,
                created_at, updated_at
            )
            values ($1,$2,$3,$4,$5,$6,$7,'DRAFT',$8,0,0,0,now(),now())
            returning content_id
        """)
                .bind(0,companyId)
                .bind(1,productId)
                .bind(2,projectId)
                .bind(3,contentType)
                .bind(4,platform)
                .bind(5,title)
                .bind(6,body)
                .bind(7,tags)
                .map((r,m)->r.get("content_id",Long.class))
                .one();
    }

    public Mono<Long> insertContentAsset(
            long contentId,
            String fileUrl,
            String mimeType,
            Long fileSize
    ) {
        return db.sql("""
            insert into content_asset (
                content_id, asset_type, file_url, mime_type,
                file_size, sort_order, created_at
            )
            values ($1,'PRIMARY',$2,$3,$4,0,now())
            returning asset_id
        """)
                .bind(0,contentId)
                .bind(1,fileUrl)
                .bind(2,mimeType)
                .bind(3,fileSize)
                .map((r,m)->r.get("asset_id",Long.class))
                .one();
    }
}
