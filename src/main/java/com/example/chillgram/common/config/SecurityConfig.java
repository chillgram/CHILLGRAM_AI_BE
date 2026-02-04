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
import org.springframework.core.env.Environment;
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
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveAuthenticationManager jwtAuthManager,
            Environment env) {
        AuthenticationWebFilter jwtWebFilter = new AuthenticationWebFilter(jwtAuthManager);
        jwtWebFilter.setServerAuthenticationConverter(new BearerTokenServerAuthenticationConverter());

        jwtWebFilter.setAuthenticationFailureHandler((webFilterExchange, ex) -> Mono
                .error(ApiException.of(ErrorCode.UNAUTHORIZED, "authentication failed")));

        boolean isProd = List.of(env.getActiveProfiles()).contains("prod");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {
                }) // corsConfigurationSource Bean 사용
                .authorizeExchange(ex -> {
                    ex.pathMatchers(HttpMethod.OPTIONS).permitAll();
                    ex.pathMatchers("/health").permitAll();
                    ex.pathMatchers("/actuator/health", "/actuator/info").permitAll();
                    ex.pathMatchers("/api/auth/**").permitAll();
                    ex.pathMatchers(HttpMethod.GET, "/api/companies").permitAll();

                    // Q&A 조회 API 공개 (질문 목록, 질문 상세)
                    ex.pathMatchers(HttpMethod.GET, "/api/qs/questions").permitAll();
                    ex.pathMatchers(HttpMethod.GET, "/api/users/hello").permitAll();

                    if (isProd) {
                        ex.pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").denyAll();
                    } else {
                        ex.pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }

                    ex.anyExchange().authenticated();
                })
                .addFilterAt(jwtWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "Location"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
