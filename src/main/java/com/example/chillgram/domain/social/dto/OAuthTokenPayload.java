package com.example.chillgram.domain.social.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public record OAuthTokenPayload(
        String accessToken,
        String refreshToken,
        long expiresAtEpochSecond
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Instant expiresAt() {
        return Instant.ofEpochSecond(expiresAtEpochSecond);
    }

    public byte[] toJsonBytes() {
        try {
            return MAPPER.writeValueAsBytes(this);
        } catch (Exception e) {
            throw new IllegalStateException("token serialize failed", e);
        }
    }

    public static OAuthTokenPayload fromJsonBytes(byte[] bytes) {
        try {
            return MAPPER.readValue(bytes, OAuthTokenPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("token deserialize failed", e);
        }
    }
}
