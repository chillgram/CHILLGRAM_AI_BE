package com.example.chillgram.domain.advertising.handler;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.advertising.dto.AdCreateRequest;
import com.example.chillgram.domain.advertising.dto.AdGuidesRequest;
import com.example.chillgram.domain.advertising.dto.AdTrendsRequest;
import com.example.chillgram.domain.advertising.dto.FileStorage;
import com.example.chillgram.domain.advertising.service.AdService;
import com.example.chillgram.domain.ai.dto.AdCopiesRequest;
import com.example.chillgram.common.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
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
    private final ObjectMapper objectMapper;
    private final FileStorage fileStorage;

    public AdHandler(AdService adService, ObjectMapper objectMapper, FileStorage fileStorage) {
        this.adService = adService;
        this.objectMapper = objectMapper;
        this.fileStorage = fileStorage;
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
                                    .bodyValue(resp));
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

        // JWT userId 추출
        Mono<Long> userIdMono = req.principal()
                .map(principal -> {

                    if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
                        return ((AuthPrincipal) auth.getPrincipal()).userId();
                    }
                    throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보를 확인할 수 없습니다.");
                })
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")));

        return userIdMono.flatMap(userId -> req.multipartData()
                .flatMap(parts -> {

                    Part payloadPart = parts.getFirst("payload");
                    Part filePart = parts.getFirst("file");

                    if (!(payloadPart instanceof FormFieldPart p)) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "payload part required"));
                    }
                    if (!(filePart instanceof FilePart f)) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "file part required"));
                    }

                    AdCreateRequest body;
                    try {
                        body = objectMapper.readValue(p.value(), AdCreateRequest.class);
                    } catch (Exception e) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "invalid payload json"));
                    }

                    return fileStorage.store(f)
                            .flatMap(stored -> adService.createProjectAndContents(productId, body, stored, userId))
                            .flatMap(res -> ServerResponse.ok().bodyValue(res));
                }));
    }
}