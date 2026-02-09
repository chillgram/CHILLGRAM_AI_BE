package com.example.chillgram.domain.social.repository;

import com.example.chillgram.domain.social.entity.SocialAccount;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SocialAccountRepository extends ReactiveCrudRepository<SocialAccount, Long> {
    // 회사+플랫폼 계정 조회(활성/비활성 포함)
//    Mono<SocialAccount> findByCompanyIdAndPlatform(Long companyId, String platform);
//    // 회사+플랫폼의 활성 계정 조회
//    Mono<SocialAccount> findByCompanyIdAndPlatformAndIsActiveTrue(Long companyId, String platform);
    // 회사의 활성 계정 전체 조회
    Flux<SocialAccount> findByCompanyIdAndIsActive(long companyId, boolean isActive);

    @Query("""
        UPDATE social_account
           SET is_active = FALSE,
               token_ref = NULL,
               connected_at = NULL
         WHERE company_id = :companyId
           AND UPPER(platform) = UPPER(:platform)
           AND is_active = TRUE
        """)
    Mono<Integer> deactivate(long companyId, String platform);

    @Query("""
        SELECT *
          FROM social_account
         WHERE company_id = :companyId
           AND UPPER(platform) = UPPER(:platform)
           AND (:active IS NULL OR is_active = :active)
        LIMIT 1
        """)
    Mono<SocialAccount> findOne(long companyId, String platform, Boolean active);
}