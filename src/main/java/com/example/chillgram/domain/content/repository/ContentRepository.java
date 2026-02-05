package com.example.chillgram.domain.content.repository;

import com.example.chillgram.domain.content.entity.Content;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface ContentRepository extends R2dbcRepository<Content, Long> {

    // 프로젝트별 콘텐츠 개수 조회
    Mono<Long> countByProjectId(Long projectId);
}
