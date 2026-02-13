package com.example.chillgram.domain.project.service;

import com.example.chillgram.domain.content.repository.ContentRepository;
import com.example.chillgram.domain.project.dto.ProjectCreateRequest;
import com.example.chillgram.domain.project.dto.ProjectResponse;
import com.example.chillgram.domain.project.entity.Project;
import com.example.chillgram.domain.project.entity.ProjectImageAttachment;
import com.example.chillgram.domain.project.repository.ProjectImageAttachmentRepository;
import com.example.chillgram.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProjectService {

        private final ProjectRepository projectRepository;
        private final ContentRepository contentRepository;
        private final ProjectImageAttachmentRepository projectImageAttachmentRepository;

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
                        ProjectCreateRequest request,
                        List<String> imageUrls) {
                Project project = request.toEntity(productId, companyId, userId);

                return projectRepository.save(project)
                                .flatMap(savedProject -> {
                                        if (imageUrls == null || imageUrls.isEmpty()) {
                                                return Mono.just(savedProject);
                                        }
                                        return Flux.fromIterable(imageUrls)
                                                        .map(url -> ProjectImageAttachment.builder()
                                                                        .projectId(savedProject.getId())
                                                                        .fileUrl(url)
                                                                        .build())
                                                        .flatMap(projectImageAttachmentRepository::save)
                                                        .then(Mono.just(savedProject));
                                })
                                .map(savedProject -> ProjectResponse.of(savedProject, 0L));
        }
}
