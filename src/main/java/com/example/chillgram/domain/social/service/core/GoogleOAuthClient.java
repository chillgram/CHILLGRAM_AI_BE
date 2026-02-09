package com.example.chillgram.domain.social.service.core;

import com.example.chillgram.domain.social.config.SocialProperties;
import com.example.chillgram.domain.social.dto.youtube.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 역할: Google OAuth 토큰 서버와 통신하는 전용 클라이언트
 * - code → 토큰(access/refresh) 교환
 * - refresh_token → access_token 갱신
 */
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    // OAuth 설정(client_id, client_secret, redirect_uri 등)
    private final SocialProperties props;

    // Google 토큰 엔드포인트 호출용 HTTP 클라이언트
    private final WebClient web = WebClient.builder().build();

    // 인가 코드(code)를 토큰(access/refresh)으로 교환
    public Mono<GoogleTokenResponse> exchangeCode(String code) {
        var yt = props.oauth().youtube();

        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", yt.clientId());
        form.add("client_secret", yt.clientSecret());
        form.add("redirect_uri", yt.redirectUri());
        form.add("grant_type", "authorization_code");

        return postToken(form);
    }

    // refresh_token으로 access_token 갱신
    public Mono<GoogleTokenResponse> refresh(String refreshToken) {
        var yt = props.oauth().youtube();

        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", yt.clientId());
        form.add("client_secret", yt.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        return postToken(form);
    }

    // Google OAuth 토큰 엔드포인트 공통 호출
    private Mono<GoogleTokenResponse> postToken(LinkedMultiValueMap<String, String> form) {
        return web.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(GoogleTokenResponse.class);
    }
}
