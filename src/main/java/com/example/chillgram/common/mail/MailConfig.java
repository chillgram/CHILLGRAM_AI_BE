package com.example.chillgram.common.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SendGridProperties.class)
public class MailConfig {
}