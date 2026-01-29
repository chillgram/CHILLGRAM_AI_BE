package com.example.chillgram.domain.product.service;

import com.example.chillgram.domain.product.dto.ProductCreateRequest;
import com.example.chillgram.domain.product.dto.ProductDashboardStats;
import com.example.chillgram.domain.product.dto.ProductResponse;
import com.example.chillgram.domain.product.dto.ProductUpdateRequest;
import com.example.chillgram.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

        private final ProductRepository productRepository;

        /**
         * 전체 제품 수 및 상태별 통계 조회 (대시보드용)
         */
        public Mono<ProductDashboardStats> getDashboardStats() {
                // TODO: SecurityContext에서 companyId 추출 (현재는 임시로 1L 고정)
                Long companyId = 1L;

                return Mono.zip(
                                productRepository.countByCompanyId(companyId),
                                productRepository.countByCompanyIdAndIsActiveTrue(companyId),
                                productRepository.countByCompanyIdAndIsActiveFalse(companyId)).map(
                                                tuple -> ProductDashboardStats.builder()
                                                                .totalCount(tuple.getT1())
                                                                .activeCount(tuple.getT2())
                                                                .inactiveCount(tuple.getT3())
                                                                .build());
        }

        /**
         * 제품 목록 조회 (키워드 검색 및 페이징)
         */
        public Mono<Page<ProductResponse>> getProductList(String search, Pageable pageable) {
                // TODO: SecurityContext에서 companyId 추출 (현재는 임시로 1L 고정)
                Long companyId = 1L;

                if (search == null || search.isBlank()) {
                        return productRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                                        .map(ProductResponse::from)
                                        .collectList()
                                        .zipWith(productRepository.countByCompanyId(companyId))
                                        .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
                }

                return productRepository
                                .findByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(companyId,
                                                search, search,
                                                pageable)
                                .map(ProductResponse::from)
                                .collectList()
                                .zipWith(productRepository
                                                .countByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                                                companyId, search,
                                                                search))
                                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
        }

        /**
         * 제품 상세 조회
         */
        public Mono<ProductResponse> getProductDetail(Long id) {
                return productRepository.findById(id)
                                .map(ProductResponse::from)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)));
        }

        // ==========================================
        // CUD Operations
        // ==========================================

        /**
         * 제품 등록
         * POST /api/products
         */
        @Transactional
        public Mono<ProductResponse> createProduct(ProductCreateRequest request) {
                // TODO: SecurityContext에서 companyId, createdBy 추출
                Long companyId = 1L;
                String createdBy = "admin";

                return productRepository.save(request.toEntity(companyId, createdBy))
                                .map(ProductResponse::from)
                                .doOnSuccess(product -> log.info("Product created: {}", product.getId()));
        }

        /**
         * 제품 수정
         * PUT /api/products/{id}
         */
        @Transactional
        public Mono<ProductResponse> updateProduct(Long id, ProductUpdateRequest request) {
                return productRepository.findById(id)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)))
                                .map(product -> product.update(request))
                                .flatMap(productRepository::save)
                                .map(ProductResponse::from)
                                .doOnSuccess(product -> log.info("Product updated: {}", product.getId()));
        }

        /**
         * 제품 삭제
         * DELETE /api/products/{id}
         */
        @Transactional
        public Mono<Void> deleteProduct(Long id) {
                return productRepository.findById(id)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)))
                                .flatMap(product -> productRepository.deleteById(id))
                                .doOnSuccess(v -> log.info("Product deleted: {}", id));
        }
}
