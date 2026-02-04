package com.example.chillgram.domain.user.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final WebClient aiWebClient;

    public UserController(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }
    @GetMapping("/hello")
    public Mono<String> hello() {
        return aiWebClient.get()
                .uri("/hello")
                .retrieve()
                .bodyToMono(String.class);
    }
}