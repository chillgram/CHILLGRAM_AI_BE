package com.example.chillgram.common.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Bearer 토큰 변환기
 * - 입력: Authorization 헤더 ("Bearer <JWT>")
 * - 역할: 요청을 Authentication 형태로 변환
 * - 처리:
 *   - Authorization 헤더가 없거나 Bearer 형식이 아니면 인증 시도하지 않음(Mono.empty)
 *   - Bearer 토큰 추출 후 Authentication(principal/credentials=token) 생성
 * - 검증 위치: JWT 서명/만료/클레임 검증은 AuthenticationManager에서 수행
 */
public class BearerTokenServerAuthenticationConverter implements ServerAuthenticationConverter {

    @Override
    public Mono<org.springframework.security.core.Authentication> convert(ServerWebExchange exchange) {
        // Authorization 헤더 조회
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return Mono.empty();
        }

        // 헤더가 없거나 Bearer 방식이 아니면 "인증정보 없음"으로 처리
        String token = header.substring("Bearer ".length()).trim();
        if (token.isBlank()) return Mono.empty();

        // Authentication principal/credentials 모두 token을 넣고 JwtAuthenticationManager에서 검증
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}