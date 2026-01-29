package com.example.chillgram.domain.ai.router;

import com.example.chillgram.domain.ai.handler.AdCopyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.example.chillgram.domain.ai.dto.FinalCopyRequest;
import com.example.chillgram.domain.ai.dto.GuidelineRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class AdCopyRouter {

        @Bean
        @RouterOperations({
                        @RouterOperation(path = "/api/ai/generate-guidelines", method = RequestMethod.POST, beanClass = AdCopyHandler.class, beanMethod = "generateGuidelines", operation = @Operation(summary = "1단계: 가이드라인 생성", description = "브랜드명과 키워드를 입력받아 광고 카피 가이드라인 5개를 생성합니다.", tags = "AI AdCopy", requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = GuidelineRequest.class))))),
                        @RouterOperation(path = "/api/ai/generate-copy", method = RequestMethod.POST, beanClass = AdCopyHandler.class, beanMethod = "generateFinalCopy", operation = @Operation(summary = "2단계: 최종 카피 생성", description = "선택한 가이드라인을 기반으로 최종 광고 카피와 이미지 프롬프트를 생성합니다.", tags = "AI AdCopy", requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = FinalCopyRequest.class))))),
                        @RouterOperation(path = "/api/health", method = RequestMethod.GET, beanClass = AdCopyHandler.class, beanMethod = "health", operation = @Operation(summary = "헬스 체크", description = "서버 상태(UP)를 확인합니다.", tags = "Common"))
        })
        public RouterFunction<ServerResponse> adCopyRoutes(AdCopyHandler handler) {
                return RouterFunctions.route()
                                // 1단계: 가이드라인 5개 생성
                                .POST("/api/ai/generate-guidelines",
                                                accept(MediaType.APPLICATION_JSON),
                                                handler::generateGuidelines)

                                // 2단계: 가이드라인 선택 기반 최종 카피/프롬프트 생성
                                .POST("/api/ai/generate-copy",
                                                accept(MediaType.APPLICATION_JSON),
                                                handler::generateFinalCopy)

                                .GET("/api/health",
                                                handler::health)
                                .build();
        }
}
