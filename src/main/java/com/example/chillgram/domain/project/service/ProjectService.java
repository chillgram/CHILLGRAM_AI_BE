package com.example.chillgram.domain.project.service;

import com.example.chillgram.domain.product.repository.ProductRepository;
import com.example.chillgram.domain.content.repository.ContentRepository;
import com.example.chillgram.domain.project.dto.ProjectCreateRequest;
import com.example.chillgram.domain.project.dto.ProjectResponse;
import com.example.chillgram.domain.project.entity.Project;
import com.example.chillgram.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class ProjectService {

        private final ProjectRepository projectRepository;
        private final ContentRepository contentRepository;
        private final ProductRepository productRepository;
        private final com.example.chillgram.common.google.GcsFileStorage gcs;

        public ProjectService(ProjectRepository projectRepository, ContentRepository contentRepository,
                        ProductRepository productRepository, com.example.chillgram.common.google.GcsFileStorage gcs) {
                this.projectRepository = projectRepository;
                this.contentRepository = contentRepository;
                this.productRepository = productRepository;
                this.gcs = gcs;
        }

        /**
         * 제품의 프로젝트 목록 조회 (Optimized: Single query for counts)
         */
        public Mono<List<ProjectResponse>> getProjectsByProduct(Long productId) {
                return projectRepository.findAllByProductIdWithCount(productId)
                                .map(pc -> new ProjectResponse(
                                                pc.projectId(),
                                                pc.title(),
                                                pc.projectType() != null ? pc.projectType().name() : null,
                                                pc.status(),
                                                pc.adMessageFocus(),
                                                pc.adMessageTarget(),
                                                pc.contentCount(),
                                                pc.createdAt(),
                                                gcs.toPublicUrl(pc.userImgGcsUrl()),
                                                gcs.toPublicUrl(pc.dielineGcsUrl()),
                                                gcs.toPublicUrl(pc.mockupResultUrl())))
                                .collectList();
        }

        /**
         * 프로젝트 생성
         */
        @Transactional
        public Mono<ProjectResponse> createProject(Long productId, Long companyId, Long userId,
                        ProjectCreateRequest request) {
                Project project = request.toEntity(productId, companyId, userId);

                return projectRepository.save(project)
                                .flatMap(savedProject -> {
                                        // Product activation logic
                                        return productRepository.findById(productId)
                                                        .flatMap(product -> {
                                                                if (!Boolean.TRUE.equals(product.getIsActive())) {
                                                                        product.setIsActive(true);
                                                                        return productRepository.save(product);
                                                                }
                                                                return Mono.just(product);
                                                        })
                                                        .thenReturn(savedProject);
                                })
                                .map(savedProject -> {
                                        ProjectResponse resp = ProjectResponse.of(savedProject, 0L);
                                        return new ProjectResponse(
                                                        resp.projectId(), resp.title(), resp.type(), resp.status(),
                                                        resp.adMessageFocus(), resp.adMessageTarget(),
                                                        resp.contentCount(), resp.createdAt(),
                                                        gcs.toPublicUrl(resp.userImgGcsUrl()),
                                                        gcs.toPublicUrl(resp.dielineGcsUrl()),
                                                        gcs.toPublicUrl(resp.mockupResultUrl()));
                                });
        }

}
