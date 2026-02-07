package com.example.chillgram.domain.project.service;

import com.example.chillgram.domain.content.repository.ContentRepository;
import com.example.chillgram.domain.product.repository.ProductRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.http.codec.multipart.FilePart;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProjectService {

        private final ProductRepository productRepository;
        private final ProjectRepository projectRepository;
        private final ContentRepository contentRepository;
        private final ProjectImageAttachmentRepository projectImageAttachmentRepository;

        // 프로젝트 이미지 업로드 디렉토리
        private static final String UPLOAD_DIR = "/app/uploads/projects/";

        /**
         * 제품의 프로젝트 목록 조회 (Content 개수 포함)
         */
        public Mono<Map<String, Object>> getProjectsByProduct(Long productId) {
                // 1. 제품 메타데이터 조회 (이름など)
                Mono<String> productNameMono = productRepository.findById(productId)
                                .map(product -> product.getName())
                                .defaultIfEmpty("Unknown Product");

                // 2. 프로젝트 목록 조회
                Mono<List<ProjectResponse>> projectsMono = projectRepository.findAllByProductId(productId)
                                .flatMap(project -> {
                                        // 각 프로젝트별 Content 개수 조회 (N+1 문제 가능성 있으나 현재 규모상 OK)
                                        return contentRepository.countByProjectId(project.getId())
                                                        .defaultIfEmpty(0L)
                                                        .map(count -> ProjectResponse.of(project, count));
                                })
                                .collectList();

                // 3. 결과 조합
                return Mono.zip(productNameMono, projectsMono)
                                .map(tuple -> {
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("productName", tuple.getT1());
                                        result.put("projects", tuple.getT2());
                                        return result;
                                });
        }

        /**
         * 프로젝트 생성
         */
        @Transactional
        public Mono<ProjectResponse> createProject(Long productId, Long companyId, Long userId,
                        ProjectCreateRequest request,
                        List<String> imageUrls) {
                // 1. 엔티티 생성
                Project project = request.toEntity(productId, companyId, userId);

                // 2. DB 저장
                return projectRepository.save(project)
                                .flatMap(savedProject -> {
                                        // 3. 이미지 첨부파일 저장 (있는 경우)
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

        /**
         * 프로젝트 이미지 업로드 및 저장
         */
        public Mono<ProjectImageAttachment> saveProjectImage(Long projectId, FilePart filePart) {
                File uploadDir = new File(UPLOAD_DIR);
                if (!uploadDir.exists()) {
                        boolean created = uploadDir.mkdirs();
                        if (created) {
                                log.info("Created upload directory: {}", uploadDir.getAbsolutePath());
                        }
                }

                String originalFilename = filePart.filename();
                String safeFilename = UUID.randomUUID() + "_" + originalFilename;
                String filePath = Paths.get(UPLOAD_DIR, safeFilename).toString();

                String mimeType = filePart.headers().getContentType() != null
                                ? filePart.headers().getContentType().toString()
                                : "application/octet-stream";

                return filePart.transferTo(Paths.get(filePath))
                                .then(Mono.defer(() -> {
                                        long fileSize = new File(filePath).length();

                                        // DB에는 웹 접근 경로로 저장 (/uploads/projects/파일명)
                                        String webPath = "/uploads/projects/" + safeFilename;

                                        ProjectImageAttachment attachment = ProjectImageAttachment.builder()
                                                        .projectId(projectId)
                                                        .fileUrl(webPath)
                                                        .fileName(originalFilename)
                                                        .fileSize(fileSize)
                                                        .mimeType(mimeType)
                                                        .build();

                                        return projectImageAttachmentRepository.save(attachment);
                                }))
                                .doOnSuccess(att -> log.info("Project image saved: projectId={}, file={}", projectId,
                                                filePath))
                                .doOnError(e -> log.error("Project image upload failed", e));
        }
}
