package com.example.chillgram.domain.ai.router;

import com.example.chillgram.domain.ai.handler.AdCopyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class AdCopyRouter {

    @Bean
    public RouterFunction<ServerResponse> adCopyRoutes(AdCopyHandler handler) {
        return RouterFunctions.route()
                // 1단계: 가이드라인 5개 생성
                .POST("/api/v1/generate-guidelines",
                        accept(MediaType.APPLICATION_JSON),
                        handler::generateGuidelines)

                // 2단계: 가이드라인 선택 기반 최종 카피/프롬프트 생성
                .POST("/api/v1/generate-copy",
                        accept(MediaType.APPLICATION_JSON),
                        handler::generateFinalCopy)

                .GET("/api/health",
                        handler::health)
                .build();
    }
}
