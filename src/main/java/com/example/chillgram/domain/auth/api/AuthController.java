package com.example.chillgram.domain.auth.api;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.security.JwtTokenService;
import com.example.chillgram.common.security.RefreshTokenStore;
import com.example.chillgram.domain.auth.constant.AuthConst;
import com.example.chillgram.domain.auth.dto.*;
import com.example.chillgram.domain.auth.service.SignupService;
import com.example.chillgram.domain.user.repository.AppUserRepository;
import com.networknt.schema.utils.Strings;
import jakarta.validation.Valid;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 인증(Auth) 관련 API 엔드포인트를 제공하는 컨트롤러
 * - 회원가입 요청을 받아 가입 프로세스를 시작
 * - 이메일 인증 토큰을 검증하여 이메일 인증을 완료
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwt;
    private final RefreshTokenStore refreshStore;

    public AuthController(
            SignupService signupService,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwt,
            RefreshTokenStore refreshStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshStore = refreshStore;
        this.signupService = signupService;
    }

    @PostMapping("/login")
    public Mono<TokenResponse> login(@RequestBody LoginRequest req, ServerHttpResponse response) {
        if (req == null || Strings.isBlank(req.email()) || Strings.isBlank(req.password())) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "email/password required"));
        }

        String email = AuthConst.normalizeEmail(req.email());

        return userRepository.findByEmail(email)
                // 계정 없음도 동일하게 처리(계정 존재 유추 방지)
                .switchIfEmpty(Mono.error(invalidCredentials()))
                .flatMap(u -> {
                    // 계정 상태 체크
                    ErrorCode statusError = mapStatusToLoginError(u.getStatus());
                    if (statusError != null) {
                        return Mono.error(ApiException.of(statusError, "로그인 차단: 계정 상태=" + u.getStatus()));
                    }

                    // 비밀번호 체크
                    if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
                        return Mono.error(invalidCredentials());
                    }

                    // 토큰 발급 + refresh 저장 + 쿠키 설정
                    long userId = u.getUserId();
                    String role = normalizeRole(u.getRole());

                    String access = jwt.createAccessToken(userId, role);
                    String refresh = jwt.createRefreshToken(userId, role);

                    return refreshStore.saveHashed(userId, refresh)
                            .thenReturn(new IssuedTokens(access, refresh))
                            .doOnNext(tokens -> setRefreshCookie(response, tokens.refresh()));
                })
                .map(tokens -> new TokenResponse(tokens.access(), null));
    }

    private record IssuedTokens(String access, String refresh) {}

    private void setRefreshCookie(ServerHttpResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false) // 로컬 개발이면 false, 운영 HTTPS면 true
                .sameSite("Lax") // SPA + API 분리면 보통 Lax 또는 None(단 None이면 Secure 필수)
                .path("/api/auth") // refresh/logout 범위만
                .maxAge(Duration.ofDays(14))
                .build();

        response.addCookie(cookie);
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refresh(ServerWebExchange exchange) {
        String refreshToken = extractRefreshCookie(exchange);
        if (Strings.isBlank(refreshToken)) {
            return Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "refresh cookie missing"));
        }

        return Mono.fromCallable(() -> jwt.parse(refreshToken))
                .flatMap(jws -> {
                    if (!"refresh".equals(jwt.getType(jws))) {
                        return Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "not refresh token"));
                    }
                    long userId = jwt.getUserId(jws);
                    String role = jwt.getRole(jws);

                    return refreshStore.matches(userId, refreshToken)
                            .flatMap(ok -> {
                                if (!ok) return Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "refresh revoked"));
                                String newAccess = jwt.createAccessToken(userId, role);
                                return Mono.just(new TokenResponse(newAccess, null));
                            });
                });
    }

    private String extractRefreshCookie(ServerWebExchange exchange) {
        HttpCookie c = exchange.getRequest().getCookies().getFirst("refresh_token");
        return (c == null) ? null : c.getValue();
    }

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange, ServerHttpResponse response) {
        String refreshToken = extractRefreshCookie(exchange);

        // 쿠키 삭제(만료)
        ResponseCookie delete = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addCookie(delete);

        if (Strings.isBlank(refreshToken)) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> jwt.parse(refreshToken))
                .flatMap(jws -> refreshStore.delete(jwt.getUserId(jws)))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * 회원가입 요청 API.
     * 보안 목적: "계정 존재 여부 노출 방지"
     * - 가입이 실제로 처리되었는지/이미 존재하는 이메일인지 등 내부 상태를 응답으로 구분하지 않고
     *   항상 동일한 메시지를 반환하여 사용자 열거(Enumeration) 공격을 어렵게 만든다.
     */
    @PostMapping("/signup")
    public Mono<ApiMessage> signup(@Valid @RequestBody SignupRequest req) {
        // 계정 존재 여부 노출 방지: 항상 같은 메시지
        return signupService.signup(req).thenReturn(new ApiMessage("인증 메일을 전송했습니다. 메일함을 확인해주세요."));
    }

    /**
     * 이메일 인증 완료 API.
     * - 사용자가 이메일 링크를 클릭했을 때 호출되는 엔드포인트를 가정한다.
     * - token 파라미터를 받아 서비스에서 유효성 검증/만료 확인/사용 처리 등을 수행한다.
     */
    @GetMapping("/verify-email")
    public Mono<ApiMessage> verifyEmail(@RequestParam("token") String token) {
        return signupService.verifyEmail(token)
                .thenReturn(new ApiMessage("이메일 인증이 완료되었습니다."));
    }

    /** 로그인 실패(계정 없음/비번 불일치) 공통 에러 */
    private ApiException invalidCredentials() {
        return ApiException.of(
                ErrorCode.INVALID_CREDENTIALS,
                "로그인 실패: 이메일 또는 비밀번호 불일치"
        );
    }

    /**
     * 로그인에서 계정 상태에 따른 에러 코드 매핑
     * - 정상(VERIFIED)이면 null 반환
     */
    private ErrorCode mapStatusToLoginError(Integer status) {
        if (status == null) return ErrorCode.ACCOUNT_STATUS_INVALID;

        return switch (status) {
            case AuthConst.STATUS_VERIFIED   -> null;
            case AuthConst.STATUS_UNVERIFIED -> ErrorCode.ACCOUNT_UNVERIFIED;
            case AuthConst.STATUS_DORMANT    -> ErrorCode.ACCOUNT_DORMANT;
            case AuthConst.STATUS_DELETED    -> ErrorCode.ACCOUNT_DELETED;
            default                          -> ErrorCode.ACCOUNT_STATUS_INVALID;
        };
    }

    private String normalizeRole(String role) {
        return (role == null || role.isBlank()) ? "USER" : role;
    }
}