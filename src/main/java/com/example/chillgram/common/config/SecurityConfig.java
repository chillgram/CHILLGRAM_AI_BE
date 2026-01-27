package com.example.chillgram.common.config;

import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
import com.example.chillgram.common.security.BearerTokenServerAuthenticationConverter;
import com.example.chillgram.common.security.JwtAuthenticationManager;
import com.example.chillgram.common.security.JwtProperties;
import com.example.chillgram.common.security.JwtTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties props) {
        return new JwtTokenService(props);
    }

    @Bean
    public ReactiveAuthenticationManager jwtAuthManager(JwtTokenService jwtTokenService) {
        return new JwtAuthenticationManager(jwtTokenService);
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveAuthenticationManager jwtAuthManager) {

        AuthenticationWebFilter jwtWebFilter = new AuthenticationWebFilter(jwtAuthManager);
        jwtWebFilter.setServerAuthenticationConverter(new BearerTokenServerAuthenticationConverter());

        // Security 레벨에서 터지는 401도 ApiException으로 통일 (네 전역 예외처리로 내려가게)
        jwtWebFilter.setAuthenticationFailureHandler((webFilterExchange, ex) ->
                Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "authentication failed"))
        );

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {}) // 아래 corsConfigurationSource Bean 사용
                .authorizeExchange(ex -> ex
                        // preflight
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/companies").permitAll() // 회원가입 시 회사목록
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyExchange().authenticated()
                )
                // JWT 인증 필터를 chain에 추가
                .addFilterAt(jwtWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));

        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "Location"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 운영: 문서 경로는 명시적으로 차단(이중 방어).
     * springdoc enabled=false라도 실수/업그레이드로 노출되는 케이스를 원천 차단.
     */
    @Bean
    @Profile("prod")
    public SecurityWebFilterChain prodSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()

                        // Swagger / OpenAPI (prod deny)
                        .pathMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).denyAll()

                        .anyExchange().authenticated()
                )
                .build();
    }
}
