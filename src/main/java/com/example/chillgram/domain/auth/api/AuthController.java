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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 인증(Auth) 관련 API 엔드포인트
 */
@Tag(name = "Auth", description = "회원 인증 및 토큰(JWT) 관리 API")
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

    @SecurityRequirements
    @Operation(
            summary = "로그인",
            description = """
                    이메일과 비밀번호로 로그인하고 Access Token을 발급합니다.
                    Refresh Token은 HttpOnly Cookie로 설정됩니다.
                    (계정 존재 여부 노출 방지 정책 적용)
                    """
    )
    @PostMapping("/login")
    public Mono<TokenResponse> login(@RequestBody LoginRequest req, ServerHttpResponse response) {
        if (req == null || Strings.isBlank(req.email()) || Strings.isBlank(req.password())) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST, "email/password required"));
        }

        String email = AuthConst.normalizeEmail(req.email());

        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(invalidCredentials()))
                .flatMap(u -> {
                    ErrorCode statusError = mapStatusToLoginError(u.getStatus());
                    if (statusError != null) {
                        return Mono.error(ApiException.of(statusError, "로그인 차단: 계정 상태=" + u.getStatus()));
                    }

                    if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
                        return Mono.error(invalidCredentials());
                    }

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

    @SecurityRequirements
    @Operation(
            summary = "Access Token 재발급",
            description = """
                    Refresh Token 쿠키를 검증하여 새로운 Access Token을 발급합니다.
                    Refresh Token은 서버 저장값과 매칭하여 폐기 여부를 확인합니다.
                    """
    )
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

    @Operation(
            summary = "로그아웃",
            description = """
                    Refresh Token 쿠키를 만료시키고 서버 저장소에서 토큰을 제거합니다.
                    Access Token은 클라이언트에서 폐기 처리합니다.
                    """
    )
    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange, ServerHttpResponse response) {
        String refreshToken = extractRefreshCookie(exchange);

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

    @SecurityRequirements
    @Operation(
            summary = "회원가입 요청",
            description = """
                    회원가입 요청을 처리하고 이메일 인증을 시작합니다.
                    보안상 계정 존재 여부와 관계없이 동일한 응답 메시지를 반환합니다.
                    """
    )
    @PostMapping("/signup")
    public Mono<ApiMessage> signup(@Valid @RequestBody SignupRequest req) {
        return signupService.signup(req)
                .thenReturn(new ApiMessage("인증 메일을 전송했습니다. 메일함을 확인해주세요."));
    }

    @SecurityRequirements
    @Operation(
            summary = "이메일 인증 완료",
            description = """
                    이메일로 전달된 인증 토큰을 검증하여 계정을 활성화합니다.
                    """
    )
    @GetMapping("/verify-email")
    public Mono<ApiMessage> verifyEmail(@RequestParam("token") String token) {
        return signupService.verifyEmail(token)
                .thenReturn(new ApiMessage("이메일 인증이 완료되었습니다."));
    }

    private void setRefreshCookie(ServerHttpResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(14))
                .build();
        response.addCookie(cookie);
    }

    private String extractRefreshCookie(ServerWebExchange exchange) {
        HttpCookie c = exchange.getRequest().getCookies().getFirst("refresh_token");
        return (c == null) ? null : c.getValue();
    }

    private ApiException invalidCredentials() {
        return ApiException.of(
                ErrorCode.INVALID_CREDENTIALS,
                "로그인 실패: 이메일 또는 비밀번호 불일치"
        );
    }

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
