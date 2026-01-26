package com.example.chillgram.domain.qa.handler;

import com.example.chillgram.domain.qa.service.QaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QaHandler {

    private final QaService qaService;

    public Mono<ServerResponse> createQuestion(ServerRequest request) {
        return request.multipartData()
                .flatMap(parts -> {
                    Map<String, Part> partMap = parts.toSingleValueMap();

                    // 1. Extract Form Fields
                    String title = getFormValue(partMap, "title");
                    String content = getFormValue(partMap, "content");
                    Long categoryId = parseLongSafe(getFormValue(partMap, "category"));
                    Long companyId = parseLongSafe(getFormValue(partMap, "companyId"));
                    Long createdBy = parseLongSafe(getFormValue(partMap, "createdBy"));

                    // 2. Extract File (optional)
                    Part filePart = partMap.get("file");
                    FilePart file = (filePart instanceof FilePart) ? (FilePart) filePart : null;

                    // 3. Validation
                    if (title.isBlank() || content.isBlank()) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", "Title and content are required"));
                    }

                    // 4. Call Service and return response
                    return qaService.createQuestion(title, content, categoryId, companyId, createdBy, file)
                            .flatMap(response -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(response));
                })
                .onErrorResume(e -> {
                    log.error("Failed to create question", e);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", e.getMessage()));
                });
    }

    public Mono<ServerResponse> getQuestionList(ServerRequest request) {
        // Query Params Parsing
        int page = Integer.parseInt(request.queryParam("page").orElse("0"));
        int size = Integer.parseInt(request.queryParam("size").orElse("10"));
        String search = request.queryParam("search").orElse(null);
        String status = request.queryParam("status").orElse("ALL");

        return qaService.getQuestionList(page, size, search, status)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(e -> {
                    log.error("Failed to get question list", e);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", e.getMessage()));
                });
    }

    public Mono<ServerResponse> getQuestionDetail(ServerRequest request) {
        Long questionId = Long.parseLong(request.pathVariable("id"));

        return qaService.getQuestionDetail(questionId)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(e -> {
                    log.error("Failed to get question detail", e);
                    return ServerResponse.notFound().build();
                });
    }

    // ==================== 답변 작성 ====================
    public Mono<ServerResponse> createAnswer(ServerRequest request) {
        Long questionId = Long.parseLong(request.pathVariable("questionId"));

        return request.bodyToMono(AnswerRequest.class)
                .flatMap(req -> {
                    // Validation
                    if (req.body == null || req.body.isBlank()) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", "Answer body is required"));
                    }

                    return qaService.createAnswer(questionId, req.body, req.companyId, req.answeredBy)
                            .flatMap(response -> ServerResponse.status(201)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(response));
                })
                .onErrorResume(e -> {
                    log.error("Failed to create answer", e);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", e.getMessage()));
                });
    }

    // 답변 요청 DTO (내부 클래스)
    private static class AnswerRequest {
        public String body;
        public Long companyId;
        public Long answeredBy;
    }

    private String getFormValue(Map<String, Part> partMap, String key) {
        if (!partMap.containsKey(key)) {
            return "";
        }
        Part part = partMap.get(key);
        if (part instanceof FormFieldPart formFieldPart) {
            return formFieldPart.value();
        }
        return "";
    }

    private Long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
