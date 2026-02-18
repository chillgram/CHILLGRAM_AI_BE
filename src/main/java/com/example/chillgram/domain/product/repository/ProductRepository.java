package com.example.chillgram.domain.product.repository;

import com.example.chillgram.domain.product.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductRepository extends R2dbcRepository<Product, Long> {

        // For Dashboard Stats
        Mono<Long> countByCompanyId(Long companyId);

        @org.springframework.data.r2dbc.repository.Query("SELECT COUNT(*) FROM product WHERE company_id = :companyId AND is_active = true")
        Mono<Long> countByCompanyIdAndIsActiveTrue(Long companyId);

        @org.springframework.data.r2dbc.repository.Query("SELECT COUNT(*) FROM product WHERE company_id = :companyId AND is_active = false")
        Mono<Long> countByCompanyIdAndIsActiveFalse(Long companyId);

        // For List with Search
        Flux<Product> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

        Flux<Product> findByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(Long companyId,
                        String name, String description, Pageable pageable);

        Mono<Long> countByCompanyIdAndNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(Long companyId,
                        String name,
                        String description);

        // Optimized Query (N+1 Solve)
        @org.springframework.data.r2dbc.repository.Query("""
                            SELECT p.product_id AS id,
                                   p.company_id AS companyId,
                                   p.name AS name,
                                   p.category AS category,
                                   p.description AS description,
                                   p.is_active AS isActive,
                                   p.created_by AS createdBy,
                                   p.created_at AS createdAt,
                                   p.updated_at AS updatedAt,
                                   p.review_url AS reviewUrl,
                                   c.name AS companyName,
                                   u.name AS createdByName
                              FROM product p
                              LEFT JOIN company c ON p.company_id = c.company_id
                              LEFT JOIN app_user u ON p.created_by = u.user_id
                             WHERE p.company_id = :companyId
                             ORDER BY p.created_at DESC
                             LIMIT :limit OFFSET :offset
                        """)
        Flux<ProductWithDetails> findAllWithDetails(Long companyId, int limit, long offset);

        record ProductWithDetails(
                        Long id,
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
}
