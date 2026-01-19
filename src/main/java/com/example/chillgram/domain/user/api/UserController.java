package com.example.chillgram.domain.user.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/hello")
    public Mono<String> hello() {
        return Mono.just("Hello World from WebFlux!");
    }

}