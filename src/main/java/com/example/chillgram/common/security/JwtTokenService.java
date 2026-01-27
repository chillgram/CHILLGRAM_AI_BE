package com.example.chillgram.common.security;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 토큰 서비스
 * - 기능: access/refresh 발급, 서명/만료/형식 검증, 클레임 추출
 * - 알고리즘: HS256(HMAC)
 * - 키 제약: secret 최소 32바이트
 * - 토큰 구분: typ 클레임(access | refresh)
 * - 클레임: uid(long), role(string), typ(string)
 * - 실패 처리: 검증 실패는 UNAUTHORIZED로 통일 (원인 과다 노출 방지)
 */
public final class JwtTokenService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenService(JwtProperties props) {
        this.props = props;

        // secret 설정 (HS256 최소 32바이트)
        if (props.secret() == null || props.secret().isBlank() || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes");
        }

        // HMAC 서명 키 생성
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    // access 토큰 생성
    public String createAccessToken(long userId, String role) {
        return createToken(userId, role, "access", props.accessTtlSeconds());
    }

    // refresh 토큰 생성
    public String createRefreshToken(long userId, String role) {
        return createToken(userId, role, "refresh", props.refreshTtlSeconds());
    }

    // 토큰 공통 생성
    private String createToken(long userId, String role, String typ, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .claims(Map.of(
                        "uid", userId,
                        "role", role,
                        "typ", typ
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // 토큰 검증 + 파싱
    public Jws<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "JWT 검증 실패: 토큰이 비어있음");
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "JWT 검증 실패: 만료됨");
        } catch (JwtException e) {
            throw ApiException.of(ErrorCode.UNAUTHORIZED, "JWT 검증 실패: 유효하지 않음");
        }
    }

    // uid 클레임 추출
    public long getUserId(Jws<Claims> jws) {
        Object uid = jws.getPayload().get("uid");
        if (uid instanceof Integer i) return i.longValue();
        if (uid instanceof Long l) return l;
        if (uid instanceof String s) return Long.parseLong(s);
        throw ApiException.of(ErrorCode.UNAUTHORIZED, "JWT 클레임 오류: uid 형식이 올바르지 않음");
    }

    // role 클레임 추출
    public String getRole(Jws<Claims> jws) {
        Object role = jws.getPayload().get("role");
        return (role == null) ? "USER" : String.valueOf(role);
    }

    // typ 클레임 추출
    public String getType(Jws<Claims> jws) {
        Object typ = jws.getPayload().get("typ");
        return (typ == null) ? "" : String.valueOf(typ);
    }
}