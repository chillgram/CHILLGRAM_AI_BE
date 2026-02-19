package com.example.chillgram.domain.content.repository;

import com.example.chillgram.domain.content.entity.Content;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContentRepository extends R2dbcRepository<Content, Long> {

    Flux<Content> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    // 베이스 이미지 조회용 (gcs_img_urlO, 타입 != MOCKUP)
    @Query("SELECT * FROM content WHERE product_id = :productId AND gcs_img_url IS NOT NULL AND content_type != 'MOCKUP' ORDER BY created_at DESC")
    Flux<Content> findBaseImagesByProductId(Long productId);

    Mono<Long> countByProjectId(Long projectId);
}
