package com.example.chillgram.domain.ai.handler;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
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
    private final String callbackSecret;

    public JobHandler(
            ObjectMapper om,
            JobService jobService,
            SmartValidator validator,
            GcsFileStorage gcs,
            @Value("${app.jobs.result-callback-secret}") String callbackSecret
    ) {
        this.om = om;
        this.jobService = jobService;
        this.validator = validator;
        this.gcs = gcs;
        this.callbackSecret = callbackSecret;
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

        String secret = req.headers().firstHeader("X-Job-Secret");
        if (secret == null || secret.isBlank() || !secret.equals(callbackSecret)) {
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

    /**
     * BASIC: 업로드한 입력 이미지를 GCS에 저장하고,
     * Worker에는 gs:// inputUrl 전달 (Worker가 gs:// 다운로드를 하기 때문)
     */
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

            final JsonNode payloadJson;
            try {
                payloadJson = om.readTree(p.value());
            } catch (Exception e) {
                return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "payload json invalid"));
            }

            final String p1 = payloadJson.path("prompt").isMissingNode() ? null : payloadJson.path("prompt").asText(null);
            final String p2 = payloadJson.path("instruction").isMissingNode() ? null : payloadJson.path("instruction").asText(null);
            final String prompt =
                    (p1 != null && !p1.isBlank()) ? p1
                            : (p2 != null && !p2.isBlank()) ? p2
                            : null;

            if (prompt == null) {
                return Mono.error(ApiException.of(ErrorCode.VALIDATION_FAILED, "prompt (or instruction) is required"));
            }

            final UUID tmpId = UUID.randomUUID();
            final String objectName = "tmp/basic-input/" + tmpId + ".png";

            return gcs.storeFixed(f, objectName)
                    .flatMap(stored -> {
                        // ✅ Worker 입력은 gs://
                        final String inputGsUri = stored.gsUri();

                        final ObjectNode jobPayload = om.createObjectNode();
                        jobPayload.put("inputUrl", inputGsUri);
                        jobPayload.put("prompt", prompt);

                        final CreateJobRequest jobReq = new CreateJobRequest(JobEnums.JobType.BASIC, jobPayload);

                        return jobService.requestJob(0L, jobReq, req.exchange().getRequest().getId())
                                .flatMap(realJobId -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of("jobId", realJobId.toString())));
                    });
        });
    }

    /**
     * ✅ 프론트가 기대하는 JSON을 준다.
     * - SUCCEEDED면 candidates[0].url 에 "공개 https URL"을 넣어준다.
     */
    public Mono<ServerResponse> getBasicImagesResult(ServerRequest req) {
        UUID jobId = UUID.fromString(req.pathVariable("jobId"));

        return jobService.getJob(jobId)
                .flatMap(job -> {
                    if (job.status() != JobEnums.JobStatus.SUCCEEDED) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("status", job.status().name()));
                    }

                    String out = job.outputUri();
                    if (out == null || out.isBlank()) {
                        return Mono.error(ApiException.of(ErrorCode.INTERNAL_ERROR, "outputUri missing"));
                    }

                    // ✅ gs:// => https://storage.googleapis.com/... 로 변환해서 프론트에 준다
                    String publicUrl = gcs.toPublicUrl(out);

                    var candidates = new ArrayList<Map<String, Object>>();
                    candidates.add(Map.of(
                            "id", "1",
                            "label", "basic-preview",
                            "meta", Map.of("jobId", jobId.toString(), "kind", "BASIC"),
                            "url", publicUrl
                    ));

                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of(
                                    "status", job.status().name(),
                                    "candidates", candidates
                            ));
                });
    }
}
