package com.example.chillgram.domain.content.handler;

import com.example.chillgram.domain.content.dto.ContentUpdateRequest;
import com.example.chillgram.domain.content.service.ContentService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class ContentHandler {

    private final ContentService contentService;

    public ContentHandler(ContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * GET /api/projects/{projectId}/contents
     * 프로젝트별 콘텐츠 목록 조회
     */
    public Mono<ServerResponse> getContentsByProject(ServerRequest req) {
        long projectId = Long.parseLong(req.pathVariable("projectId"));
        return contentService.getContentsByProject(projectId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    /**
     * GET /api/contents/{contentId}
     * 콘텐츠 상세 조회
     */
    public Mono<ServerResponse> getContentById(ServerRequest req) {
        long contentId = Long.parseLong(req.pathVariable("contentId"));
        return contentService.getContentById(contentId)
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp));
    }

    /**
     * PUT /api/contents/{contentId}
     * 콘텐츠 수정
     */
    public Mono<ServerResponse> updateContent(ServerRequest req) {
        long contentId = Long.parseLong(req.pathVariable("contentId"));
        return req.bodyToMono(ContentUpdateRequest.class)
                .flatMap(body -> contentService.updateContent(contentId, body))
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp));
    }

    /**
     * DELETE /api/contents/{contentId}
     * 콘텐츠 삭제 (에셋도 함께 삭제)
     */
    public Mono<ServerResponse> deleteContent(ServerRequest req) {
        long contentId = Long.parseLong(req.pathVariable("contentId"));
        return contentService.deleteContent(contentId)
                .then(ServerResponse.noContent().build());
    }

    /**
     * GET /api/contents/{contentId}/assets
     * 콘텐츠의 에셋 목록 조회
     */
    public Mono<ServerResponse> getAssetsByContent(ServerRequest req) {
        long contentId = Long.parseLong(req.pathVariable("contentId"));
        return contentService.getAssetsByContent(contentId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    /**
     * DELETE /api/assets/{assetId}
     * 에셋 삭제
     */
    public Mono<ServerResponse> deleteAsset(ServerRequest req) {
        long assetId = Long.parseLong(req.pathVariable("assetId"));
        return contentService.deleteAsset(assetId)
                .then(ServerResponse.noContent().build());
    }
}
