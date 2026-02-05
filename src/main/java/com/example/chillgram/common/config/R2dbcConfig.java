package com.example.chillgram.common.config;

import io.r2dbc.spi.ConnectionFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    private final ConnectionFactory connectionFactory;

    public R2dbcConfig(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public @NonNull ConnectionFactory connectionFactory() {
        return this.connectionFactory;
    }

    @Bean
    public R2dbcTransactionManager r2dbcTransactionManager(ConnectionFactory cf) {
        return new R2dbcTransactionManager(cf);
    }

    @Bean
    public TransactionalOperator transactionalOperator(R2dbcTransactionManager tm) {
        return TransactionalOperator.create(tm);
    }

    // -------------------------------------------------------------------------
    // 1. Auditing (created_by 자동 입력)
    // -------------------------------------------------------------------------
    @Bean
    public ReactiveAuditorAware<Long> auditorAware() {
        return () -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(p -> p instanceof Long)
                .map(p -> (Long) p);
    }

    // -------------------------------------------------------------------------
    // 2. Custom Converters (원하시는 Mapping 방식)
    // -------------------------------------------------------------------------
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        // 강제로 PostgreSQL 방언을 사용하도록 설정 (자동 감지 실패 방지)
        return R2dbcCustomConversions.of(org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE, converters);
    }
}
