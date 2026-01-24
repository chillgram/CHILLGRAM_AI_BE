package com.example.chillgram.api;

import com.example.chillgram.common.logging.AuditLog;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class LoggingTestController {

    @GetMapping("/ping")
    public Mono<String> ping() {
        return Mono.just("pong");
    }

    @PostMapping(value = "/orders/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @AuditLog("CREATE_ORDER")
    public Mono<String> create(@PathVariable("id") String id,
                               @RequestParam(name = "v", required = false) String v,
                               @RequestBody Mono<String> body) {
        return body.map(b -> "{\"ok\":true}");
    }
}