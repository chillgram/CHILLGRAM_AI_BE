package com.example.chillgram.domain.advertising.router;

import com.example.chillgram.domain.ai.handler.JobHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class JobRouter {

    @Bean
    RouterFunction<ServerResponse> jobAPIRoutes(JobHandler h) {
        return RouterFunctions.route()
                .path("/api/jobs", b -> b
                        .GET("/{jobId}", h::getJob)
                        .POST("/basic-images", h::createBasicImagesJob)
                        .GET("/basic-images/{jobId}", h::getBasicImagesResult)
                        .POST("/{jobId}/result", h::postResult)
                )
                .build();
    }
}