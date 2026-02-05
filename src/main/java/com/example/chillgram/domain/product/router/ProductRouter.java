package com.example.chillgram.domain.product.router;

import com.example.chillgram.domain.advertising.handler.AdHandler;
import com.example.chillgram.domain.product.dto.ProductCreateRequest;
import com.example.chillgram.domain.product.dto.ProductUpdateRequest;
import com.example.chillgram.domain.product.handler.ProductHandler;
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

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class ProductRouter {

    @Bean
    @RouterOperations({
            // ==========================================
            // READ Operations
            // ==========================================
            @RouterOperation(path = "/api/products/stats", method = RequestMethod.GET, beanClass = ProductHandler.class, beanMethod = "getDashboardStats", operation = @Operation(summary = "대시보드 통계 조회", description = "전체 제품 수, 활성/비활성 제품 수를 조회합니다.", tags = "Product")),
            @RouterOperation(path = "/api/products", method = RequestMethod.GET, beanClass = ProductHandler.class, beanMethod = "getProductList", operation = @Operation(summary = "제품 목록 조회", description = "등록된 제품 목록을 페이징하여 조회합니다. 검색어로 필터링 가능합니다.", tags = "Product", parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지 크기 (기본값: 10)", in = ParameterIn.QUERY),
                    @Parameter(name = "search", description = "검색어 (제품명/설명)", in = ParameterIn.QUERY)
            })),
            @RouterOperation(path = "/api/products/{id}", method = RequestMethod.GET, beanClass = ProductHandler.class, beanMethod = "getProductDetail", operation = @Operation(summary = "제품 상세 조회", description = "제품 ID로 상세 정보를 조회합니다.", tags = "Product", parameters = @Parameter(name = "id", description = "제품 ID", in = ParameterIn.PATH))),
            // ==========================================
            // CUD Operations
            // ==========================================
            @RouterOperation(path = "/api/products", method = RequestMethod.POST, beanClass = ProductHandler.class, beanMethod = "createProduct", operation = @Operation(summary = "제품 등록", description = "새로운 제품을 등록합니다.", tags = "Product", requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = ProductCreateRequest.class))))),
            @RouterOperation(path = "/api/products/{id}", method = RequestMethod.PUT, beanClass = ProductHandler.class, beanMethod = "updateProduct", operation = @Operation(summary = "제품 수정", description = "기존 제품 정보를 수정합니다. null인 필드는 기존 값을 유지합니다.", tags = "Product", parameters = @Parameter(name = "id", description = "제품 ID", in = ParameterIn.PATH), requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = ProductUpdateRequest.class))))),
            @RouterOperation(path = "/api/products/{id}", method = RequestMethod.DELETE, beanClass = ProductHandler.class, beanMethod = "deleteProduct", operation = @Operation(summary = "제품 삭제", description = "제품을 삭제합니다.", tags = "Product", parameters = @Parameter(name = "id", description = "제품 ID", in = ParameterIn.PATH))),
            @RouterOperation(
                    path = "/api/v1/products/{id}/ad-trends",
                    method = RequestMethod.GET,
                    beanClass = ProductHandler.class,
                    beanMethod = "getAdTrends",
                    operation = @Operation(
                            summary = "제품 광고 트렌드 분석 조회",
                            parameters = {
                                    @Parameter(name = "id", in = ParameterIn.PATH, required = true),
                                    @Parameter(name = "date", in = ParameterIn.QUERY, required = false, example = "2026-02-10")
                            },
                            tags = {"Product"}
                    )
            )
    })
    public RouterFunction<ServerResponse> productRoutes(ProductHandler productHandler, AdHandler adHandler) {
        return RouterFunctions.route()
                .path("/api/products", builder -> builder
                        // ==========================================
                        // READ Operations
                        // ==========================================

                        // [GET] /stats - 대시보드 통계
                        // Flow: Router → Handler.getDashboardStats → Service.getDashboardStats →
                        // Repository.count*
                        // Response: ProductDashboardStats (200 OK)
                        .route(GET("/stats"), productHandler::getDashboardStats)

                        // [GET] / - 제품 목록 조회
                        // Flow: Router → Handler.getProductList → Service.getProductList →
                        // Repository.findAll*
                        // Params: page, size, search
                        // Response: Page<ProductResponse> (200 OK)
                        .route(GET(""), productHandler::getProductList)

                        // [GET] /{id} - 제품 상세 조회
                        // Flow: Router → Handler.getProductDetail → Service.getProductDetail →
                        // Repository.findById
                        // Response: ProductResponse (200 OK) or 404 Not Found
                        .route(GET("/{id}"), productHandler::getProductDetail)

                        // ==========================================
                        // CUD Operations
                        // ==========================================

                        // [POST] / - 제품 등록
                        // Flow: Router → Handler.createProduct → Service.createProduct →
                        // Repository.save
                        // Request: ProductCreateRequest (JSON)
                        // Response: ProductResponse (201 Created + Location Header)
                        .route(POST("").and(accept(MediaType.APPLICATION_JSON)),
                                productHandler::createProduct)

                        // [PUT] /{id} - 제품 수정
                        // Flow: Router → Handler.updateProduct → Service.updateProduct →
                        // Repository.findById → Entity.update → Repository.save
                        // Request: ProductUpdateRequest (JSON)
                        // Response: ProductResponse (200 OK) or 404 Not Found
                        .route(PUT("/{id}").and(accept(MediaType.APPLICATION_JSON)),
                                productHandler::updateProduct)

                        // [DELETE] /{id} - 제품 삭제
                        // Flow: Router → Handler.deleteProduct → Service.deleteProduct →
                        // Repository.findById → Repository.deleteById
                        // Response: 204 No Content or 404 Not Found
                        .route(DELETE("/{id}"), productHandler::deleteProduct)

                        /**
                         * 광고 트렌드 분석 조회
                         * 제품 ID와 선택적 컨텍스트(기준 날짜)를 기반으로광고에 활용할 "가장 가까운 이벤트 5개"를 제공
                         *
                         * POST /api/v1/products/{id}/ad-trends
                         * @return 가까운 이벤트 5개 목록
                         */
                        .route(POST("/{id}/ad-trends"), adHandler::getAdTrends)
                        .route(POST("/{id}/ad-guides"), adHandler::createAdGuides)
                        .route(POST("/{id}/ad-copies"), adHandler::createAdCopies)
                        .route(POST("/{id}/ads"), adHandler::createAdProjectAndContents)
                )
                .build();
    }
}
