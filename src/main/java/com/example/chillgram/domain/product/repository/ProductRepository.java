package com.example.chillgram.domain.product.repository;

import com.example.chillgram.domain.product.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductRepository extends R2dbcRepository<Product, Long> {

        // For Dashboard Stats
        Mono<Long> countByCompanyId(Long companyId);

        @Query("SELECT COUNT(*) FROM product WHERE company_id = :companyId AND is_active = true")
        Mono<Long> countByCompanyIdAndIsActiveTrue(Long companyId);

        @Query("SELECT COUNT(*) FROM product WHERE company_id = :companyId AND is_active = false")
        Mono<Long> countByCompanyIdAndIsActiveFalse(Long companyId);

        // For List with Search
        Flux<Product> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

        Flux<Product> findByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(Long companyId,
                        String name, String description, Pageable pageable);

        Mono<Long> countByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(Long companyId,
                        String name,
                        String description);

        // Optimized Query (N+1 Solve)
        @Query("""
                            SELECT p.product_id,
                                   p.company_id,
                                   p.name,
                                   p.category,
                                   p.description,
                                   p.is_active,
                                   p.created_by,
                                   p.created_at,
                                   p.updated_at,
                                   p.review_url,
                                   c.name AS company_name,
                                   u.name AS created_by_name
                              FROM product p
                              LEFT JOIN company c ON p.company_id = c.company_id
                              LEFT JOIN app_user u ON p.created_by = u.user_id
                             WHERE p.company_id = :companyId
                             ORDER BY p.created_at DESC
                             LIMIT :limit OFFSET :offset
                        """)
        Flux<ProductWithDetails> findAllWithDetails(Long companyId, int limit, long offset);

        record ProductWithDetails(
                        Long productId,
                        Long companyId,
                        String name,
                        String category,
                        String description,
                        Boolean isActive,
                        Long createdBy,
                        java.time.LocalDateTime createdAt,
                        java.time.LocalDateTime updatedAt,
                        String reviewUrl,
                        String companyName,
                        String createdByName) {
        }

        @Query("SELECT p.category FROM product p WHERE p.product_id = :productId")
        Mono<String> findCategoryByProductId(Long productId);
}
