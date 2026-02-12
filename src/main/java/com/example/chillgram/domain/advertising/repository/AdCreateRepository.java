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
    public Mono<Long> insertProject(long companyId, long productId, String projectType, String title, String desc,
            long createdBy, Integer focus, Integer target, String userImgGcsUrl) {
        var spec = db.sql("""
                    insert into project (
                        company_id, product_id, project_type, title, description,
                        status, created_by, ad_message_focus, ad_message_target, userimg_gcs_url,
                        created_at, updated_at
                    )
                    values ($1, $2, $3, $4, $5, 'ACTIVE', $6, $7, $8, $9, now(), now())
                    returning project_id
                """)
                .bind(0, companyId)
                .bind(1, productId)
                .bind(2, projectType == null ? "AD" : projectType)
                .bind(3, title == null ? "" : title)
                .bind(4, desc == null ? "" : desc)
                .bind(5, createdBy)
                .bind(6, focus == null ? 0 : focus)
                .bind(7, target == null ? 0 : target);

        if (userImgGcsUrl == null) {
            spec = spec.bindNull(8, String.class);
        } else {
            spec = spec.bind(8, userImgGcsUrl);
        }

        return spec.map((r, m) -> r.get("project_id", Long.class)).one();
    }

    .one();

    }

    // content insert + content_id 반환
    // content insert + content_id 반환
    public Mono<Long> insertContent(long companyId, long productId, long projectId,
            String contentType, String platform,
            String title, String body, String tags,
            long createdBy, Integer bannerRatio) {
        var spec = db.sql("""
                    insert into content (
                        company_id, product_id, project_id,
                        content_type, platform,
                        title, body, status, tags,
                        view_count, like_count, share_count,
                        created_by, banner_ratio,
                        created_at, updated_at
                    )
                    values ($1,$2,$3,$4,$5,$6,$7,'DRAFT',$8,0,0,0,$9,$10,now(),now())
                    returning content_id
                """)
                .bind(0, companyId)
                .bind(1, productId)
                .bind(2, projectId)
                .bind(3, contentType == null ? "" : contentType)
                .bind(4, platform == null ? "" : platform)
                .bind(5, title == null ? "" : title)
                .bind(6, body == null ? "" : body);

        if (tags == null) {
            spec = spec.bindNull(7, String.class);
        } else {
            spec = spec.bind(7, tags);
        }

        return spec.bind(8, createdBy)
                .bind(9, bannerRatio == null ? 0 : bannerRatio)
                .map((r, m) -> r.get("content_id", Long.class))
                .one();
    }

    public Mono<Long> insertContentAsset(
            long contentId,
            String fileUrl,
            String mimeType,
            Long fileSize) {
        var spec = db.sql("""
                    insert into content_asset (
                        content_id, asset_type, file_url, mime_type,
                        file_size, sort_order, created_at
                    )
                    values ($1,'PRIMARY',$2,$3,$4,0,now())
                    returning asset_id
                """)
                .bind(0, contentId)
                .bind(1, fileUrl == null ? "" : fileUrl);

        if (mimeType == null) {
            spec = spec.bindNull(2, String.class);
        } else {
            spec = spec.bind(2, mimeType);
        }

        return spec.bind(3, fileSize == null ? 0L : fileSize)
                .map((r, m) -> r.get("asset_id", Long.class))
                .one();
    }
}
