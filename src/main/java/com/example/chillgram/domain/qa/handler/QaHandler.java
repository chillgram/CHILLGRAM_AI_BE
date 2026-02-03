package com.example.chillgram.domain.qa.handler;

import com.example.chillgram.domain.qa.dto.QaAnswerCreateRequest;
import com.example.chillgram.domain.qa.service.QaService;
import com.example.chillgram.domain.user.repository.AppUserRepository;
import com.example.chillgram.common.exception.ApiException;
import com.example.chillgram.common.exception.ErrorCode;
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

/**
 * Q&A API 핸들러 (Controller 역할)
 * 
 * [처리 흐름]
 * 1. Router로부터 ServerRequest(요청 정보)를 전달받음
 * 2. 요청 본문(Body)이나 파라미터를 파싱(Parsing) 및 검증(Validation)
 * 3. 비즈니스 로직 수행을 위해 Service 계층 호출 (qaService.method())
 * 4. Service의 결과를 받아 ServerResponse(HTTP 응답)로 변환하여 반환
 * 
 * ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
 * │ 프론트엔드 │ → │ QaRouter │ → │ QaHandler │
 * │ (브라우저) │ │ (URL 매핑) │ │ (실제 처리) │
 * └────────────────┘ └────────────────┘ └────────────────┘
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class QaHandler {

    private final QaService qaService;
    private final AppUserRepository appUserRepository;

    // ============================================================================
    // [POST] /api/v1/qs/questions - 질문 작성
    // ============================================================================
    // 입력: ServerRequest (multipart/form-data)
    // - title: 질문 제목 (필수)
    // - content: 질문 내용 (필수)
    // - category: 카테고리 ID
    // - companyId: 회사 ID
    // - createdBy: 작성자 ID
    // - file: 첨부파일 (선택)
    // 출력: ServerResponse
    // - 성공: 200 OK + QaWriteResponse (JSON)
    // - 실패: 400 Bad Request + { "error": "메시지" }
    // ============================================================================
    public Mono<ServerResponse> createQuestion(ServerRequest request) {
        // 로그인한 유저 ID 추출 (JWT principal에서)
        Mono<Long> userIdMono = request.principal()
                .map(principal -> {
                    if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
                        return (Long) auth.getPrincipal();
                    }
                    throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보를 확인할 수 없습니다.");
                })
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")));

        // userId로 DB 조회 → companyId 획득
        return userIdMono.flatMap(loggedInUserId -> appUserRepository.findById(loggedInUserId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")))
                .flatMap(user -> {
                    Long companyId = user.getCompanyId() != null ? user.getCompanyId() : 1L;
                    Long createdBy = user.getUserId();

                    log.info("User found: userId={}, companyId={}", createdBy, companyId);

                    return request.multipartData()
                            .flatMap(parts -> {
                                Map<String, Part> partMap = parts.toSingleValueMap();

                                // 1. Form Field 추출
                                String title = getFormValue(partMap, "title");
                                String content = getFormValue(partMap, "content");

                                // 기본값 1L 설정 (FK 에러 방지)
                                Long categoryId = parseLongSafe(getFormValue(partMap, "category"));
                                if (categoryId == null)
                                    categoryId = 1L;

                                log.info(
                                        "Create Question Params: title={}, content={}, categoryId={}, companyId={}, createdBy={}",
                                        title, content, categoryId, companyId, createdBy);

                                // 2. 첨부파일 추출 (선택)
                                Part filePart = partMap.get("file");
                                FilePart file = (filePart instanceof FilePart) ? (FilePart) filePart : null;

                                // 3. 필수값 검증
                                if (title.isBlank() || content.isBlank()) {
                                    String missing = "";
                                    if (title.isBlank())
                                        missing += "title ";
                                    if (content.isBlank())
                                        missing += "content ";
                                    log.warn("Missing required fields: {}", missing);
                                    return ServerResponse.badRequest()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(Map.of("error", "Missing required fields: " + missing.trim()));
                                }

                                // 4. Service 호출 → 응답 반환
                                return qaService.createQuestion(title, content, categoryId, companyId, createdBy, file)
                                        .flatMap(response -> ServerResponse.ok()
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(response));
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to create question", e);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "Server Error: " + e.getMessage()));
                }));
    }

    // ============================================================================
    // [GET] /api/v1/qs/questions - 목록 조회
    // ============================================================================
    // 입력: ServerRequest (Query Parameters)
    // - page: 페이지 번호 (기본 0)
    // - size: 페이지당 개수 (기본 10)
    // - search: 검색어 (제목+내용 검색)
    // - status: 상태 필터 (ALL, WAITING, ANSWERED)
    // 출력: ServerResponse
    // - 성공: 200 OK + Page<QaListResponse>
    // - 실패: 400 Bad Request + { "error": "메시지" }
    // ============================================================================
    public Mono<ServerResponse> getQuestionList(ServerRequest request) {
        // Query Parameter 추출
        int page = Integer.parseInt(request.queryParam("page").orElse("0"));
        int size = Integer.parseInt(request.queryParam("size").orElse("10"));
        String search = request.queryParam("search").orElse(null);
        String status = request.queryParam("status").orElse("ALL");

        // Service 호출 → 응답 반환
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

    // ============================================================================
    // [GET] /api/v1/qs/questions/{id} - 상세 조회
    // ============================================================================
    // 입력: ServerRequest (Path Variable)
    // - id: 질문 ID
    // 출력: ServerResponse
    // - 성공: 200 OK + QaDetailResponse (질문 + 첨부파일 + 답변 목록)
    // - 실패: 404 Not Found (질문이 존재하지 않을 때)
    // ============================================================================
    public Mono<ServerResponse> getQuestionDetail(ServerRequest request) {
        // Path Variable 추출
        Long questionId = Long.parseLong(request.pathVariable("id"));

        // Service 호출 → 응답 반환
        return qaService.getQuestionDetail(questionId)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(e -> {
                    log.error("Failed to get question detail", e);
                    return ServerResponse.notFound().build();
                });
    }

    // ============================================================================
    // [POST] /api/v1/qs/questions/{questionId}/answers - 답변 작성
    // ============================================================================
    // 입력: ServerRequest
    // - Path Variable: questionId (질문 ID)
    // - Body (JSON): { "body": "답변내용", "companyId": 5, "answeredBy": 99 }
    // 출력: ServerResponse
    // - 성공: 201 Created + QaAnswerResponse
    // - 실패: 400 Bad Request + { "error": "메시지" }
    // 부수 효과: 첫 답변 시 질문 상태를 WAITING → ANSWERED로 변경
    // ============================================================================
    public Mono<ServerResponse> createAnswer(ServerRequest request) {
        // Path Variable 추출
        Long questionId = Long.parseLong(request.pathVariable("questionId"));

        // 로그인한 유저 ID 추출 (JWT principal에서)
        Mono<Long> userIdMono = request.principal()
                .map(principal -> {
                    if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
                        return (Long) auth.getPrincipal();
                    }
                    throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보를 확인할 수 없습니다.");
                })
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")));

        // userId로 DB 조회 → companyId 획득
        return userIdMono.flatMap(loggedInUserId -> appUserRepository.findById(loggedInUserId)
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")))
                .flatMap(user -> {
                    Long companyId = user.getCompanyId() != null ? user.getCompanyId() : 1L;
                    Long answeredBy = user.getUserId();

                    log.info("User found for answer: userId={}, companyId={}", answeredBy, companyId);

                    return request.bodyToMono(QaAnswerCreateRequest.class)
                            .flatMap(req -> {
                                // 필수값 검증
                                if (req.getBody() == null || req.getBody().isBlank()) {
                                    return ServerResponse.badRequest()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(Map.of("error", "Answer body is required"));
                                }

                                log.info("Create Answer Params: questionId={}, companyId={}, answeredBy={}",
                                        questionId, companyId, answeredBy);

                                // Service 호출 → 응답 반환
                                return qaService.createAnswer(questionId, req.getBody(), companyId, answeredBy)
                                        .flatMap(response -> ServerResponse.status(201)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(response));
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to create answer", e);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", e.getMessage()));
                }));
    }

    // ============================================================================
    // 내부 클래스 및 유틸 메서드
    // ============================================================================

    /**
     * Multipart Form에서 특정 필드 값 추출
     */
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

    /**
     * 문자열을 Long으로 안전하게 파싱 (실패 시 null 반환)
     */
    private Long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============================================================================
    // [PUT] /api/qs/questions/{questionId} - 질문 수정
    // ============================================================================
    public Mono<ServerResponse> updateQuestion(ServerRequest request) {
        Long questionId = Long.parseLong(request.pathVariable("questionId"));

        // JWT에서 userId 추출
        Mono<Long> userIdMono = request.principal()
                .map(principal -> {
                    if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
                        return (Long) auth.getPrincipal();
                    }
                    throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보를 확인할 수 없습니다.");
                })
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")));

        return userIdMono
                .flatMap(userId -> request.bodyToMono(com.example.chillgram.domain.qa.dto.QaQuestionUpdateRequest.class)
                        .flatMap(req -> {
                            // Validation 검증
                            if (req.getTitle() == null || req.getTitle().isBlank()) {
                                return ServerResponse.badRequest()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of("error", "제목을 입력해주세요"));
                            }
                            if (req.getContent() == null || req.getContent().isBlank()) {
                                return ServerResponse.badRequest()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of("error", "내용을 입력해주세요"));
                            }

                            return qaService.updateQuestion(questionId, req.getTitle(), req.getContent(), userId)
                                    .flatMap(response -> ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(response));
                        })
                        .onErrorResume(e -> {
                            log.error("Failed to update question", e);
                            return ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Map.of("error", e.getMessage()));
                        }));
    }

    // ============================================================================
    // [PUT] /api/qs/questions/{questionId}/answers/{answerId} - 답변 수정
    // ============================================================================
    public Mono<ServerResponse> updateAnswer(ServerRequest request) {
        Long answerId = Long.parseLong(request.pathVariable("answerId"));

        // JWT에서 userId 추출
        Mono<Long> userIdMono = request.principal()
                .map(principal -> {
                    if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
                        return (Long) auth.getPrincipal();
                    }
                    throw ApiException.of(ErrorCode.UNAUTHORIZED, "인증 정보를 확인할 수 없습니다.");
                })
                .switchIfEmpty(Mono.error(ApiException.of(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")));

        return userIdMono
                .flatMap(userId -> request.bodyToMono(com.example.chillgram.domain.qa.dto.QaAnswerUpdateRequest.class)
                        .flatMap(req -> {
                            // Validation 검증
                            if (req.getBody() == null || req.getBody().isBlank()) {
                                return ServerResponse.badRequest()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of("error", "답변 내용을 입력해주세요"));
                            }

                            return qaService.updateAnswer(answerId, req.getBody(), userId)
                                    .flatMap(response -> ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(response));
                        })
                        .onErrorResume(e -> {
                            log.error("Failed to update answer", e);
                            return ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Map.of("error", e.getMessage()));
                        }));
    }
}
