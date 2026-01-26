package com.example.chillgram.domain.auth.service;

import com.example.chillgram.domain.auth.dto.SignupRequest;
import reactor.core.publisher.Mono;

public interface SignupService {
    Mono<Void> signup(SignupRequest req);
    Mono<Void> verifyEmail(String token);
}