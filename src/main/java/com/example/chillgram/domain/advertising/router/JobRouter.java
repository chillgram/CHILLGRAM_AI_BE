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
                .path("/api", b -> b
                        .POST("/projects/{projectId}/jobs", h::createJob)
                        .GET("/jobs/{jobId}", h::getJob)
                        .POST("/jobs/{jobId}/result", h::postResult)
                )
                .build();
    }
}