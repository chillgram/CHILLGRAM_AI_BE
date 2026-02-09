package com.example.chillgram.domain.social.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocialProperties.class)
public class SocialModuleConfig {}