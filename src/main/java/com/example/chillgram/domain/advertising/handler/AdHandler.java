package com.example.chillgram.domain.advertising.handler;

import com.example.chillgram.domain.advertising.dto.AdCreateRequest;
import com.example.chillgram.domain.advertising.dto.AdGuidesRequest;
import com.example.chillgram.domain.advertising.dto.AdTrendsRequest;
import com.example.chillgram.domain.advertising.service.AdService;
import com.example.chillgram.domain.ai.dto.AdCopiesRequest;
import com.example.chillgram.domain.ai.dto.AdCopiesResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 역할: 광고 관련 HTTP 요청 처리
 */
@Component
public class AdHandler {

    private final AdService adService;

    public AdHandler(AdService adService) {
        this.adService = adService;
    }

    /**
     * 광고 트렌드 분석 조회
     */
    public Mono<ServerResponse> getAdTrends(ServerRequest request) {
        long productId = Long.parseLong(request.pathVariable("id"));

        return request.bodyToMono(AdTrendsRequest.class)
                .flatMap(req -> {
                    return adService.getAdTrends(productId, req.baseDate())
                            .flatMap(resp -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(resp)
                            );
                });
    }

    /**
     * 광고 가이드라인 생성
     * POST /api/v1/products/{id}/ad-guides
     */
    public Mono<ServerResponse> createAdGuides(ServerRequest request) {
        long productId = Long.parseLong(request.pathVariable("id"));

        return request.bodyToMono(AdGuidesRequest.class)
                .flatMap(req -> adService.createAdGuides(productId, req))
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp));
    }

    public Mono<ServerResponse> createAdCopies(ServerRequest req) {
        long productId = Long.parseLong(req.pathVariable("id"));

        return req.bodyToMono(AdCopiesRequest.class)
                .flatMap(body -> adService.createAdCopies(productId, body))
                .flatMap(resp -> ServerResponse.ok().bodyValue(resp));
    }

    public Mono<ServerResponse> createAdProjectAndContents(ServerRequest req) {
        long productId = Long.parseLong(req.pathVariable("id"));

        return req.bodyToMono(AdCreateRequest.class)
                .flatMap(body -> adService.createProjectAndContents(productId, body))
                .flatMap(res -> ServerResponse.ok().bodyValue(res));
    }
}