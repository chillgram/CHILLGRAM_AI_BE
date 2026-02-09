package com.example.chillgram.domain.social.dto;

public record SnsAccountDto(
        boolean connected,
        SnsPlatform platform,
        String accountLabel
) {
    public static SnsAccountDto connected(SnsPlatform platform, String accountLabel) {
        return new SnsAccountDto(true, platform, accountLabel);
    }

    public static SnsAccountDto disconnected(SnsPlatform platform) {
        return new SnsAccountDto(false, platform, null);
    }

    public static SnsAccountDto youtube(String accountLabel) { return connected(SnsPlatform.YOUTUBE, accountLabel); }
}