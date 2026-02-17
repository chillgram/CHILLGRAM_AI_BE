package com.example.chillgram.common.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitQueuesConfig {

    @Bean
    public Queue jobResultsQueue(@Value("${app.jobs.result-queue}") String q) {
        return QueueBuilder.durable(q).build();
    }

    // [Fix] Worker로 보내는 큐도 선언 필요 (Default Exchange 사용 시 필수)
    @Bean
    public Queue jobsQueue(@Value("${app.jobs.routing-key}") String q) {
        return QueueBuilder.durable(q).build();
    }
}