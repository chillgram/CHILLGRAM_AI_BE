package com.example.chillgram.domain.project.controller;

import com.example.chillgram.common.security.AuthPrincipal;
import com.example.chillgram.domain.project.dto.ProjectCreateRequest;
import com.example.chillgram.domain.project.dto.ProjectResponse;
import com.example.chillgram.domain.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project", description = "프로젝트 관리 API")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로젝트 생성", description = "특정 제품(Product) 하위에 새로운 프로젝트를 생성합니다.")
    public Mono<ProjectResponse> createProject(
            @Parameter(description = "제품 ID", required = true) @PathVariable Long productId,
            @Valid @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        return projectService.createProject(
                productId,
                principal.companyId(),
                principal.userId(),
                request,
                null);
    }

    @GetMapping
    @Operation(summary = "프로젝트 목록 조회", description = "특정 제품(Product)의 프로젝트 목록을 조회합니다.")
    public Mono<List<ProjectResponse>> getProjects(
            @Parameter(description = "제품 ID", required = true) @PathVariable Long productId) {
        return projectService.getProjectsByProduct(productId);
    }
}
