package com.example.chillgram.domain.advertising.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

@Repository
public class EventCalendarRepository {

    private final DatabaseClient databaseClient;

    public EventCalendarRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<EventRow> findNearest(LocalDate baseDate, int limit) {
        if (baseDate == null) throw new IllegalArgumentException("baseDate must not be null");

        int safeLimit = Math.max(1, Math.min(limit, 50));

        String sql = """
        select
            event_date as event_date,
            event_name as event_name,
            (event_date - $1) as diff_days
        from event_calendar
        order by abs(event_date - $1) asc, event_date asc, event_name asc
        limit %d
        """.formatted(safeLimit);

        return databaseClient.sql(sql)
                .bind(0, baseDate) // $1
                .map((row, meta) -> new EventRow(
                        row.get("event_date", LocalDate.class),
                        row.get("event_name", String.class),
                        row.get("diff_days", Integer.class)
                ))
                .all();
    }

    public record EventRow(LocalDate date, String name, Integer diffDays) {}
}
