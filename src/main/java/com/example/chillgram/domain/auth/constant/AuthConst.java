package com.example.chillgram.domain.auth.constant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public final class AuthConst {
    private AuthConst() {
    }

    // app_user.status
    public static final int STATUS_UNVERIFIED = 0; // 미인증
    public static final int STATUS_VERIFIED = 1; // 인증
    public static final int STATUS_DORMANT = 2; // 휴면
    public static final int STATUS_DELETED = 3; // 탈퇴

    // Redis 토큰 TTL
    public static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);

    public static final String EMAIL_VERIFY_PREFIX = "email_verify:";

    public static String normalizeEmail(String email) {
        return (email == null) ? null : email.trim().toLowerCase();
    }

    /**
     * 토큰 원문을 저장하지 않고 해시로만 저장하기 위한 SHA-256 hex.
     * - 입력이 null/blank면 INVALID_REQUEST로 막는 게 맞지만, 상수 클래스에서는 예외 타입을 섞지 않는다.
     * - 호출부에서 null/blank 검증 후 호출
     */
    public static String sha256Hex(String raw) {
        if (raw == null) throw new IllegalArgumentException("raw is null");

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // JVM 표준 알고리즘이라 사실상 발생하면 런타임 환경 자체가 비정상
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String emailVerifyKeyByRawToken(String rawToken) {
        return EMAIL_VERIFY_PREFIX + sha256Hex(rawToken);
    }

    public static String emailVerifyKeyByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash is blank");
        }
        return EMAIL_VERIFY_PREFIX + tokenHash;
    }
}