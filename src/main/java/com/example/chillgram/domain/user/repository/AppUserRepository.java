package com.example.chillgram.domain.user.repository;

import com.example.chillgram.domain.user.domain.AppUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AppUserRepository extends ReactiveCrudRepository<AppUser, Long> {

    @Query("""
        SELECT * FROM app_user
        WHERE company_id = :companyId
          AND lower(email) = lower(:email)
        LIMIT 1
    """)
    Mono<AppUser> findByCompanyIdAndEmailIgnoreCase(Long companyId, String email);

    Mono<AppUser> findByEmail(String email);
}