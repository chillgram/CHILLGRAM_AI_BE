package com.example.chillgram.domain.ai.messaging;

import com.example.chillgram.domain.advertising.dto.jobs.JobResultRequest;
import com.example.chillgram.domain.ai.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Component
public class JobResultsConsumer {

    private final ObjectMapper om;
    private final JobService jobService;

    public JobResultsConsumer(ObjectMapper om, JobService jobService) {
        this.om = om;
        this.jobService = jobService;
    }

    @RabbitListener(queues = "${app.jobs.result-queue}")
    public void onMessage(String body) throws Exception {
        JsonNode n = om.readTree(body);

        String jobIdStr = n.path("jobId").asText("");
        if (jobIdStr.isBlank()) return;

        boolean success = n.path("success").asBoolean(false);
        String outputUri = n.path("outputUri").isNull() ? null : n.path("outputUri").asText(null);
        String errorCode = n.path("errorCode").isNull() ? null : n.path("errorCode").asText(null);
        String errorMessage = n.path("errorMessage").isNull() ? null : n.path("errorMessage").asText(null);

        JobResultRequest req = new JobResultRequest(success, outputUri, errorCode, errorMessage);

        // Listener 스레드에서 오래 붙잡지 않기: 별도 스케줄러로 넘김
        jobService.applyResult(UUID.fromString(jobIdStr), req)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}