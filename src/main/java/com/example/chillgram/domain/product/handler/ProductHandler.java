package com.example.chillgram.domain.product.handler;

import com.example.chillgram.domain.product.dto.ProductCreateRequest;
import com.example.chillgram.domain.product.dto.ProductUpdateRequest;
import com.example.chillgram.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductHandler {

    private final ProductService productService;

    /**
     * [GET] /api/products/stats
     * 대시보드 통계 조회
     *
     * [Flow]
     * 1. Request: None
     * 2. Service: getDashboardStats() 호출
     * - Repository에서 전체/활성/비활성 카운트 조회 (Parallel)
     * 3. Response: ProductDashboardStats (200 OK)
     */
    public Mono<ServerResponse> getDashboardStats(ServerRequest request) {
        return productService.getDashboardStats()
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    /**
     * [GET] /api/products
     * 제품 목록 조회 (검색, 페이징)
     *
     * [Flow]
     * 1. Request Params: page, size, search
     * 2. Validation: page >= 0, size > 0
     * 3. Service: getProductList() 호출
     * - 검색어 유무에 따라 Repository 조회 분기
     * 4. Response: Page<ProductResponse> (200 OK)
     */
    public Mono<ServerResponse> getProductList(ServerRequest request) {
        // 1. Extract & Validate Params
        int page = request.queryParam("page").map(Integer::parseInt).orElse(0);
        int size = request.queryParam("size").map(Integer::parseInt).orElse(10);
        String search = request.queryParam("search").orElse("");

        if (page < 0) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Page must be >= 0"));
        }
        if (size <= 0) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Size must be > 0"));
        }

        // 2. Call Service
        Pageable pageable = PageRequest.of(page, size);
        return productService.getProductList(search, pageable)
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    /**
     * [GET] /api/products/{id}
     * 제품 상세 조회
     *
     * [Flow]
     * 1. Path Variable: id
     * 2. Service: getProductDetail(id) 호출
     * 3. Response:
     * - Success: ProductResponse (200 OK)
     * - Fail: 404 Not Found (if ID not exists)
     */
    public Mono<ServerResponse> getProductDetail(ServerRequest request) {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid product ID"));
        }

        return productService.getProductDetail(id)
                .flatMap(product -> ServerResponse.ok().bodyValue(product))
                .switchIfEmpty(ServerResponse.notFound().build())
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.notFound().build());
    }

    /**
     * [POST] /api/products
     * 제품 등록
     *
     * [Flow]
     * 1. Request Body: ProductCreateRequest (JSON)
     * 2. Extract userId from JWT principal
     * 3. Service: createProduct() 호출 (userId로 companyId 자동 조회)
     * 4. Response: 201 Created + ProductResponse + Location Header
     */
    public Mono<ServerResponse> createProduct(ServerRequest request) {
        // JWT에서 userId 추출
        Mono<Long> userIdMono = request.principal()
                .map(principal -> {
                    if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
                        var authPrincipal = (com.example.chillgram.common.security.AuthPrincipal) auth.getPrincipal();
                        return authPrincipal.userId();
                    }
                    throw new IllegalStateException("인증 정보가 올바르지 않습니다.");
                });

        return userIdMono
                .flatMap(userId -> request.bodyToMono(ProductCreateRequest.class)
                        .flatMap(createRequest -> productService.createProduct(createRequest, userId)))
                .flatMap(product -> ServerResponse
                        .created(URI.create("/api/products/" + product.getId()))
                        .bodyValue(product))
                .onErrorResume(e -> {
                    log.error("Failed to create product", e);
                    return ServerResponse.badRequest()
                            .bodyValue(Map.of("error", e.getMessage()));
                });
    }

    /**
     * [PUT] /api/products/{id}
     * 제품 수정
     *
     * [Flow]
     * 1. Path Variable: id
     * 2. Request Body: ProductUpdateRequest (JSON)
     * 3. Service: updateProduct(id, request) 호출
     * 4. Response:
     * - Success: 200 OK + ProductResponse
     * - Fail: 404 Not Found (if ID not exists)
     */
    public Mono<ServerResponse> updateProduct(ServerRequest request) {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid product ID"));
        }

        return request.bodyToMono(ProductUpdateRequest.class)
                .flatMap(updateRequest -> productService.updateProduct(id, updateRequest))
                .flatMap(product -> ServerResponse.ok().bodyValue(product))
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.notFound().build());
    }

    /**
     * [DELETE] /api/products/{id}
     * 제품 삭제
     *
     * [Flow]
     * 1. Path Variable: id
     * 2. Service: deleteProduct(id) 호출
     * 3. Response:
     * - Success: 204 No Content
     * - Fail: 404 Not Found (if ID not exists)
     */
    public Mono<ServerResponse> deleteProduct(ServerRequest request) {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid product ID"));
        }

        return productService.deleteProduct(id)
                .then(ServerResponse.noContent().build())
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.notFound().build());
    }
}
