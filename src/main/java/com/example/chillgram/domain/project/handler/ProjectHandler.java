package com.example.chillgram.domain.project.handler;

import com.example.chillgram.domain.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHandler {

    private final ProjectService projectService;

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
}
