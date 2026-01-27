package com.example.chillgram.common.mail;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.auth.constant.AuthConst;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 이메일 인증용 토큰을 발급/소비(1회성)하는 서비스.
 * - 회원가입 후 이메일 인증 링크에 포함될 토큰을 생성하고 Redis에 저장한다.
 * - 사용자가 인증 링크를 클릭하면 토큰을 1회성으로 소비(조회 후 즉시 삭제)하여 재사용을 방지한다.
 * - raw token을 SHA-256 해시로 변환한 값만 Redis key로 사용하여, Redis 유출 시 피해를 줄인다.
 * - TTL을 적용하여 만료된 토큰은 자동 제거된다.
 *
 */
@Service
public class EmailVerificationTokenService {

    private final ReactiveStringRedisTemplate redis;
    private final String verifyBaseUrl;

    public EmailVerificationTokenService(
            ReactiveStringRedisTemplate redis,
            @Value("${app.auth.verify-base-url}") String verifyBaseUrl
    ) {
        this.redis = redis;
        this.verifyBaseUrl = verifyBaseUrl;
    }

    /**
     * 이메일 인증 토큰 발급.
     * 흐름:
     * - raw token(UUID 조합)을 생성한다. (랜덤 문자열)
     * - raw token을 SHA-256 해시로 변환한다.
     * - Redis에 (tokenHash -> userId) 매핑을 TTL과 함께 저장한다.
     * - 이메일에 담을 verifyUrl을 구성하여 반환한다.
     *
     * @param userId 인증 대상 사용자 ID
     * @return 발급된 토큰 정보(rawToken, tokenHash, verifyUrl)
     */
    public Mono<IssuedToken> issue(Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "invalid userId=" + userId));
        }

        // 이메일로 전달될 원문 토큰
        String raw = UUID.randomUUID() + "-" + UUID.randomUUID();

        // Redis 키는 해시 기반(원문 저장 금지)
        String key = AuthConst.emailVerifyKeyByRawToken(raw);

        // 링크 안전성을 위해 URL 인코딩 후 baseUrl 뒤에 붙인다.
        String verifyUrl = verifyBaseUrl + URLEncoder.encode(raw, StandardCharsets.UTF_8);

        return redis.opsForValue()
                .set(key, String.valueOf(userId), AuthConst.EMAIL_TOKEN_TTL)
                .flatMap(ok -> Boolean.TRUE.equals(ok)
                        ? Mono.just(new IssuedToken(raw, AuthConst.sha256Hex(raw), verifyUrl))
                        : Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "failed to save verify token"))
                );
    }
    /**
     * 이메일 인증 토큰 1회성 소비(원자적).
     * - Lua로 GET + DEL을 한 번에 수행하여 재사용/중복소비 레이스 방지
     */
    public Mono<Long> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "rawToken is blank"));
        }

        String key = AuthConst.emailVerifyKeyByRawToken(rawToken);

        // Lua: val = GET key; if val then DEL key end; return val
        RedisScript<String> getAndDel = RedisScript.of(
                "local v = redis.call('GET', KEYS[1]); " +
                        "if v then redis.call('DEL', KEYS[1]); end; " +
                        "return v;",
                String.class
        );

        return redis.execute(getAndDel, List.of(key))
                .next() // script 결과 1개
                .flatMap(userIdStr -> {
                    if (userIdStr == null || userIdStr.isBlank()) {
                        return Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "token not found or expired"));
                    }
                    try {
                        return Mono.just(Long.parseLong(userIdStr));
                    } catch (NumberFormatException e) {
                        return Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "invalid token payload"));
                    }
                });
    }

    /**
     * 토큰 발급 결과 DTO.
     * - rawToken: 이메일 링크에 담길 원문 토큰
     * - tokenHash: Redis 저장에 사용된 해시(디버깅/추적용; 외부 노출 금지)
     * - verifyUrl: 사용자에게 전달할 최종 인증 링크
     */
    public record IssuedToken(String rawToken, String tokenHash, String verifyUrl) {}
}
