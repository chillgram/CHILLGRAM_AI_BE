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
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProjectService {

        private final ProjectRepository projectRepository;
        private final ContentRepository contentRepository;
        private final ProductRepository productRepository;

        /**
         * 제품의 프로젝트 목록 조회 (Content 개수 포함)
         */
        public Mono<List<ProjectResponse>> getProjectsByProduct(Long productId) {
                return projectRepository.findAllByProductId(productId)
                                .flatMap(project -> contentRepository.countByProjectId(project.getId())
                                                .defaultIfEmpty(0L)
                                                .map(count -> ProjectResponse.of(project, count)))
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
                                .map(savedProject -> ProjectResponse.of(savedProject, 0L));
        }

}
