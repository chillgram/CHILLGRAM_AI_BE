package com.example.chillgram.domain.auth.constant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

public final class AuthConst {
    private AuthConst() {}

    // app_user.status
    public static final int STATUS_UNVERIFIED = 0; // 미인증
    public static final int STATUS_VERIFIED   = 1; // 인증
    public static final int STATUS_DORMANT    = 2; // 휴면
    public static final int STATUS_DELETED    = 3; // 탈퇴

    // Redis 토큰 TTL
    public static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    // token 원문을 저장하지 않고 해시로만 저장
    public static String sha256Hex(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA- Compressor");
            return "";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String tokenKey(String tokenHash) {
        return "email_verify:" + tokenHash;
    }

    public static String sha256HexSafe(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}