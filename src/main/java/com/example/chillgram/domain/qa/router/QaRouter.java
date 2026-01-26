package com.example.chillgram.domain.qa.router;

import com.example.chillgram.domain.qa.handler.QaHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@Configuration
public class QaRouter {

        @Bean
        public RouterFunction<ServerResponse> qaRoutes(QaHandler qaHandler) {
                return RouterFunctions.route()
                                .path("/api/v1/qs", builder -> builder
                                                // 질문 작성 (multipart)
                                                .nest(accept(MediaType.MULTIPART_FORM_DATA),
                                                                routeBuilder -> routeBuilder
                                                                                .route(POST("/questions"),
                                                                                                qaHandler::createQuestion))
                                                // 목록 조회
                                                .route(GET("/questions"), qaHandler::getQuestionList)
                                                // 상세 조회
                                                .route(GET("/questions/{id}"), qaHandler::getQuestionDetail)
                                                // 답변 작성 (JSON)
                                                .nest(accept(MediaType.APPLICATION_JSON),
                                                                routeBuilder -> routeBuilder
                                                                                .route(POST("/questions/{questionId}/answers"),
                                                                                                qaHandler::createAnswer)))
                                .build();
        }
}
