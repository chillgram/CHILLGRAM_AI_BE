package com.example.chillgram.domain.social.service;
import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.social.config.SocialProperties;
import com.example.chillgram.domain.social.dto.OAuthTokenPayload;
import com.example.chillgram.domain.social.dto.SnsAccountDto;
import com.example.chillgram.domain.social.dto.SnsPlatform;
import com.example.chillgram.domain.social.dto.youtube.GoogleTokenResponse;
import com.example.chillgram.domain.social.dto.youtube.YoutubeChannelsResponse;
import com.example.chillgram.domain.social.entity.SocialAccount;
import com.example.chillgram.domain.social.repository.SocialAccountRepository;
import com.example.chillgram.domain.social.service.core.GoogleOAuthClient;
import com.example.chillgram.domain.social.service.core.YoutubeTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class YoutubeOAuthService {

    // YouTube 플랫폼 문자열
    private static final String PLATFORM_YT = SnsPlatform.YOUTUBE.name();

    // 만료 판정 여유 시간(만료 직전 토큰은 갱신 대상으로 처리)
    private static final Duration TOKEN_SKEW = Duration.ofMinutes(2);

    private final SocialProperties props;
    private final SocialAccountRepository repo;
    private final ReactiveStringRedisTemplate redis;
    private final GoogleOAuthClient oauth;
    private final YoutubeTokenStore tokenStore;

    // YouTube Data API 호출용
    private final WebClient web = WebClient.builder().build();

    // OAuth 인증 시작 URL 발급(state 저장 포함)
    public Mono<String> issueAuthUrl(long companyId) {
        String state = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(props.oauth().stateTtlSeconds());

        return redis.opsForValue()
                .set(stateKey(state), Long.toString(companyId), ttl)
                .flatMap(ok -> ok
                        ? Mono.just(buildAuthUrl(state))
                        : Mono.error(new IllegalStateException("state 저장 실패")));
    }

    // state 검증 후 code를 토큰으로 교환하고 연결 정보 저장
    public Mono<SnsAccountDto> exchange(long companyId, String code, String state) {
        return consumeState(state)
                .filter(saved -> saved == companyId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "state가 유효하지 않습니다.")))
                .then(oauth.exchangeCode(code))
                .flatMap(token -> persistConnection(companyId, token));
    }

    // 필요 시 refresh 후 유효한 access_token 반환
    public Mono<String> getValidAccessToken(long companyId) {
        return repo.findOne(companyId, PLATFORM_YT, true)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.SOCIAL_ACCOUNT_NOT_CONNECTED, "YouTube 미연결")))
                .flatMap(this::ensureValidToken);
    }

    // 토큰/채널 조회 후 SecretManager + social_account 저장(업서트)
    private Mono<SnsAccountDto> persistConnection(long companyId, GoogleTokenResponse token) {
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn());

        return repo.findOne(companyId, PLATFORM_YT, null)
                .defaultIfEmpty(SocialAccount.builder()
                        .companyId(companyId)
                        .platform(PLATFORM_YT)
                        .isActive(true)
                        .build())
                .flatMap(existing -> fetchMyChannel(token.accessToken())
                        .flatMap(ch -> resolveRefreshToken(existing, token)
                                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "refresh_token 없음. 재동의 필요")))
                                .flatMap(refresh -> tokenStore.save(companyId,
                                        new OAuthTokenPayload(token.accessToken(), refresh, expiresAt.getEpochSecond())))
                                .flatMap(tokenRef -> repo.save(upsertEntity(companyId, existing, tokenRef, ch)))
                                .map(saved -> SnsAccountDto.youtube(saved.getAccountLabel()))
                        )
                );
    }

    // 만료 여부 판단 후 필요 시 refresh 수행
    private Mono<String> ensureValidToken(SocialAccount acc) {
        String ref = acc.getTokenRef();
        if (ref == null || ref.isBlank()) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "token_ref 비어있음"));
        }

        return tokenStore.readLatest(ref)
                .flatMap(opt -> {
                    if (opt.isEmpty()) return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "토큰 읽기 실패"));
                    OAuthTokenPayload cur = opt.get();

                    if (Instant.now().isBefore(cur.expiresAt().minus(TOKEN_SKEW))) {
                        return Mono.just(cur.accessToken());
                    }

                    String refresh = cur.refreshToken();
                    if (refresh == null || refresh.isBlank()) {
                        return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "refresh_token 없음. 재인증 필요"));
                    }

                    return oauth.refresh(refresh)
                            .flatMap(refreshed -> tokenStore.save(acc.getCompanyId(),
                                    new OAuthTokenPayload(
                                            refreshed.accessToken(),
                                            refresh,
                                            Instant.now().plusSeconds(refreshed.expiresIn()).getEpochSecond()
                                    )).thenReturn(refreshed.accessToken()));
                });
    }

    // refresh_token 확보(응답에 없으면 기존 SecretManager 저장값에서 복원)
    private Mono<String> resolveRefreshToken(SocialAccount existing, GoogleTokenResponse token) {
        if (token.refreshToken() != null && !token.refreshToken().isBlank()) {
            return Mono.just(token.refreshToken());
        }
        if (existing.getTokenRef() == null || existing.getTokenRef().isBlank()) {
            return Mono.empty();
        }

        return tokenStore.readLatest(existing.getTokenRef())
                .map(opt -> opt.map(OAuthTokenPayload::refreshToken).orElse(null))
                .flatMap(r -> (r == null || r.isBlank()) ? Mono.empty() : Mono.just(r));
    }

    // social_account 저장 엔티티 생성(업서트용)
    private SocialAccount upsertEntity(long companyId, SocialAccount existing, String tokenRef, YoutubeChannelsResponse.Item ch) {
        String title = (ch.snippet() != null && ch.snippet().title() != null && !ch.snippet().title().isBlank())
                ? ch.snippet().title()
                : PLATFORM_YT;

        return SocialAccount.builder()
                .socialAccountId(existing.getSocialAccountId())
                .companyId(existing.getCompanyId() != null ? existing.getCompanyId() : companyId)
                .platform(PLATFORM_YT)
                .accountLabel(title)
                .tokenRef(tokenRef)
                .isActive(true)
                .connectedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    // YouTube 채널 정보 조회(mine=true)
    private Mono<YoutubeChannelsResponse.Item> fetchMyChannel(String accessToken) {
        return web.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("www.googleapis.com")
                        .path("/youtube/v3/channels")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("mine", "true")
                        .build())
                .headers(h -> h.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(YoutubeChannelsResponse.class)
                .flatMap(res -> (res.items() == null || res.items().isEmpty())
                        ? Mono.error(ApiException.of(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND, "YouTube 채널 없음"))
                        : Mono.just(res.items().get(0)));
    }

    // Google OAuth 인증 URL 생성
    private String buildAuthUrl(String state) {
        var yt = props.oauth().youtube();
        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", yt.clientId())
                .queryParam("redirect_uri", yt.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", yt.scopes()))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    // OAuth state Redis key
    private String stateKey(String state) {
        return "oauth:youtube:state:" + state;
    }

    // state 조회 후 1회성 소비(delete)
    private Mono<Long> consumeState(String state) {
        String key = stateKey(state);
        return redis.opsForValue().get(key)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "state 만료")))
                .flatMap(v -> redis.delete(key).thenReturn(Long.parseLong(v)));
    }
}