package com.example.chillgram.domain.ai.handler;

import com.example.chillgram.domain.ai.service.AdCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdCopyHandler {
        private final AdCopyService adCopyService;

        /**
         * 1단계: 가이드라인 5개 생성 (Deprecated/Disabled)
         */
        public Mono<ServerResponse> generateGuidelines(ServerRequest request) {
                return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "This endpoint is deprecated. Use /api/v1/ads/ad-guides"));
        }

        /**
         * 2단계: 가이드라인 선택 기반 최종 카피/프롬프트 생성 (Deprecated/Disabled)
         */
        public Mono<ServerResponse> generateFinalCopy(ServerRequest request) {
                return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "This endpoint is deprecated. Use /api/v1/ads/ad-copies"));
        }

        public Mono<ServerResponse> health(ServerRequest request) {
                return ServerResponse.ok()
                                .bodyValue(Map.of("status", "UP", "service", "ad-copy-generator-v2"));
        }
}
