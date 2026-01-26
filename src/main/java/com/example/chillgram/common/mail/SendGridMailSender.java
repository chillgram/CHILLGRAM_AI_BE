package com.example.chillgram.common.mail;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class SendGridMailSender implements MailSenderPort {

    private final WebClient client;
    private final SendGridProperties props;

    public SendGridMailSender(WebClient.Builder builder, SendGridProperties props) {
        this.props = props;
        this.client = builder
                .baseUrl("https://api.sendgrid.com/v3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .build();
    }

    @Override
    public Mono<Void> sendVerificationMail(String toEmail, String verifyUrl) {
        String subject = "[Chillgram] 이메일 인증을 완료해주세요";
        String html = """
                <div style="font-family:Arial,sans-serif">
                  <h3>이메일 인증</h3>
                  <p>아래 링크를 눌러 인증을 완료하세요.</p>
                  <p><a href="%s">이메일 인증하기</a></p>
                  <p style="color:#666;font-size:12px">본인이 요청하지 않았다면 무시하세요.</p>
                </div>
                """.formatted(verifyUrl);

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail)))),
                "from", Map.of("email", props.fromEmail(), "name", props.fromName()),
                "subject", subject,
                "content", List.of(Map.of("type", "text/html", "value", html))
        );

        return client.post()
                .uri("/mail/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
//                .onStatus(
//                        status -> status.is4xxClientError() || status.is5xxServerError(),
//                        resp -> resp.bodyToMono(String.class)
//                                .defaultIfEmpty("")
//                                .flatMap(body -> Mono.error(new IllegalStateException(
//                                        "SendGrid error: HTTP " + resp.statusCode() + " body=" + body
//                                )))
//                )
                .toBodilessEntity() // SendGrid는 성공 시 202
                .then();
    }
}