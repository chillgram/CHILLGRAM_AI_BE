package com.example.chillgram.domain.social.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "social")
public record SocialProperties(
        OAuth oauth,
        Gcp gcp
) {
    public record OAuth(
            Youtube youtube,
            long stateTtlSeconds
    ) {
        public record Youtube(
                String clientId,
                String clientSecret,
                String redirectUri,
                List<String> scopes
        ) {}
    }

    public record Gcp(String projectId) {}
}