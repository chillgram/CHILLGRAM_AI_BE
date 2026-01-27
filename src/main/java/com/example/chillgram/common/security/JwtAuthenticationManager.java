package com.example.chillgram.common.security;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 인증 매니저
 * - 입력: Bearer 토큰(Authorization 헤더에서 추출된 JWT)
 * - 검증: 서명/만료/형식(JwtTokenService.parse)
 * - 제한: typ=access 만 허용 (refresh 토큰으로 API 접근 차단)
 * - 결과: Authentication 생성(principal=userId, authorities=ROLE_xxx) -> SecurityContext 저장
 */
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenService jwt;

    public JwtAuthenticationManager(JwtTokenService jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());

        return Mono.fromCallable(() -> jwt.parse(token))
                .flatMap(jws -> {
                    if (!"access".equals(jwt.getType(jws))) {
                        return Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "인증 실패: access 토큰이 아닙니다."));
                    }

                    long userId = jwt.getUserId(jws);
                    String role = jwt.getRole(jws);

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    // principal=userId
                    return Mono.just(new UsernamePasswordAuthenticationToken(userId, token, authorities));
                });
    }
}