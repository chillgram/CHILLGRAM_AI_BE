package com.example.chillgram.domain.ai.handler;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.google.BasicImageManifestService;
import com.example.chillgram.common.google.GcsFileStorage;
import com.example.chillgram.domain.advertising.dto.jobs.CreateJobRequest;
import com.example.chillgram.domain.advertising.dto.jobs.JobEnums;
import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Component
public class JobHandler {

    private final ObjectMapper om;
    private final JobService jobService;
    private final GcsFileStorage gcs;
    private final SmartValidator validator;
    private final BasicImageManifestService manifestService;

    /**
     * Worker → Spring 결과 콜백 보호용 시크릿
     * - Worker가 콜백 요청에 X-Job-Secret 헤더로 넣어야 함
     * - Spring은 이 값이 일치할 때만 DB 업데이트를 허용
     */
    private final String callbackSecret;

    public JobHandler(
            ObjectMapper om,
            JobService jobService,
            SmartValidator validator,
            GcsFileStorage gcs,
            BasicImageManifestService manifestService,
            @Value("${app.jobs.result-callback-secret}") String callbackSecret
    ) {
        this.om = om;
        this.jobService = jobService;
        this.validator = validator;
        this.gcs = gcs;
        this.manifestService = manifestService;
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

    public Mono<ServerResponse> createBasicImagesJob(ServerRequest req) {
        return req.multipartData().flatMap(parts -> {
            Part payloadPart = parts.getFirst("payload");
            Part filePart = parts.getFirst("file");

            if (!(payloadPart instanceof FormFieldPart p)) {
                return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "payload part required"));
            }
            if (!(filePart instanceof FilePart f)) {
                return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "file part required"));
            }

            JsonNode payloadJson;
            try {
                payloadJson = om.readTree(p.value());
            } catch (Exception e) {
                return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "payload json invalid"));
            }

            int n = payloadJson.path("n").asInt(3);
            if (n <= 0 || n > 10) {
                return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "n must be 1..10"));
            }

            // 1) jobId 먼저 생성 (tmp 입력 파일명에 사용)
            UUID jobId = UUID.randomUUID();
            String objectName = "tmp/basic-input/" + jobId + ".png";

            // 2) 입력 파일을 GCS tmp/basic-input/{jobId}.png 로 업로드
            //    - 네 GcsFileStorage는 파일명 기반으로 objectName을 자동 생성하므로,
            //      "jobId로 고정 파일명"이 필요하면 storeFixed() 같은 메서드를 추가하는 게 맞다.
            //    - 최소 변경으로 가려면: store() 결과 url을 inputUrl로 써도 됨.
            return gcs.storeFixed(f, objectName)
                    .flatMap(stored -> {
                        // 3) BASIC job payload 구성: inputUrl + n (+원하면 기타 텍스트들)
                        ObjectNode jobPayload = om.createObjectNode();
                        jobPayload.put("inputUrl", stored.gsUri()); // public https
                        jobPayload.put("n", n);

                        // 필요하면 payloadJson의 다른 필드도 같이 전달 가능
                        // (예: productName, adGoal 등) -> BASIC 생성 프롬프트에 쓸 거면 여기에 넣어라.
                        // jobPayload.set("context", payloadJson);

                        CreateJobRequest jobReq = new CreateJobRequest(
                                com.example.chillgram.domain.advertising.dto.jobs.JobEnums.JobType.BASIC,
                                jobPayload
                        );

                        // projectId는 BASIC에서는 의미 없으니 0으로 고정
                        return jobService.requestJob(0L, jobReq, req.exchange().getRequest().getId())
                                .flatMap(realJobId -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of("jobId", realJobId.toString())));
                    });
        });
    }

    public Mono<ServerResponse> getBasicImagesResult(ServerRequest req) {
        UUID jobId = UUID.fromString(req.pathVariable("jobId"));

        return jobService.getJob(jobId)
                .flatMap(job -> {
                    // status 그대로 내려줌 (REQUESTED/RUNNING/SUCCEEDED/FAILED)
                    if (job.status() != JobEnums.JobStatus.SUCCEEDED) {
                        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("status", job.status().name()));
                    }

                    // outputUri는 gs://.../manifest.json (worker가 그렇게 publish)
                    String manifestGsUri = job.outputUri();
                    if (manifestGsUri == null || manifestGsUri.isBlank()) {
                        return Mono.error(ApiException.of(ErrorCode.INTERNAL_ERROR, "manifest outputUri missing"));
                    }

                    return manifestService.readManifest(manifestGsUri)
                            .flatMap(manifest -> {
                                // candidates에 url 붙여서 반환
                                var candidates = new ArrayList<Map<String, Object>>();
                                for (int i = 0; i < manifest.candidates().size(); i++) {
                                    var c = manifest.candidates().get(i);
                                    candidates.add(Map.of(
                                            "id", String.valueOf(c.id()),
                                            "label", c.label(),
                                            "meta", c.meta(),
                                            "url", "/api/projects/basic-images/" + jobId + "/image/" + i
                                    ));
                                }
                                return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of(
                                                "status", job.status().name(),
                                                "candidates", candidates
                                        ));
                            });
                });
    }

    public Mono<ServerResponse> getBasicImage(ServerRequest req) {
        UUID jobId = UUID.fromString(req.pathVariable("jobId"));
        int idx = Integer.parseInt(req.pathVariable("idx"));

        return jobService.getJob(jobId)
                .flatMap(job -> {
                    if (job.status() != JobEnums.JobStatus.SUCCEEDED) {
                        return ServerResponse.status(409).bodyValue("job not succeeded");
                    }
                    String manifestGsUri = job.outputUri();
                    return manifestService.readManifest(manifestGsUri)
                            .flatMap(manifest -> {
                                if (idx < 0 || idx >= manifest.candidates().size()) {
                                    return ServerResponse.status(404).bodyValue("candidate not found");
                                }
                                String gsUri = manifest.candidates().get(idx).gsUri();
                                return gcs.fetchBytes(gsUri)
                                        .flatMap(bytes -> ServerResponse.ok()
                                                .contentType(MediaType.IMAGE_PNG)
                                                .bodyValue(bytes));
                            });
                });
    }
}
