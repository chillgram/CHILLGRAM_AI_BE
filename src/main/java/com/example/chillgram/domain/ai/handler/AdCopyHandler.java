package com.example.chillgram.domain.ai.handler;

import com.example.chillgram.domain.ai.dto.FinalCopyRequest;
import com.example.chillgram.domain.ai.dto.GuidelineRequest;
import com.example.chillgram.domain.ai.service.AdCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdCopyHandler {
        private final AdCopyService adCopyService;

        /**
         * 1단계: 가이드라인 5개 생성
         */
        public Mono<ServerResponse> generateGuidelines(ServerRequest request) {
                return request.bodyToMono(GuidelineRequest.class)
                                .publishOn(Schedulers.boundedElastic())
                                .map(adCopyService::generateGuidelines)
                                .flatMap(response -> ServerResponse.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(response))
                                .onErrorResume(e -> {
                                        log.error("가이드라인 생성 실패: {}", e.getMessage());
                                        return ServerResponse.badRequest()
                                                        .bodyValue(Map.of("error", e.getMessage()));
                                });
        }

        /**
         * 2단계: 가이드라인 선택 기반 최종 카피/프롬프트 생성
         */
        public Mono<ServerResponse> generateFinalCopy(ServerRequest request) {
                return request.bodyToMono(FinalCopyRequest.class)
                                .publishOn(Schedulers.boundedElastic())
                                .map(adCopyService::generateFinalCopy)
                                .flatMap(response -> ServerResponse.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(response))
                                .onErrorResume(e -> {
                                        log.error("최종 카피 생성 실패: {}", e.getMessage());
                                        return ServerResponse.badRequest()
                                                        .bodyValue(Map.of("error", e.getMessage()));
                                });
        }

        public Mono<ServerResponse> health(ServerRequest request) {
                return ServerResponse.ok()
                                .bodyValue(Map.of("status", "UP", "service", "ad-copy-generator-v2"));
        }
}
