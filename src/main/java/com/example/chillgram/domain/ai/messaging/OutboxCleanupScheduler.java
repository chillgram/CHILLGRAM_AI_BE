package com.example.chillgram.domain.ai.messaging;

import com.example.chillgram.domain.ai.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxRepo;
    private final int retentionMinutes;

    public OutboxCleanupScheduler(
            OutboxEventRepository outboxRepo,
            @Value("${app.outbox.retention-minutes:10080}") int retentionMinutes // default 7일
    ) {
        this.outboxRepo = outboxRepo;
        this.retentionMinutes = retentionMinutes;
    }

    @Scheduled(fixedDelayString = "${app.outbox.cleanup-interval-ms:600000}")
    public void cleanup() {
        outboxRepo.deleteOlderThanMinutes(retentionMinutes).subscribe();
    }
}