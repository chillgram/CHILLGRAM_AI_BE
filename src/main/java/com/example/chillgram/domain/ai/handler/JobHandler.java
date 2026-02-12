package com.example.chillgram.domain.ai.handler;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.service.JobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class JobHandler {

    private final JobService jobService;
    private final SmartValidator validator;

    /**
     * Worker → Spring 결과 콜백 보호용 시크릿
     * - Worker가 콜백 요청에 X-Job-Secret 헤더로 넣어야 함
     * - Spring은 이 값이 일치할 때만 DB 업데이트를 허용
     */
    private final String callbackSecret;

    public JobHandler(
            JobService jobService,
            SmartValidator validator,
            @Value("${app.jobs.result-callback-secret}") String callbackSecret
    ) {
        this.jobService = jobService;
        this.validator = validator;
        this.callbackSecret = callbackSecret;
    }

    public Mono<ServerResponse> createJob(ServerRequest req) {
        long projectId = Long.parseLong(req.pathVariable("projectId"));
        String traceId = req.headers().firstHeader("X-Trace-Id");

        return req.bodyToMono(CreateJobRequest.class)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "body is required")))
                .flatMap(body -> {
                    var errors = new BeanPropertyBindingResult(body, "CreateJobRequest");
                    validator.validate(body, errors);
                    if (errors.hasErrors()) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, errors.getAllErrors().toString()));
                    }

                    return jobService.requestJob(projectId, body, traceId)
                            .flatMap(jobId -> ServerResponse.accepted()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Map.of("jobId", jobId.toString(), "status", "REQUESTED")));
                });
    }

    public Mono<ServerResponse> getJob(ServerRequest req) {
        UUID jobId = UUID.fromString(req.pathVariable("jobId"));
        return jobService.getJob(jobId)
                .flatMap(job -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(job));
    }

    public Mono<ServerResponse> postResult(ServerRequest req) {
        UUID jobId = UUID.fromString(req.pathVariable("jobId"));

        // ---------------------------------------------------------------------
        // [중요] Worker 콜백 보호: 공유 시크릿 헤더 검증
        // - JWT를 쓰면 안 됨(Worker는 사용자 세션이 아님)
        // - 네트워크가 열려있으면 아무나 결과를 조작할 수 있으므로 반드시 막아야 함
        // ---------------------------------------------------------------------
        String secret = req.headers().firstHeader("X-Job-Secret");
        if (secret == null || secret.isBlank() || !secret.equals(callbackSecret)) {
            // 인증 실패는 403 (Forbidden)
            return ServerResponse.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("code", "FORBIDDEN", "message", "invalid callback secret"));
        }

        return req.bodyToMono(JobResultRequest.class)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "body is required")))
                .flatMap(body -> {
                    var errors = new BeanPropertyBindingResult(body, "JobResultRequest");
                    validator.validate(body, errors);
                    if (errors.hasErrors()) {
                        return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, errors.getAllErrors().toString()));
                    }
                    return jobService.applyResult(jobId, body)
                            .then(ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Map.of("ok", true)));
                });
    }
}
