package com.example.chillgram.domain.product.router;

import com.example.chillgram.domain.product.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class ProductRouter {

    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler productHandler) {
        return RouterFunctions.route()
                .path("/api/v1/products", builder -> builder
                        // ==========================================
                        // [GET] /stats - 대시보드 통계
                        // ------------------------------------------
                        // 설명: 전체 제품 수, 활성/비활성 제품 수 조회
                        // Flow: Router -> Handler.getDashboardStats -> Service
                        // ==========================================
                        .route(GET("/stats"), productHandler::getDashboardStats)

                        // ==========================================
                        // [GET] / - 제품 목록 조회
                        // ------------------------------------------
                        // 파라미터: page(0..), size(10..), search(optional)
                        // 설명: 등록된 제품 목록을 페이징하여 조회
                        // Flow: Router -> Handler.getProductList -> Service
                        // ==========================================
                        .route(GET(""), productHandler::getProductList)

                        // ==========================================
                        // [GET] /{id} - 제품 상세 조회
                        // ------------------------------------------
                        // 파라미터: id (Path Variable)
                        // 설명: 특정 제품의 상세 정보 조회
                        // Flow: Router -> Handler.getProductDetail -> Service
                        // ==========================================
                        .route(GET("/{id}"), productHandler::getProductDetail))
                .build();
    }
}
