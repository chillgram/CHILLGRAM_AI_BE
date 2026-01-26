package com.example.chillgram.domain.qa.router;

import com.example.chillgram.domain.qa.handler.QaHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class QaRouter {

    @Bean
    public RouterFunction<ServerResponse> qaRoutes(QaHandler qaHandler) {
        return RouterFunctions.route()
                .path("/api/v1/qs", builder -> builder
                        .nest(accept(MediaType.MULTIPART_FORM_DATA), routeBuilder -> routeBuilder
                                .route(POST("/questions"), qaHandler::createQuestion)))
                .build();
    }
}
