package com.example.chillgram.common.mail;

import reactor.core.publisher.Mono;

public interface MailSenderPort {
    Mono<Void> sendVerificationMail(String toEmail, String verifyUrl);
}