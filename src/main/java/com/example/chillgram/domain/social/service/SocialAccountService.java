package com.example.chillgram.domain.social.service;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.social.dto.SnsAccountDto;
import com.example.chillgram.domain.social.dto.SnsAccountsDto;
import com.example.chillgram.domain.social.dto.SnsPlatform;
import com.example.chillgram.domain.social.entity.SocialAccount;
import com.example.chillgram.domain.social.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 소셜 계정 상태 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private static final String PLATFORM_YT = SnsPlatform.YOUTUBE.name();
    private static final String PLATFORM_IG = SnsPlatform.INSTAGRAM.name();

    private final SocialAccountRepository socialAccountRepository;

    /**
     * 현재 회사(companyId)의 "연결된 소셜 계정" 상태 조회
     */
    public Mono<SnsAccountsDto> getConnectedAccounts(long companyId) {
        return socialAccountRepository.findByCompanyIdAndIsActive(companyId, true)
                .collectMap(SocialAccount::getPlatform)
                .map(map -> {
                    var yt = map.get(PLATFORM_YT) != null
                            ? SnsAccountDto.youtube(map.get(PLATFORM_YT).getAccountLabel())
                            : SnsAccountDto.disconnected(SnsPlatform.YOUTUBE);

                    var ig = map.get(PLATFORM_IG) != null
                            ? SnsAccountDto.connected(SnsPlatform.INSTAGRAM, map.get(PLATFORM_IG).getAccountLabel())
                            : SnsAccountDto.disconnected(SnsPlatform.INSTAGRAM);

                    return new SnsAccountsDto(yt, ig);
                });
    }

    /**
     * 특정 플랫폼(platform)의 소셜 계정 연결을 해제
     * - 회사+플랫폼의 활성 계정을 비활성화하고 토큰 참조/연결시각을 초기화
     */
    public Mono<Void> disconnect(long companyId, String platform) {
        return socialAccountRepository.deactivate(companyId, platform)
                .flatMap(updated -> {
                    if (updated == null || updated == 0) {
                        return Mono.error(ApiException.of(
                                ErrorCode.SOCIAL_ACCOUNT_NOT_CONNECTED,
                                "해당 SNS 계정이 연결되어 있지 않습니다."
                        ));
                    }
                    return Mono.empty();
                });
    }
}
