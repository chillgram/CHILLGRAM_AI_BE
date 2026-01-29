package com.example.chillgram.domain.qa.router;

import com.example.chillgram.domain.qa.dto.QaAnswerCreateRequest;
import com.example.chillgram.domain.qa.handler.QaHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

/**
 * Q&A API 라우터
 */
@Configuration
public class QaRouter {

        @Bean
        @RouterOperations({
                        // 1. 질문 생성 (Multipart)
                        @RouterOperation(path = "/api/qs/questions", method = RequestMethod.POST, beanClass = QaHandler.class, beanMethod = "createQuestion", operation = @Operation(summary = "질문 등록", description = "질문 내용(JSON)과 이미지 파일(Multipart)을 업로드하여 질문을 생성합니다.", tags = "QA", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(type = "object", description = "질문 데이터(title, content, category, companyId, createdBy)와 이미지 파일(file)을 포함해야 합니다."))))),

                        // 2. 질문 목록 조회
                        @RouterOperation(path = "/api/qs/questions", method = RequestMethod.GET, beanClass = QaHandler.class, beanMethod = "getQuestionList", operation = @Operation(summary = "질문 목록 조회", description = "질문 목록을 검색조건(keyword)과 페이징하여 조회합니다.", tags = "QA", parameters = {
                                        @Parameter(name = "page", description = "페이지 번호 (0..)", in = ParameterIn.QUERY),
                                        @Parameter(name = "size", description = "페이지 크기 (10..)", in = ParameterIn.QUERY),
                                        @Parameter(name = "keyword", description = "검색 키워드 (제목/내용)", in = ParameterIn.QUERY)
                        })),

                        // 3. 질문 상세 조회
                        @RouterOperation(path = "/api/qs/questions/{id}", method = RequestMethod.GET, beanClass = QaHandler.class, beanMethod = "getQuestionDetail", operation = @Operation(summary = "질문 상세 조회", description = "질문 ID로 상세 정보를 조회합니다.", tags = "QA", parameters = {
                                        @Parameter(name = "id", description = "질문 ID", in = ParameterIn.PATH)
                        })),

                        // 4. 답변 등록
                        @RouterOperation(path = "/api/qs/questions/{questionId}/answers", method = RequestMethod.POST, beanClass = QaHandler.class, beanMethod = "createAnswer", operation = @Operation(summary = "답변 등록", description = "질문에 대한 답변을 등록합니다.", tags = "QA", parameters = {
                                        @Parameter(name = "questionId", description = "질문 ID", in = ParameterIn.PATH)
                        }, requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = QaAnswerCreateRequest.class)))))
        })
        public RouterFunction<ServerResponse> qaRoutes(QaHandler qaHandler) {
                return RouterFunctions.route()
                                .path("/api/qs", builder -> builder

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
