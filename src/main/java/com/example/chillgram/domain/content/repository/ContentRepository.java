package com.example.chillgram.domain.content.repository;

import com.example.chillgram.domain.content.entity.Content;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContentRepository extends R2dbcRepository<Content, Long> {

    Flux<Content> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Mono<Long> countByProjectId(Long projectId);
}
