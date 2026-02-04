# 광고 카피 생성 시스템 구현 계획서

## 구현 방향

### Phase 1 (지금) - LLM 카피 생성만
```
키워드 입력 → Gemini API 호출 → 카피/프롬프트 출력
```

### Phase 2 (나중에) - RAG 추가
```
키워드 → Gemini → 벡터 검색 → 점수 평가 → 재시도
```

---






## Phase 1: LLM 카피 생성 (지금 구현)

### 전체 흐름
```
Step 1 → Step 2 → Step 3 → Step 4 → Step 5 → Step 6 → Step 7
의존성    설정     DTO    Service  Handler  Router   테스트
```

---

## Git 커밋 전략

| 순서 | 타입 | 메시지 | 파일 |
|------|------|--------|------|
| 1 | `chore` | Spring AI Gemini 의존성 추가 | `build.gradle` |
| 2 | `chore` | Gemini API 설정 추가 | `application.properties` |
| 3 | `feat` | 광고 카피 응답 DTO 추가 | `AdCopyResponse.java` |
| 4 | `feat` | Gemini 기반 광고 카피 서비스 구현 | `AdCopyService.java` |
| 5 | `feat` | 함수형 광고 카피 핸들러 추가 | `AdCopyHandler.java` |
| 6 | `feat` | 광고 카피 API 라우터 추가 | `AdCopyRouter.java` |
| 7 | `docs` | 구현 계획서 추가 | `WEBFLUX_LANGGRAPH_PLAN.md` |

---

## Step 1: 의존성 추가 (5분)

**파일:** `build.gradle`

```groovy
dependencyManagement {
    imports {
        mavenBom "org.springframework.ai:spring-ai-bom:1.0.0"
    }
}

dependencies {
    // Gemini
    implementation 'org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter'
}
```

**커밋:**
```bash
git add build.gradle
git commit -m "chore: Spring AI Gemini 의존성 추가"
```

---

## Step 2: 설정 추가 (5분)

**파일:** `src/main/resources/application.properties`

```properties
# Gemini API 설정
spring.ai.google.gemini.api-key=${GEMINI_API_KEY}
spring.ai.google.gemini.chat.options.model=gemini-3-pro-preview
```

**커밋:**
```bash
git add src/main/resources/application.properties
git commit -m "chore: Gemini API 설정 추가"
```

---

## Step 3: AdCopyResponse DTO (5분)

**파일:** `src/main/java/com/example/chillgram/domain/ai/dto/AdCopyResponse.java`

```java
package com.example.chillgram.domain.ai.dto;

public record AdCopyResponse(
    String productName,
    String trendConcept,
    String finalCopy,
    String bannerPrompt,
    String snsPrompt
) {
    public static AdCopyResponse of(String productName, String concept, String copy) {
        String bannerPrompt = "헤드라인: " + copy + "\n컨셉: " + concept;
        String snsPrompt = "캡션: " + copy + " ✨\n#" + concept.replace(" ", "");
        
        return new AdCopyResponse(productName, concept, copy, bannerPrompt, snsPrompt);
    }
}
```

**커밋:**
```bash
git add src/main/java/com/example/chillgram/domain/ai/dto/AdCopyResponse.java
git commit -m "feat: 광고 카피 응답 DTO 추가"
```

---

## Step 4: AdCopyService (20분)

**파일:** `src/main/java/com/example/chillgram/domain/ai/service/AdCopyService.java`

```java
package com.example.chillgram.domain.ai.service;

import com.example.chillgram.domain.ai.dto.AdCopyRequest;
import com.example.chillgram.domain.ai.dto.AdCopyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdCopyService {
    private final ChatClient.Builder chatClientBuilder;
    
    public AdCopyResponse generate(AdCopyRequest request) {
        ChatClient chatClient = chatClientBuilder.build();
        
        // LLM으로 카피 생성
        String prompt = buildPrompt(request);
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();
        
        // 응답 파싱
        String concept = parseField(response, "컨셉");
        String copy = parseField(response, "카피");
        if (copy.length() > 10) copy = copy.substring(0, 10);
        
        return AdCopyResponse.of(request.keyword(), concept, copy);
    }
    
    private String buildPrompt(AdCopyRequest request) {
        return """
            당신은 광고 카피라이터입니다.
            
            제품: %s
            톤앤매너: %s
            타겟: %s
            
            아래 형식으로 출력하세요:
            컨셉: (트렌드를 반영한 한 문장)
            카피: (10자 이내의 광고 카피)
            """.formatted(
                request.keyword(),
                request.tone(),
                request.targetAudience()
            );
    }
    
    private String parseField(String response, String fieldName) {
        for (String line : response.split("\n")) {
            if (line.contains(fieldName + ":")) {
                return line.split(":", 2)[1].trim();
            }
        }
        return "";
    }
}
```

**커밋:**
```bash
git add src/main/java/com/example/chillgram/domain/ai/service/AdCopyService.java
git commit -m "feat: Gemini 기반 광고 카피 서비스 구현"
```

---

## Step 5: AdCopyHandler (10분)

**파일:** `src/main/java/com/example/chillgram/domain/ai/handler/AdCopyHandler.java`

```java
package com.example.chillgram.domain.ai.handler;

import com.example.chillgram.domain.ai.dto.AdCopyRequest;
import com.example.chillgram.domain.ai.service.AdCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdCopyHandler {
    private final AdCopyService adCopyService;
    
    public Mono<ServerResponse> generateCopy(ServerRequest request) {
        return request
            .bodyToMono(AdCopyRequest.class)
            .map(adCopyService::generate)
            .flatMap(response -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response))
            .onErrorResume(e -> ServerResponse
                .badRequest()
                .bodyValue(Map.of("error", e.getMessage())));
    }
    
    public Mono<ServerResponse> health(ServerRequest request) {
        return ServerResponse.ok()
            .bodyValue(Map.of("status", "UP"));
    }
}
```

**커밋:**
```bash
git add src/main/java/com/example/chillgram/domain/ai/handler/AdCopyHandler.java
git commit -m "feat: 함수형 광고 카피 핸들러 추가"
```

---

## Step 6: AdCopyRouter (5분)

**파일:** `src/main/java/com/example/chillgram/domain/ai/router/AdCopyRouter.java`

```java
package com.example.chillgram.domain.ai.router;

import com.example.chillgram.domain.ai.handler.AdCopyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class AdCopyRouter {
    
    @Bean
    public RouterFunction<ServerResponse> adCopyRoutes(AdCopyHandler handler) {
        return RouterFunctions.route()
            .POST("/api/v1/generate-copy", 
                accept(MediaType.APPLICATION_JSON), 
                handler::generateCopy)
            .GET("/api/health", 
                handler::health)
            .build();
    }
}
```

**커밋:**
```bash
git add src/main/java/com/example/chillgram/domain/ai/router/AdCopyRouter.java
git commit -m "feat: 광고 카피 API 라우터 추가"
```

---

## Step 7: 로컬 테스트 (10분)

### 환경변수 설정
```bash
export GEMINI_API_KEY=AIzaSyXXX
```

### 서버 실행
```bash
./gradlew bootRun
```

### API 테스트
```bash
# 헬스 체크
curl http://localhost:8080/api/health

# 카피 생성
curl -X POST http://localhost:8080/api/v1/generate-copy \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "저당 말차 라떼",
    "tone": "친근한",
    "targetAudience": "MZ세대"
  }'
```

### 예상 응답
```json
{
  "productName": "저당 말차 라떼",
  "trendConcept": "건강을 챙기면서도 달콤함을 포기하지 않는 선택",
  "finalCopy": "죄책감 제로",
  "bannerPrompt": "헤드라인: 죄책감 제로\n컨셉: 건강을 챙기면서...",
  "snsPrompt": "캡션: 죄책감 제로 ✨\n#건강달콤"
}
```

---

## 요약 (Phase 1)

| Step | 작업 | 커밋 | 파일 | 시간 |
|------|------|------|------|------|
| 1 | 의존성 | `chore` | build.gradle | 5분 |
| 2 | 설정 | `chore` | application.properties | 5분 |
| 3 | DTO | `feat` | AdCopyResponse.java | 5분 |
| 4 | Service | `feat` | AdCopyService.java | 20분 |
| 5 | Handler | `feat` | AdCopyHandler.java | 10분 |
| 6 | Router | `feat` | AdCopyRouter.java | 5분 |
| 7 | 테스트 | - | - | 10분 |

**총: 60분 + 7커밋**
## 도커 테스트2

---

## Phase 2: RAG 추가 (나중에)

### 추가 작업

| 작업 | 설명 |
|------|------|
| GCP PostgreSQL에 pgvector 설치 | `CREATE EXTENSION vector;` |
| VectorStoreConfig 추가 | PgVectorStore 빈 설정 |
| AdCopyService 수정 | 벡터 검색 + 점수 계산 로직 |
| 데이터 임베딩 | 기존 광고 카피를 벡터로 저장 |

### GCP PostgreSQL 연결
```properties
# 로컬 → GCP로 변경
spring.r2dbc.url=r2dbc:postgresql://GCP_IP:5432/chillgram
```
