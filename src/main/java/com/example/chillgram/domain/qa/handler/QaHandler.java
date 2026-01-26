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
