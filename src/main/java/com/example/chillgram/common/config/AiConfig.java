package com.example.chillgram.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.ai.enabled", havingValue = "TRUE")
public class AiConfig {

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            @Value("${spring.ai.google.genai.api-key:}") String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank() || "API_KEY".equals(apiKey)) {
            throw new IllegalStateException("GenAI API Key가 설정되지 않았습니다.");
        }
        return builder.build();
    }
}