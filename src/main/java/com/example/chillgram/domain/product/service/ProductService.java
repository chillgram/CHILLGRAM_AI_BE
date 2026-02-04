package com.example.chillgram.domain.product.service;

import com.example.chillgram.domain.company.domain.Company;
import com.example.chillgram.domain.company.repository.CompanyRepository;
import com.example.chillgram.domain.product.dto.ProductCreateRequest;
import com.example.chillgram.domain.product.dto.ProductDashboardStats;
import com.example.chillgram.domain.product.dto.ProductResponse;
import com.example.chillgram.domain.product.dto.ProductUpdateRequest;
import com.example.chillgram.domain.product.entity.Product;
import com.example.chillgram.domain.product.repository.ProductRepository;
import com.example.chillgram.domain.user.domain.AppUser;
import com.example.chillgram.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

        private final ProductRepository productRepository;
        private final AppUserRepository appUserRepository;
        private final CompanyRepository companyRepository;

        /**
         * 전체 제품 수 및 상태별 통계 조회 (대시보드용)
         */
        public Mono<ProductDashboardStats> getDashboardStats() {
                // TODO: SecurityContext에서 companyId 추출 (현재는 임시로 1L 고정)
                Long companyId = 1L;

                return Mono.zip(
                                productRepository.countByCompanyId(companyId),
                                productRepository.countByCompanyIdAndIsActiveTrue(companyId),
                                productRepository.countByCompanyIdAndIsActiveFalse(companyId))
                                .map(tuple -> ProductDashboardStats.builder()
                                                .totalCount(tuple.getT1())
                                                .activeCount(tuple.getT2())
                                                .inactiveCount(tuple.getT3())
                                                .build());
        }

        /**
         * 제품 목록 조회 (키워드 검색 및 페이징) + 작성자/회사 이름 포함
         */
        public Mono<Page<ProductResponse>> getProductList(String search, Pageable pageable) {
                // TODO: SecurityContext에서 companyId 추출 (현재는 임시로 1L 고정)
                Long companyId = 1L;

                Mono<Long> countMono;
                Flux<Product> productFlux;

                if (search == null || search.isBlank()) {
                        countMono = productRepository.countByCompanyId(companyId);
                        productFlux = productRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
                } else {
                        countMono = productRepository
                                        .countByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                                        companyId, search, search);
                        productFlux = productRepository
                                        .findByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                                        companyId, search, search, pageable);
                }

                return Mono.zip(countMono, productFlux.collectList())
                                .flatMap(tuple -> {
                                        Long total = tuple.getT1();
                                        List<Product> products = tuple.getT2();

                                        if (products.isEmpty()) {
                                                return Mono.just(new PageImpl<>(Collections.emptyList(), pageable,
                                                                total));
                                        }

                                        // Collect IDs for bulk fetch
                                        Set<Long> companyIds = products.stream().map(Product::getCompanyId)
                                                        .filter(id -> id != null)
                                                        .collect(Collectors.toSet());
                                        Set<Long> createdByIds = products.stream().map(Product::getCreatedBy)
                                                        .filter(id -> id != null)
                                                        .collect(Collectors.toSet());

                                        return Mono.zip(
                                                        companyRepository.findAllById(companyIds).collectMap(
                                                                        Company::getCompanyId, Company::getName),
                                                        appUserRepository.findAllById(createdByIds).collectMap(
                                                                        AppUser::getUserId, AppUser::getName))
                                                        .map(namesTuple -> {
                                                                Map<Long, String> companyNames = namesTuple.getT1();
                                                                Map<Long, String> userNames = namesTuple.getT2();

                                                                List<ProductResponse> responses = products.stream()
                                                                                .map(product -> {
                                                                                        ProductResponse res = ProductResponse
                                                                                                        .from(product);
                                                                                        res.setCompanyName(companyNames
                                                                                                        .getOrDefault(product
                                                                                                                        .getCompanyId(),
                                                                                                                        "Unknown"));
                                                                                        res.setCreatedByName(userNames
                                                                                                        .getOrDefault(product
                                                                                                                        .getCreatedBy(),
                                                                                                                        "Unknown"));
                                                                                        return res;
                                                                                }).collect(Collectors.toList());

                                                                return new PageImpl<>(responses, pageable, total);
                                                        });
                                });
        }

        /**
         * 제품 상세 조회 + 작성자/회사 이름 포함
         */
        public Mono<ProductResponse> getProductDetail(Long id) {
                return productRepository.findById(id)
                                .switchIfEmpty(Mono.error(
                                                new IllegalArgumentException("Product not found with id: " + id)))
                                .flatMap(product -> {
                                        return Mono.zip(
                                                        companyRepository.findById(product.getCompanyId())
                                                                        .map(Company::getName)
                                                                        .defaultIfEmpty("Unknown"),
                                                        appUserRepository.findById(product.getCreatedBy())
                                                                        .map(AppUser::getName)
                                                                        .defaultIfEmpty("Unknown"))
                                                        .map(namesTuple -> {
                                                                ProductResponse res = ProductResponse.from(product);
                                                                res.setCompanyName(namesTuple.getT1());
                                                                res.setCreatedByName(namesTuple.getT2());
                                                                return res;
                                                        });
                                });
        }

        // ==========================================
        // CUD Operations
        // ==========================================

        /**
         * 제품 등록
         * POST /api/products
         *
         * @param request 제품 생성 요청 DTO
         * @param userId  로그인한 사용자 ID (JWT에서 추출)
         * @return ProductResponse
         */
        @Transactional
        public Mono<ProductResponse> createProduct(ProductCreateRequest request, Long userId) {
                // 1. userId로 AppUser 조회 → companyId 획득
                return appUserRepository.findById(userId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("사용자를 찾을 수 없습니다. id=" + userId)))
                                .flatMap(user -> {
                                        Long companyId = user.getCompanyId();
                                        Long createdBy = userId;

                                        // 2. Product 저장
                                        return productRepository.save(request.toEntity(companyId, createdBy))
                                                        .flatMap(product -> {
                                                                // 3. 응답에 이름 정보 포함
                                                                return Mono.zip(
                                                                                companyRepository.findById(
                                                                                                product.getCompanyId())
                                                                                                .map(Company::getName)
                                                                                                .defaultIfEmpty("Unknown"),
                                                                                Mono.just(user.getName() != null
                                                                                                ? user.getName()
                                                                                                : "Unknown"))
                                                                                .map(namesTuple -> {
                                                                                        ProductResponse res = ProductResponse
                                                                                                        .from(product);
                                                                                        res.setCompanyName(namesTuple
                                                                                                        .getT1());
                                                                                        res.setCreatedByName(namesTuple
                                                                                                        .getT2());
                                                                                        return res;
                                                                                });
                                                        });
                                })
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
                                .flatMap(savedProduct -> {
                                        return Mono.zip(
                                                        companyRepository.findById(savedProduct.getCompanyId())
                                                                        .map(Company::getName)
                                                                        .defaultIfEmpty("Unknown"),
                                                        appUserRepository.findById(savedProduct.getCreatedBy())
                                                                        .map(AppUser::getName)
                                                                        .defaultIfEmpty("Unknown"))
                                                        .map(namesTuple -> {
                                                                ProductResponse res = ProductResponse
                                                                                .from(savedProduct);
                                                                res.setCompanyName(namesTuple.getT1());
                                                                res.setCreatedByName(namesTuple.getT2());
                                                                return res;
                                                        });
                                })
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
