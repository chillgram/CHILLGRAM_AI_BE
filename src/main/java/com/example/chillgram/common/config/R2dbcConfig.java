package com.example.chillgram.common.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.transaction.ReactiveTransactionManager;

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
    public ConnectionFactory connectionFactory() {
        return this.connectionFactory;
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
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
