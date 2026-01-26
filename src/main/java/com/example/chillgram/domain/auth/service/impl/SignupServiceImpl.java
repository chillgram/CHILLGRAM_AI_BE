package com.example.chillgram.domain.auth.service.impl;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.mail.EmailVerificationTokenService;
import com.example.chillgram.common.mail.MailSenderPort;
import com.example.chillgram.domain.auth.constant.AuthConst;
import com.example.chillgram.domain.auth.dto.SignupRequest;
import com.example.chillgram.domain.auth.service.SignupService;
import com.example.chillgram.domain.company.repository.CompanyRepository;
import com.example.chillgram.domain.user.domain.AppUser;
import com.example.chillgram.domain.user.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * 회원가입 + 이메일 인증(verify) 흐름을 담당하는 서비스
 *
 * 1) 회사(companyId) 유효성 검사
 * 2) 동일 회사 내 이메일 사용자 존재 여부 확인
 *    - 이미 인증된 계정 / 탈퇴 계정: 가입 불가(응답은 컨트롤러에서 동일 메시지로 숨김)
 *    - 미인증/휴면 계정: 계정 정보를 갱신하고 인증메일 재발송(재가입/재인증 시나리오)
 *    - 미존재: 신규 사용자 생성 후 인증메일 발송
 * 3) 이메일 인증 토큰 소비(1회성) -> 사용자 상태 VERIFIED로 전환
 *
 */
@Service
public class SignupServiceImpl implements SignupService {

    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final MailSenderPort mailSender;
    private final EmailVerificationTokenService tokenService;
    private final PasswordEncoder encoder;

    public SignupServiceImpl(
            AppUserRepository userRepository,
            CompanyRepository companyRepository,
            MailSenderPort mailSender,
            EmailVerificationTokenService tokenService,
            PasswordEncoder encoder
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.mailSender = mailSender;
        this.tokenService = tokenService;
        this.encoder = encoder;
    }

    @Override
    public Mono<Void> signup(SignupRequest req) {
        String email = AuthConst.normalizeEmail(req.email());

        return companyRepository.existsById(req.companyId())
                .flatMap(exists -> exists
                        ? Mono.empty() : Mono.error(ApiException.of(
                        ErrorCode.INVALID_REQUEST, "invalid companyId=" + req.companyId()
                )))
                .then(
                        userRepository.findByCompanyIdAndEmailIgnoreCase(req.companyId(), email)
                                .flatMap(existing -> handleExisting(req, existing, email))
                                .switchIfEmpty(Mono.defer(() -> createNew(req, email)))
                ).then();
    }

    /**
     * 이미 동일 회사+이메일 사용자가 있는 경우 처리.
     * - VERIFIED/DELETED: 가입 불가(그러나 응답은 컨트롤러에서 동일 메시지로 숨김)
     * - UNVERIFIED/DORMANT: 최신 정보로 갱신 후 인증메일 재발송
     */
    private Mono<Void> handleExisting(SignupRequest req, AppUser existing, String email) {
        Integer st = existing.getStatus();

        // 인증(1) / 탈퇴(3) => 가입 불가(응답은 동일하게 하기 위해 조용히 종료)
        if (st != null && (st == AuthConst.STATUS_VERIFIED || st == AuthConst.STATUS_DELETED)) {
            return Mono.empty();
        }

        // 미인증(0)/휴면(2) => 미인증으로 갱신 + 메일 재발송
        existing.setStatus(AuthConst.STATUS_UNVERIFIED);
        existing.setEmail(email);
        existing.setName(req.name());
        existing.setPasswordHash(encoder.encode(req.password()));
        existing.setUpdatedAt(OffsetDateTime.now());
        if (req.privacyConsent()) existing.setPrivacyConsentAt(OffsetDateTime.now());

        return userRepository.save(existing)
                .flatMap(saved -> sendVerification(saved.getUserId(), email));
    }

    /**
     * 신규 사용자 생성 후 인증메일 발송.
     */
    private Mono<Void> createNew(SignupRequest req, String email) {
        AppUser u = new AppUser();
        u.setCompanyId(req.companyId());
        u.setEmail(email);
        u.setName(req.name());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole("OPERATOR"); // DB 체크제약 참고
        u.setStatus(AuthConst.STATUS_UNVERIFIED);
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        if (req.privacyConsent()) u.setPrivacyConsentAt(OffsetDateTime.now());

        return userRepository.save(u)
                .flatMap(saved -> sendVerification(saved.getUserId(), email));
    }

    /**
     * 이메일 인증 토큰 발급 + 인증 메일 발송.
     * - mailSender 구현체에서 실패 시 UPSTREAM_ERROR/UPSTREAM_TIMEOUT 등으로 매핑하는 걸 권장
     */
    private Mono<Void> sendVerification(Long userId, String email) {
        return tokenService.issue(userId)
                .flatMap(issued -> mailSender.sendVerificationMail(email, issued.verifyUrl()));
    }

    /**
     * 이메일 인증:
     * - 실패 케이스(만료/위조/이미 사용/유저 없음/탈퇴 등)는 외부 노출 메시지를 통일한다.
     * - 내부 detail은 로그용으로만 남긴다(토큰 원문/민감정보 금지).
     */
    @Override
    public Mono<Void> verifyEmail(String token) {
        return tokenService.consume(token)
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(ApiException.of(
                                ErrorCode.AUTH_EMAIL_TOKEN_INVALID,"token consume returned empty"
                        )))
                        .flatMap(u -> {
                            // 이미 인증(1)이면 멱등 처리(재클릭 대비)
                            if (u.getStatus() != null && u.getStatus() == AuthConst.STATUS_VERIFIED) {
                                return Mono.empty();
                            }
                            // 탈퇴(3)면 인증 불가
                            if (u.getStatus() != null && u.getStatus() == AuthConst.STATUS_DELETED) {
                                return Mono.error(ApiException.of(
                                        ErrorCode.AUTH_ACCOUNT_DELETED, "deleted user tried verify userId=" + userId
                                ));
                            }
                            u.setStatus(AuthConst.STATUS_VERIFIED);
                            u.setUpdatedAt(OffsetDateTime.now());
                            return userRepository.save(u).then();
                        })
                )
                .then();
    }
}