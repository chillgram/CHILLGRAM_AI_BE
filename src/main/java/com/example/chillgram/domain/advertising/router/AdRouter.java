package com.example.chillgram.domain.advertising.router;

import com.example.chillgram.common.google.FileStorage;
import com.example.chillgram.domain.advertising.handler.AdHandler;
import com.example.chillgram.domain.advertising.service.AdService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
@Configuration
public class AdRouter {

    private final AdService adService;
    private final ObjectMapper objectMapper;
    private final FileStorage fileStorage;

    public AdRouter(AdService adService, ObjectMapper objectMapper, FileStorage fileStorage) {
        this.adService = adService;
        this.objectMapper = objectMapper;
        this.fileStorage = fileStorage;
    }

    @Bean
    public RouterFunction<ServerResponse> adRoutes(AdHandler adHandler) {
        return route()
                .path("/api/advertising", builder -> builder
                        .route(POST("/{id}/ad-trends"), adHandler::getAdTrends)
                        .route(POST("/{id}/ad-guides"), adHandler::createAdGuides)
                        .route(POST("/{id}/ad-copies"), adHandler::createAdCopies)
                        .route(POST("/{id}/ads"), adHandler::createAdProjectAndContents)
                )
                .build();
    }
}