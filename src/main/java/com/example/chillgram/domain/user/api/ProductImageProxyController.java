package com.example.chillgram.domain.user.api;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@RestController
@RequestMapping("/api/products")
public class ProductImageProxyController {

    private static final Set<String> ALLOWED_TYPES = Set.of("base", "video", "poster", "mockup", "banner");

    private final WebClient aiWebClient;

    // WebClient가 없어도(null) 빈 생성이 되도록 ObjectProvider 사용
    public ProductImageProxyController(
            org.springframework.beans.factory.ObjectProvider<WebClient> aiWebClientProvider) {
        this.aiWebClient = aiWebClientProvider.getIfAvailable();
    }

    @GetMapping("/hello")
    public Mono<ResponseEntity<String>> hello() {
        if (aiWebClient == null) {
            return Mono.just(ResponseEntity.status(503).body("AI Service Unavailable"));
        }
        return aiWebClient.get()
                .uri("/ai/hello")
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/{productId}/images/{type}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> proxyImage(
            @PathVariable Long productId,
            @PathVariable String type) {
        if (aiWebClient == null) {
            return Mono.just(ResponseEntity.status(503).build());
        }

        if (!ALLOWED_TYPES.contains(type)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return aiWebClient.get()
                .uri("/ai/products/{productId}/images/{type}", productId, type)
                .exchangeToMono(res -> {
                    if (res.statusCode().is2xxSuccessful()) {
                        MediaType contentType = res.headers()
                                .contentType()
                                .orElse(MediaType.APPLICATION_OCTET_STREAM);

                        Flux<DataBuffer> body = res.bodyToFlux(DataBuffer.class);

                        return Mono.just(
                                ResponseEntity.ok()
                                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                                        .contentType(contentType)
                                        .body(body));
                    }

                    return Mono.just(ResponseEntity.status(res.statusCode()).build());
                });
    }
}