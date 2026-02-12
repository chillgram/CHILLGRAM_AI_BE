package com.example.chillgram.domain.ai.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class OutboxEventRepository {

    private final DatabaseClient db;

    public OutboxEventRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insertOutbox(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String routingKey,
            JsonNode payload,
            OffsetDateTime now
    ) {
        return db.sql("""
                insert into outbox_event(id, aggregate_type, aggregate_id, event_type, routing_key, payload, created_at)
                values (:id, :aggregateType, :aggregateId, :eventType, :routingKey, cast(:payload as jsonb), :now)
                """)
                .bind("id", id)
                .bind("aggregateType", aggregateType)
                .bind("aggregateId", aggregateId)
                .bind("eventType", eventType)
                .bind("routingKey", routingKey)
                .bind("payload", payload.toString())
                .bind("now", now)
                .fetch().rowsUpdated()
                .then();
    }

    public Mono<Long> deleteOlderThanMinutes(int minutes) {
        return db.sql("""
                delete from outbox_event
                where created_at < now() - (:minutes || ' minutes')::interval
                """)
                .bind("minutes", minutes)
                .fetch().rowsUpdated();
    }
}