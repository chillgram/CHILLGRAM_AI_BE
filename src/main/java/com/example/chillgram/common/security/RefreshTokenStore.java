package com.example.chillgram.common.security;

import com.example.chillgram.domain.auth.constant.AuthConst;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Refresh 토큰 저장소(Redis)
 * - 저장 위치: Redis
 * - 키 규칙: rt:{userId}
 * - 저장 값: refresh 토큰 원문이 아닌 SHA-256 해시
 * - TTL: security.jwt.refresh-ttl-seconds (JwtProperties)
 */
@Component
public class RefreshTokenStore {

    private final ReactiveStringRedisTemplate redis;
    private final JwtProperties props;

    public RefreshTokenStore(ReactiveStringRedisTemplate redis, JwtProperties props) {
        this.redis = redis;
        this.props = props;
    }

    private String key(long userId) {
        return "rt:%d".formatted(userId);
    }

    /**
     * refresh 저장
     * - 입력: userId, refresh 토큰 원문
     * - 처리: SHA-256 해시로 변환 후 Redis에 저장(TTL 적용)
     */
    public Mono<Void> saveHashed(long userId, String refreshTokenRaw) {
        String hash = AuthConst.sha256Hex(refreshTokenRaw);
        return redis.opsForValue()
                .set(key(userId), hash, Duration.ofSeconds(props.refreshTtlSeconds()))
                .then();
    }

    // refresh 일치 여부 확인
    public Mono<Boolean> matches(long userId, String refreshTokenRaw) {
        String hash = AuthConst.sha256Hex(refreshTokenRaw);
        return redis.opsForValue()
                .get(key(userId))
                .map(saved -> saved.equals(hash))
                .defaultIfEmpty(false);
    }

    // refresh 삭제(로그아웃/강제 만료)
    public Mono<Void> delete(long userId) {
        return redis.delete(key(userId)).then();
    }
}