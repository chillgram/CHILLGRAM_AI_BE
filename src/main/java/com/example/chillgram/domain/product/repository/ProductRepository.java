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
}
