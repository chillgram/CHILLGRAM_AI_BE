package com.example.chillgram.domain.ai.messaging;

import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Component
public class JobResultsConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobResultsConsumer.class);

    private final ObjectMapper om;
    private final JobService jobService;

    public JobResultsConsumer(ObjectMapper om, JobService jobService) {
        this.om = om;
        this.jobService = jobService;
    }

    @RabbitListener(queues = "${app.jobs.result-queue}")
    public void onMessage(String body) throws Exception {
        final JsonNode n = om.readTree(body);

        final String jobIdStr = n.path("jobId").asText("");
        if (jobIdStr.isBlank()) {
            // 재시도해도 의미 없음 -> requeue 금지
            log.warn("Missing jobId. body={}", body);
            throw new AmqpRejectAndDontRequeueException("Missing jobId");
        }

        final UUID jobId;
        try {
            jobId = UUID.fromString(jobIdStr);
        } catch (IllegalArgumentException e) {
            // 재시도해도 100% 실패 -> 무한루프 차단
            log.warn("Invalid UUID jobId='{}'. body={}", jobIdStr, body);
            throw new AmqpRejectAndDontRequeueException("Invalid UUID jobId=" + jobIdStr, e);
        }

        final boolean success = n.path("success").asBoolean(false);
        final String outputUri = n.path("outputUri").isNull() ? null : n.path("outputUri").asText(null);
        final String errorCode = n.path("errorCode").isNull() ? null : n.path("errorCode").asText(null);
        final String errorMessage = n.path("errorMessage").isNull() ? null : n.path("errorMessage").asText(null);

        final JobResultRequest req = new JobResultRequest(success, outputUri, errorCode, errorMessage);

        jobService.applyResult(jobId, req)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(ex -> log.error("applyResult failed. jobId={}, body={}", jobId, body, ex))
                .block(); // [Fix] Void 메서드에서 비동기 유실 방지 및 안정적인 ACK 보장
    }
}
