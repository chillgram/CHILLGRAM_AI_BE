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

/**
 * Q&A API 라우터
 * 
 * [요청 처리 흐름]
 * 1. 사용자가 API 요청을 보냄 (예: GET /api/v1/qs/questions)
 * 2. Spring WebFlux가 이 Router 설정을 확인
 * 3. URL과 메서드가 일치하면 해당 Handler 메서드로 연결 (Routing)
 * 4. Handler가 실제 비즈니스 로직 처리 후 응답 반환
 * 
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ API 엔드포인트 목록 │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │ POST /api/v1/qs/questions → 질문 작성 │
 * │ GET /api/v1/qs/questions → 목록 조회 │
 * │ GET /api/v1/qs/questions/{id} → 상세 조회 │
 * │ POST /api/v1/qs/questions/{questionId}/answers → 답변 작성 │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class QaRouter {

        @Bean
        public RouterFunction<ServerResponse> qaRoutes(QaHandler qaHandler) {
                return RouterFunctions.route()
                                .path("/api/v1/qs", builder -> builder

                                                // 1. 질문 작성 (POST /questions) - Multipart
                                                .nest(accept(MediaType.MULTIPART_FORM_DATA),
                                                                routeBuilder -> routeBuilder.route(POST("/questions"),
                                                                                qaHandler::createQuestion))

                                                // 2. 질문 목록 조회 (GET /questions) - Paging & Search
                                                .route(GET("/questions"), qaHandler::getQuestionList)

                                                // 3. 질문 상세 조회 (GET /questions/{id})
                                                .route(GET("/questions/{id}"), qaHandler::getQuestionDetail)

                                                // 4. 답변 작성 (POST /questions/{questionId}/answers)
                                                .nest(accept(MediaType.APPLICATION_JSON),
                                                                routeBuilder -> routeBuilder.route(
                                                                                POST("/questions/{questionId}/answers"),
                                                                                qaHandler::createAnswer)))
                                .build();
        }
}
