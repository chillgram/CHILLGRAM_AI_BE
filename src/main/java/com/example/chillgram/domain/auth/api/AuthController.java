package com.example.chillgram.domain.auth.api;

import com.example.chillgram.domain.auth.dto.ApiMessage;
import com.example.chillgram.domain.auth.dto.SignupRequest;
import com.example.chillgram.domain.auth.service.SignupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 인증(Auth) 관련 API 엔드포인트를 제공하는 컨트롤러
 * - 회원가입 요청을 받아 가입 프로세스를 시작
 * - 이메일 인증 토큰을 검증하여 이메일 인증을 완료
 *
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;

    public AuthController(SignupService signupService) {
        this.signupService = signupService;
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
}