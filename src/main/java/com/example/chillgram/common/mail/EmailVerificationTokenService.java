package com.example.chillgram.common.mail;

import com.example.chillgram.domain.auth.constant.AuthConst;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        // 이메일로 전달될 원문 토큰
        String raw = UUID.randomUUID() + "-" + UUID.randomUUID();
        // Redis에는 원문이 아닌 해시를 키로 사용(
        String hash = AuthConst.sha256HexSafe(raw);
        // 링크 안전성을 위해 URL 인코딩 후 baseUrl 뒤에 붙인다.
        String verifyUrl = verifyBaseUrl + URLEncoder.encode(raw, StandardCharsets.UTF_8);

        // Redis: key(hash) -> value(userId), TTL 적용
        return redis.opsForValue()
                .set(AuthConst.tokenKey(hash), String.valueOf(userId), AuthConst.EMAIL_TOKEN_TTL)
                .thenReturn(new IssuedToken(raw, hash, verifyUrl));
    }

    /**
     * 이메일 인증 토큰 1회성 소비.
     * - 인증 링크 재사용 방지(Replay Attack 방어)
     * - GET 요청이 들어오면 토큰을 조회하고 즉시 삭제
     * 흐름:
     * - raw token을 해시로 변환하여 Redis key를 만든다.
     * - Redis에서 userId를 조회한다.
     * - 조회 성공 시 즉시 삭제(delete) 후 userId를 반환한다.
     * - 없으면(만료/위조/이미 사용) 에러로 처리한다.

     * @param rawToken 이메일 링크로 전달된 토큰 원문
     * @return 토큰에 매핑된 userId
     */
    public Mono<Long> consume(String rawToken) {
        String hash = AuthConst.sha256HexSafe(rawToken);
        String key = AuthConst.tokenKey(hash);

        return redis.opsForValue()
                .get(key)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("토큰이 없거나 만료되었습니다.")))
                // 조회 성공 시 즉시 삭제 후 userId 반환(1회성)
                .flatMap(userIdStr ->
                        redis.delete(key).thenReturn(Long.parseLong(userIdStr))
                );
    }

    /**
     * 토큰 발급 결과 DTO.
     * - rawToken: 이메일 링크에 담길 원문 토큰
     * - tokenHash: Redis 저장에 사용된 해시(디버깅/추적용; 외부 노출 금지)
     * - verifyUrl: 사용자에게 전달할 최종 인증 링크
     */
    public record IssuedToken(String rawToken, String tokenHash, String verifyUrl) {}
}
