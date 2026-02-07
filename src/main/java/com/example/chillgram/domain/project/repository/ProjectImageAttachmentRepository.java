package com.example.chillgram.domain.project.repository;

import com.example.chillgram.domain.project.entity.ProjectImageAttachment;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ProjectImageAttachmentRepository extends R2dbcRepository<ProjectImageAttachment, Long> {

    Flux<ProjectImageAttachment> findByProjectId(Long projectId);
}
