package com.example.chillgram.domain.social.config;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

@Configuration
public class GcpSecretConfig {
    @Bean(destroyMethod = "close") // 앱 종료 시에만 닫음
    public SecretManagerServiceClient secretManagerServiceClient() throws IOException {
        return SecretManagerServiceClient.create();
    }
}