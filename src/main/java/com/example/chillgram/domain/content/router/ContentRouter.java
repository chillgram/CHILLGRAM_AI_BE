package com.example.chillgram.domain.content.router;

import com.example.chillgram.domain.content.dto.ContentAssetResponse;
import com.example.chillgram.domain.content.dto.ContentResponse;
import com.example.chillgram.domain.content.dto.ContentUpdateRequest;
import com.example.chillgram.domain.content.handler.ContentHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class ContentRouter {

        @Bean
        @RouterOperations({
                        @RouterOperation(path = "/api/projects/{projectId}/contents", method = RequestMethod.GET, beanClass = ContentHandler.class, beanMethod = "getContentsByProject", operation = @Operation(summary = "프로젝트별 콘텐츠 목록 조회", description = "프로젝트에 속한 콘텐츠 목록을 조회합니다.", tags = "Content", parameters = @Parameter(name = "projectId", description = "프로젝트 ID", in = ParameterIn.PATH, required = true), responses = @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContentResponse.class)))))),
                        @RouterOperation(path = "/api/contents/{contentId}", method = RequestMethod.GET, beanClass = ContentHandler.class, beanMethod = "getContentById", operation = @Operation(summary = "콘텐츠 상세 조회", description = "콘텐츠 ID로 상세 정보를 조회합니다.", tags = "Content", parameters = @Parameter(name = "contentId", description = "콘텐츠 ID", in = ParameterIn.PATH, required = true), responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ContentResponse.class))))),
                        @RouterOperation(path = "/api/contents/{contentId}", method = RequestMethod.PUT, beanClass = ContentHandler.class, beanMethod = "updateContent", operation = @Operation(summary = "콘텐츠 수정", description = "콘텐츠의 제목, 본문, 상태, 태그, 플랫폼을 수정합니다. null인 필드는 기존 값 유지.", tags = "Content", parameters = @Parameter(name = "contentId", description = "콘텐츠 ID", in = ParameterIn.PATH, required = true), requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = ContentUpdateRequest.class))), responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ContentResponse.class))))),
                        @RouterOperation(path = "/api/contents/{contentId}/assets", method = RequestMethod.GET, beanClass = ContentHandler.class, beanMethod = "getAssetsByContent", operation = @Operation(summary = "콘텐츠 에셋 목록 조회", description = "콘텐츠에 속한 에셋(이미지, 영상 등) 목록을 조회합니다.", tags = "ContentAsset", parameters = @Parameter(name = "contentId", description = "콘텐츠 ID", in = ParameterIn.PATH, required = true), responses = @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContentAssetResponse.class))))))
        })
        public RouterFunction<ServerResponse> contentRoutes(ContentHandler contentHandler) {
                return RouterFunctions.route()
                                // 프로젝트별 콘텐츠 목록
                                .route(GET("/api/projects/{projectId}/contents"), contentHandler::getContentsByProject)

                                // 콘텐츠 조회/수정
                                .route(GET("/api/contents/{contentId}"), contentHandler::getContentById)
                                .route(PUT("/api/contents/{contentId}").and(accept(MediaType.APPLICATION_JSON)),
                                                contentHandler::updateContent)

                                // 에셋 조회
                                .route(GET("/api/contents/{contentId}/assets"), contentHandler::getAssetsByContent)

                                .build();
        }
}
