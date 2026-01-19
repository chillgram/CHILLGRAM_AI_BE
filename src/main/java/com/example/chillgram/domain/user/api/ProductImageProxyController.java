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

    private static final Set<String> ALLOWED_TYPES =
            Set.of("base", "video", "poster", "mockup", "banner");

    private final WebClient aiWebClient;

    public ProductImageProxyController(WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    @GetMapping("/hello")
    public Mono<ResponseEntity<String>> hello() {
        return aiWebClient.get()
                .uri("/ai/hello")
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/{productId}/images/{type}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> proxyImage(
            @PathVariable Long productId,
            @PathVariable String type
    ) {
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
                                        .body(body)
                        );
                    }

                    return Mono.just(ResponseEntity.status(res.statusCode()).build());
                });
    }
}