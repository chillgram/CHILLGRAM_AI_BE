package com.example.chillgram.api;

import com.example.chillgram.common.exception.BusinessException;
import com.example.chillgram.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 테스트용 컨트롤러: 실제 앱 라우팅과 충돌 방지 위해 __test prefix 사용
 */
@RestController
@RequestMapping("/__test/errors")
public class TestErrorController {

    @GetMapping("/business")
    public Mono<String> business() {
        throw new BusinessException(
                ErrorCode.CONFLICT,
                "충돌 테스트",
                Map.of("reason", "duplicate")
        );
    }

    @GetMapping("/business-default-message")
    public Mono<String> businessDefaultMessage() {
        throw new BusinessException(ErrorCode.CONFLICT, "   ", Map.of());
    }

    @PostMapping("/validation")
    public Mono<String> validation(@Valid @RequestBody ValidationRequest req) {
        return Mono.just("ok");
    }

    @GetMapping("/unhandled")
    public Mono<String> unhandled() {
        return Mono.fromSupplier(() -> {
            throw new IllegalStateException("boom");
        });
    }

    record ValidationRequest(
            @NotBlank(message = "name은 필수입니다.")
            String name
    ) {}
}
