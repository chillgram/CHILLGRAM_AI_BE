package com.example.chillgram.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.sendgrid")
public record SendGridProperties(
        String apiKey,
        String fromEmail,
        String fromName
) {}