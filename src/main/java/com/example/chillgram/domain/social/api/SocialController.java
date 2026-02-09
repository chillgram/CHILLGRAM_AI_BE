package com.example.chillgram.domain.social.api;

import com.example.chillgram.common.security.AuthPrincipal;
import com.example.chillgram.domain.social.dto.SnsAccountDto;
import com.example.chillgram.domain.social.dto.SnsAccountsDto;
import com.example.chillgram.domain.social.dto.youtube.YoutubeAuthCodeExchangeRequest;
import com.example.chillgram.domain.social.dto.youtube.YoutubeAuthUrlResponse;
import com.example.chillgram.domain.social.service.SocialAccountService;
import com.example.chillgram.domain.social.service.YoutubeOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/social")
public class SocialController {

    private final SocialAccountService socialAccountService;
    private final YoutubeOAuthService youtubeOAuthService;

    // 유튜브 OAuth 시작용 인증 URL 발급
    @GetMapping("/youtube/auth-url")
    public Mono<YoutubeAuthUrlResponse> authUrl(@AuthenticationPrincipal AuthPrincipal principal) {
        return youtubeOAuthService.issueAuthUrl(principal.companyId())
                .map(YoutubeAuthUrlResponse::new);
    }

    // OAuth code/state를 토큰으로 교환하고 유튜브 계정 연결 처리
    @PostMapping("/youtube/exchange")
    public Mono<SnsAccountDto> exchange(
            @Valid @RequestBody YoutubeAuthCodeExchangeRequest req,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return youtubeOAuthService.exchange(principal.companyId(), req.code(), req.state());
    }

    // (테스트) 현재 유효한 유튜브 access_token 조회
    @GetMapping("/youtube/token")
    public Mono<Map<String, String>> token(@AuthenticationPrincipal AuthPrincipal principal) {
        return youtubeOAuthService.getValidAccessToken(principal.companyId()).map(t -> Map.of("accessToken", t));
    }

    // 현재 연결된 소셜 계정 상태 조회
    @GetMapping("/accounts")
    public Mono<SnsAccountsDto> getConnectedAccounts(@AuthenticationPrincipal AuthPrincipal principal) {
        return socialAccountService.getConnectedAccounts(principal.companyId());
    }

    // 특정 플랫폼 소셜 계정 연결 해제
    @DeleteMapping("/accounts/{platform}")
    public Mono<Void> disconnect(@PathVariable String platform, @AuthenticationPrincipal AuthPrincipal principal) {
        return socialAccountService.disconnect(principal.companyId(), platform);
    }
}
