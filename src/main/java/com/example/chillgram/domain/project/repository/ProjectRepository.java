package com.example.chillgram.domain.project.repository;

import com.example.chillgram.domain.project.entity.Project;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ProjectRepository extends R2dbcRepository<Project, Long> {
    Flux<Project> findAllByProductId(Long productId);
}
