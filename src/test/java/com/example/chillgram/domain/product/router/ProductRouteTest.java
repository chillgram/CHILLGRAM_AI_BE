package com.example.chillgram.domain.product.router;

import com.example.chillgram.common.logging.AuditHandlerResolver;
import com.example.chillgram.common.logging.LogEmitter;
import com.example.chillgram.common.logging.LogSanitizer;
import com.example.chillgram.common.logging.RequestBodyCaptor;
import com.example.chillgram.common.logging.RequestClassifier;
import com.example.chillgram.common.logging.RequestParamExtractor;
import com.example.chillgram.common.logging.TraceIdResolver;
import com.example.chillgram.common.logging.model.AuditDecision;
import com.example.chillgram.domain.product.entity.Product;
import com.example.chillgram.domain.product.handler.ProductHandler;
import com.example.chillgram.domain.product.repository.ProductRepository;
import com.example.chillgram.domain.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

// ...

@WebFluxTest(controllers = ProductRouter.class, excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class })
@Import({ ProductHandler.class, ProductService.class })
class ProductRouteTest {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private ProductRepository productRepository;

        @MockitoBean
        private RequestClassifier requestClassifier;

        @MockitoBean
        private AuditHandlerResolver auditHandlerResolver;

        @MockitoBean
        private RequestParamExtractor requestParamExtractor;

        @MockitoBean
        private RequestBodyCaptor requestBodyCaptor;

        @MockitoBean
        private LogSanitizer logSanitizer;

        @MockitoBean
        private LogEmitter logEmitter;

        @MockitoBean
        private TraceIdResolver traceIdResolver;

        private Product product;

        @BeforeEach
        void setUp() {
                product = Product.builder()
                                .id(1L)
                                .name("Test Product")
                                .description("Test Description")
                                .price(new BigDecimal("15000"))
                                .category("FOOD")
                                .companyId(10L)
                                .isActive(true)
                                .createdBy("user1")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                // Stubbing Logging Dependencies to prevent NPE
                given(traceIdResolver.resolveOrCreate(any())).willReturn("test-trace-id");
                given(requestBodyCaptor.decorateIfNeeded(any())).willAnswer(invocation -> invocation.getArgument(0));
                given(auditHandlerResolver.resolve(any())).willReturn(AuditDecision.disabled("test"));
        }

        @Test
        @DisplayName("GET /api/v1/products/stats - 대시보드 통계 조회 성공")
        void getDashboardStats_Success() {
                // Given
                given(productRepository.countByCompanyId(anyLong())).willReturn(Mono.just(10L));
                given(productRepository.countByCompanyIdAndIsActiveTrue(anyLong())).willReturn(Mono.just(8L));
                given(productRepository.countByCompanyIdAndIsActiveFalse(anyLong())).willReturn(Mono.just(2L));

                // When & Then
                webTestClient.get()
                                .uri("/api/v1/products/stats")
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.totalCount").isEqualTo(10)
                                .jsonPath("$.activeCount").isEqualTo(8)
                                .jsonPath("$.inactiveCount").isEqualTo(2);
        }

        @Test
        @DisplayName("GET /api/v1/products - 목록 조회 성공 (No Search)")
        void getProductList_Success_NoSearch() {
                // Given (UI Sample Data)
                Product p1 = Product.builder()
                                .id(1L)
                                .companyId(1L)
                                .name("프리미엄 초콜릿") // UI Data
                                .category("초콜릿")
                                .description("벨기에산 카카오 70% 함유")
                                .price(new BigDecimal("15000"))
                                .isActive(true)
                                .createdAt(LocalDateTime.of(2024, 1, 15, 0, 0))
                                .build();

                Pageable pageable = PageRequest.of(0, 10);
                given(productRepository.findAllByCompanyIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class)))
                                .willReturn(reactor.core.publisher.Flux.just(p1));
                given(productRepository.countByCompanyId(anyLong())).willReturn(Mono.just(1L));

                // When & Then
                webTestClient.get()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/api/v1/products")
                                                .queryParam("page", "0")
                                                .queryParam("size", "10")
                                                .build())
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.content[0].name").isEqualTo("프리미엄 초콜릿")
                                .jsonPath("$.content[0].category").isEqualTo("초콜릿")
                                .jsonPath("$.content[0].description").isEqualTo("벨기에산 카카오 70% 함유")
                                .jsonPath("$.content[0].price").isEqualTo(15000)
                                .jsonPath("$.content[0].isActive").isEqualTo(true)
                                .jsonPath("$.content[0].createdAt").isNotEmpty(); // Date format check
        }

        @Test
        @DisplayName("GET /api/v1/products/{id} - 상세 조회 성공")
        void getProductDetail_Success() {
                // Given
                given(productRepository.findById(1L)).willReturn(Mono.just(product));

                // When & Then
                webTestClient.get()
                                .uri("/api/v1/products/1")
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.name").isEqualTo("Test Product")
                                .jsonPath("$.price").isEqualTo(15000);
        }

        @Test
        @DisplayName("GET /api/v1/products/{id} - 상세 조회 실패 (Not Found)")
        void getProductDetail_NotFound() {
                // Given
                given(productRepository.findById(999L)).willReturn(Mono.empty());

                // When & Then
                webTestClient.get()
                                .uri("/api/v1/products/999")
                                .exchange()
                                .expectStatus().isNotFound();
        }

}
