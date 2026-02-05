package com.example.chillgram.domain.project.service;

import com.example.chillgram.domain.content.repository.ContentRepository;
import com.example.chillgram.domain.product.repository.ProductRepository;
import com.example.chillgram.domain.project.dto.ProjectResponse;
import com.example.chillgram.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProjectService {

    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;
    private final ContentRepository contentRepository;

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
}
