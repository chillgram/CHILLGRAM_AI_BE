package com.example.chillgram.domain.project.router;

import com.example.chillgram.domain.project.handler.ProjectHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
@Tag(name = "Project", description = "프로젝트 관리 API")
public class ProjectRouter {

        @Bean
        @RouterOperations({
                        @RouterOperation(path = "/api/products/{id}/projects", method = RequestMethod.GET, beanClass = ProjectHandler.class, beanMethod = "getProjectsByProduct", operation = @Operation(operationId = "getProjectsByProduct", summary = "제품의 프로젝트 목록 조회", description = "특정 제품에 속한 모든 프로젝트 목록을 조회합니다. 각 프로젝트에는 콘텐츠 개수가 포함됩니다.", tags = {
                                        "Project" }, parameters = {
                                                        @Parameter(name = "id", description = "제품 ID", required = true, in = ParameterIn.PATH)
                                        }, responses = {
                                                        @ApiResponse(responseCode = "200", description = "성공"),
                                                        @ApiResponse(responseCode = "404", description = "제품을 찾을 수 없음")
                                        })),
                        @RouterOperation(path = "/api/products/{id}/projects", method = RequestMethod.POST, beanClass = ProjectHandler.class, beanMethod = "createProject", operation = @Operation(operationId = "createProject", summary = "프로젝트 생성", description = "특정 제품에 새로운 프로젝트(광고 설정)를 생성합니다.", tags = {
                                        "Project" }, parameters = {
                                                        @Parameter(name = "id", description = "제품 ID", required = true, in = ParameterIn.PATH)
                                        }, responses = {
                                                        @ApiResponse(responseCode = "201", description = "프로젝트 생성 성공"),
                                                        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
                                                        @ApiResponse(responseCode = "401", description = "인증 필요"),
                                                        @ApiResponse(responseCode = "403", description = "회사 정보 없음")
                                        }))
        })
        public RouterFunction<ServerResponse> projectRoutes(ProjectHandler projectHandler) {
                return RouterFunctions.route()
                                .path("/api/products", builder -> builder
                                                .nest(accept(MediaType.APPLICATION_JSON), products -> products
                                                                .GET("/{id}/projects",
                                                                                projectHandler::getProjectsByProduct)
                                                                .POST("/{id}/projects", projectHandler::createProject)))
                                .build();
        }
}
