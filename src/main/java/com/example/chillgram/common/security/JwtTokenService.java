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

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYP = "typ";
    private static final String CLAIM_CID = "cid";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenService(JwtProperties props) {
        this.props = props;

        if (props.secret() == null || props.secret().isBlank()
                || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes");
        }

        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    // access 토큰 생성
    public String createAccessToken(long userId, long companyId, String role) {
        return createToken(userId, companyId, role, "access", props.accessTtlSeconds());
    }

    // refresh 토큰 생성
    public String createRefreshToken(long userId, long companyId, String role) {
        return createToken(userId, companyId, role, "refresh", props.refreshTtlSeconds());
    }

    private String createToken(long userId, long companyId, String role, String typ, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .claims(Map.of(
                        CLAIM_UID, userId,
                        CLAIM_CID, companyId,
                        CLAIM_ROLE, role,
                        CLAIM_TYP, typ
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

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

    public long getUserId(Jws<Claims> jws) {
        return toLongClaim(jws.getPayload().get(CLAIM_UID), "uid");
    }

    public long getCompanyId(Jws<Claims> jws) {
        return toLongClaim(jws.getPayload().get(CLAIM_CID), "cid");
    }

    public String getRole(Jws<Claims> jws) {
        Object role = jws.getPayload().get(CLAIM_ROLE);
        return (role == null) ? "USER" : String.valueOf(role);
    }

    public String getType(Jws<Claims> jws) {
        Object typ = jws.getPayload().get(CLAIM_TYP);
        return (typ == null) ? "" : String.valueOf(typ);
    }

    private long toLongClaim(Object v, String name) {
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Long l) return l;
        if (v instanceof String s) return Long.parseLong(s);
        throw ApiException.of(ErrorCode.UNAUTHORIZED, "JWT 클레임 오류: " + name + " 형식이 올바르지 않음");
    }
}
