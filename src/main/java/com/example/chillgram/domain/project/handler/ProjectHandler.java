package com.example.chillgram.domain.project.handler;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.project.dto.ProjectCreateRequest;
import com.example.chillgram.domain.project.service.ProjectService;
import com.example.chillgram.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHandler {

    private final ProjectService projectService;
    private final AppUserRepository appUserRepository;

    /**
     * [GET] /api/products/{id}/projects
     * 제품의 프로젝트 목록 조회
     */
    public Mono<ServerResponse> getProjectsByProduct(ServerRequest request) {
        long productId;
        try {
            productId = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid product ID"));
        }

        return projectService.getProjectsByProduct(productId)
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    /**
     * [POST] /api/products/{id}/projects
     * 프로젝트 생성
     */
    public Mono<ServerResponse> createProject(ServerRequest request) {
        long productId;
        try {
            productId = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid product ID"));
        }

        // QaHandler와 동일한 인증 패턴 사용
        Mono<Long> userIdMono = request.principal()
                .map(principal -> {
                    if (principal instanceof UsernamePasswordAuthenticationToken auth) {
                        return (Long) auth.getPrincipal();
                    }
                    throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보를 확인할 수 없습니다.");
                })
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")));

        final long finalProductId = productId;
        return userIdMono.flatMap(userId -> appUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")))
                .flatMap(user -> {
                    Long companyId = user.getCompanyId();
                    if (companyId == null) {
                        return Mono.error(ApiException.of(ErrorCode.FORBIDDEN, "소속된 회사가 없어 프로젝트를 생성할 수 없습니다."));
                    }

                    return request.bodyToMono(ProjectCreateRequest.class)
                            .flatMap(req -> {
                                // TODO: 이미지 URL은 별도 업로드 로직에서 처리 예정
                                List<String> imageUrls = Collections.emptyList();
                                return projectService.createProject(finalProductId, companyId, userId, req, imageUrls);
                            })
                            .flatMap(response -> ServerResponse.status(HttpStatus.CREATED).bodyValue(response));
                }));
    }

    /**
     * [POST] /api/projects/{id}/images
     * 프로젝트 이미지 업로드
     */
    public Mono<ServerResponse> uploadProjectImage(ServerRequest request) {
        long projectId;
        try {
            projectId = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid project ID"));
        }

        final long finalProjectId = projectId;
        return request.multipartData()
                .flatMap(parts -> {
                    var filePart = parts.toSingleValueMap().get("file");
                    if (filePart == null || !(filePart instanceof org.springframework.http.codec.multipart.FilePart)) {
                        return ServerResponse.badRequest().bodyValue(Map.of("error", "파일이 첨부되지 않았습니다"));
                    }

                    return projectService
                            .saveProjectImage(finalProjectId,
                                    (org.springframework.http.codec.multipart.FilePart) filePart)
                            .flatMap(attachment -> ServerResponse.status(HttpStatus.CREATED).bodyValue(attachment))
                            .onErrorResume(e -> {
                                log.error("Project image upload failed", e);
                                return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .bodyValue(Map.of("error", "파일 업로드 실패: " + e.getMessage()));
                            });
                });
    }
}
