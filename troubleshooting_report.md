# 프로젝트 API 트러블슈팅 보고서

## 1. 발생 가능한 에러 시나리오

### 1.1 DB 스키마 불일치 에러 (가장 가능성 높음)

#### 에러 증상
- **HTTP 500 Internal Server Error**
- 서버 로그에 다음과 같은 메시지:
  ```
  java.lang.IllegalStateException: Column 'userimg_gcs_url' not found
  또는
  org.springframework.r2dbc.BadSqlGrammarException: executeMany; bad SQL grammar
  ```

#### 원인
- `Project` 엔티티에 `userImgGcsUrl`, `reviewUrl` 필드를 추가했지만
- DB 테이블에는 해당 컬럼이 존재하지 않음

#### 해결 방법
```sql
-- project 테이블 확인
DESCRIBE project;

-- 컬럼이 없으면 추가
ALTER TABLE project 
ADD COLUMN userimg_gcs_url VARCHAR(500),
ADD COLUMN review_url VARCHAR(500);
```

#### 검증
```bash
# 서버 재시작 후
curl http://localhost:5173/api/products/63/projects
```

---

### 1.2 R2DBC 매핑 에러

#### 에러 증상
- **500 에러**
- 로그에 다음과 같은 메시지:
  ```
  Failed to convert from type [java.lang.String] to type [java.lang.Long]
  또는
  No converter found capable of converting from type [...]
  ```

#### 원인
- DB 컬럼 타입과 Java 필드 타입 불일치
- 예: DB는 `VARCHAR`인데 Java는 `Long` 타입

#### 해결 방법
1. **DB 스키마 확인**:
   ```sql
   SHOW CREATE TABLE project;
   ```

2. **엔티티 필드 타입 확인**:
   - `Project.java`의 필드 타입이 DB 컬럼 타입과 일치하는지 확인
   - `userImgGcsUrl`: `String` (DB: VARCHAR)
   - `reviewUrl`: `String` (DB: VARCHAR)

---

### 1.3 ProductCreateRequest 중복 빌더 호출 에러

#### 에러 증상
- **제품 생성 실패 (POST /api/products)**
- 로그:
  ```
  java.lang.IllegalStateException: Builder definition 'description' called twice
  ```

#### 원인 (이미 수정됨)
```java
// ❌ 잘못된 코드
.description(this.description)
.description(this.description)  // 중복!
```

#### 해결 방법 (적용 완료)
```java
// ✅ 올바른 코드
.description(this.description)
.isActive(this.isActive != null ? this.isActive : false)
```

---

### 1.4 Product 엔티티의 reviewUrl 잔존 에러

#### 에러 증상
- **제품 조회 시 예상치 못한 동작**
- DB에 `product.review_url` 컬럼이 있는데 Java 엔티티에는 없음

#### 원인
- DB 스키마와 엔티티 불일치

#### 현재 상태
- ✅ Java 엔티티에서 제거 완료
- ⚠️ DB 테이블에는 여전히 존재 (사용하지 않음)

#### 권장 해결 방법
```sql
-- Option 1: DB에서도 제거 (권장)
ALTER TABLE product DROP COLUMN review_url;

-- Option 2: 그냥 둠 (현재 상태 유지)
-- 기술적으로는 문제 없지만 혼란 가능
```

---

### 1.5 경로 변수 누락 에러 (이미 수정됨)

#### 에러 증상
- **이미지 업로드 API 호출 실패**
- 에러:
  ```
  org.springframework.web.server.MissingPathVariableException: 
  Required variable 'projectId' is not present
  ```

#### 원인 (수정 완료)
```java
// ❌ 잘못된 코드
@PostMapping(value = "/basic-images", ...)
public Mono<Map<String, Object>> createPreviewJob(
    @PathVariable long projectId,  // 경로에 {projectId} 없음!
```

#### 해결 방법 (적용 완료)
```java
// ✅ 올바른 코드
@PostMapping(value = "/{projectId}/basic-images", ...)
public Mono<Map<String, Object>> createPreviewJob(
    @PathVariable long projectId,
```

---

### 1.6 누락된 API 엔드포인트 (이미 추가됨)

#### 에러 증상
- **404 Not Found**
- `GET /api/products/{id}/projects` 호출 시

#### 원인 (해결 완료)
- `ProductController`에 해당 엔드포인트가 없었음

#### 해결 방법 (적용 완료)
```java
@GetMapping("/{id}/projects")
@Operation(summary = "제품별 프로젝트 목록 조회")
public Mono<List<ProjectResponse>> getProjectsByProduct(
        @PathVariable Long id) {
    return projectService.getProjectsByProduct(id);
}
```

---

## 2. 500 에러 디버깅 단계별 가이드

### Step 1: 서버 로그 확인
```bash
# Spring Boot 콘솔에서 에러 스택트레이스 확인
# 키워드 검색: "Column", "not found", "BadSqlGrammarException"
```

### Step 2: DB 스키마 검증
```sql
-- project 테이블 구조 확인
DESCRIBE project;

-- 예상 컬럼 목록:
-- - project_id
-- - product_id
-- - company_id
-- - title
-- - description
-- - userimg_gcs_url  ← 확인 필요!
-- - review_url        ← 확인 필요!
-- - status
-- - project_type
-- - ad_message_focus
-- - ad_message_target
-- - created_by
-- - created_at
-- - updated_at
```

### Step 3: 데이터 확인
```sql
-- product_id=63에 해당하는 프로젝트가 있는지 확인
SELECT * FROM project WHERE product_id = 63;

-- 모든 프로젝트 확인
SELECT project_id, product_id, title, userimg_gcs_url, review_url 
FROM project 
LIMIT 10;
```

### Step 4: API 직접 테스트
```bash
# cURL로 직접 호출
curl -X GET http://localhost:5173/api/products/63/projects \
  -H "Authorization: Bearer YOUR_TOKEN"

# 또는 Swagger UI에서 테스트
# http://localhost:5173/swagger-ui.html
```

### Step 5: 빌드 및 재시작
```bash
# 코드 변경 후 반드시 재빌드
./gradlew clean build

# 서버 재시작
./gradlew bootRun
```

---

## 3. 해결 이력 (Resolved Issues)

### ✅ 3.1 ProductCreateRequest 중복 빌더 호출
- **파일**: `ProductCreateRequest.java`
- **문제**: `.description()` 두 번 호출
- **해결**: 중복 라인 제거
- **커밋**: `fix: Remove duplicate description builder call`

### ✅ 3.2 ProjectController 경로 매핑 오류
- **파일**: `ProjectController.java`
- **문제**: `@PostMapping("/basic-images")`에 `{projectId}` 누락
- **해결**: `@PostMapping("/{projectId}/basic-images")`로 수정
- **커밋**: 적용 완료

### ✅ 3.3 ProductController 누락 엔드포인트
- **파일**: `ProductController.java`
- **문제**: `GET /api/products/{id}/projects` API 없음
- **해결**: `getProjectsByProduct()` 메서드 추가
- **커밋**: 적용 완료

### ✅ 3.4 ProjectResponse DTO 필드 누락
- **파일**: `ProjectResponse.java`
- **문제**: `userImgGcsUrl`, `reviewUrl` 필드 없음
- **해결**: 두 필드 추가 및 팩토리 메서드 업데이트
- **커밋**: 적용 완료

### ✅ 3.5 Product 엔티티 잘못된 필드
- **파일**: `Product.java`, `ProductCreateRequest.java`, `ProductResponse.java`, `ProductUpdateRequest.java`
- **문제**: `reviewUrl` 필드가 Product에 있었음 (Project에만 있어야 함)
- **해결**: Product 관련 모든 파일에서 `reviewUrl` 제거
- **커밋**: 적용 완료

### ✅ 3.6 ProjectService 중복 import
- **파일**: `ProjectService.java`
- **문제**: `Project` 엔티티 import 중복, 불필요한 `Flux` import
- **해결**: 중복 제거
- **커밋**: 적용 완료

---

## 4. 검증 완료 및 수정 이력

### ✅ 4.1 DB 스키마 동기화 (검증 완료 - 2026-02-16)
**현재 상태**: DB에 컬럼이 **이미 존재**함을 DBeaver 스크린샷으로 확인했습니다.

**확인된 컬럼**:
- `userimg_gcs_url`: varchar(255) ✅
- `reviewurl`: varchar(255) ✅

**중요 발견: PostgreSQL 대소문자 처리**
- DB에 `reviewUrl`로 생성했지만 PostgreSQL이 자동으로 소문자 `reviewurl`로 변환
- Java 엔티티의 `@Column("reviewUrl")`을 `@Column("reviewurl")`로 수정 완료

**수정 완료**:
```java
// Project.java (Line 63-67)
@Column("userimg_gcs_url")  // ✅ 정상
private String userImgGcsUrl;

@Column("reviewurl")         // ✅ 수정 완료 (reviewUrl → reviewurl)
private String reviewUrl;
```

---

### ⚠️ 4.2 여전히 확인 필요한 사항

### ⚠️ 4.2.1 인증 토큰 검증
**증상**: 프론트엔드에서 401 에러 대신 500 에러 발생 가능

**확인 방법**:
```bash
# Authorization 헤더 확인
curl -X GET http://localhost:5173/api/products/63/projects \
  -H "Authorization: Bearer YOUR_ACTUAL_TOKEN" \
  -v
```

### ⚠️ 4.2.2 Content 테이블 연관 관계
**잠재적 문제**: `ProjectService.getProjectsByProduct()`에서 `contentRepository.countByProjectId()` 호출 시 에러 가능

**확인 방법**:
```sql
-- content 테이블에 project_id 컬럼이 있는지 확인
DESCRIBE content;

-- 테스트 데이터 확인
SELECT project_id, COUNT(*) 
FROM content 
GROUP BY project_id;
```

---

## 5. 권장 조치 사항

### 우선순위 1: DB 마이그레이션 실행
```sql
-- project 테이블 컬럼 추가
ALTER TABLE project 
ADD COLUMN IF NOT EXISTS userimg_gcs_url VARCHAR(500),
ADD COLUMN IF NOT EXISTS review_url VARCHAR(500);

-- product 테이블에서 잘못된 컬럼 제거
ALTER TABLE product DROP COLUMN IF EXISTS review_url;
```

### 우선순위 2: 서버 로그 확인
- 정확한 500 에러 원인 파악
- Stack trace에서 실패한 지점 확인

### 우선순위 3: 통합 테스트
```bash
# 1. 제품 생성
curl -X POST http://localhost:5173/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "테스트 제품", "category": "테스트"}'

# 2. 프로젝트 생성
curl -X POST http://localhost:5173/api/projects?productId=1 \
  -H "Content-Type: application/json" \
  -d '{"title": "테스트 프로젝트", "description": "설명"}'

# 3. 프로젝트 목록 조회
curl -X GET http://localhost:5173/api/products/1/projects
```

---

## 6. 체크리스트

### ✅ 코드 수정 완료
- [x] ProductCreateRequest 중복 빌더 호출 수정
- [x] ProductUpdateRequest Lombok import 추가
- [x] ProjectController 경로 매핑 수정 (`/{projectId}/basic-images`)
- [x] ProductController에 프로젝트 목록 API 추가
- [x] ProjectResponse에 이미지 URL 필드 추가
- [x] Product 엔티티에서 reviewUrl 제거
- [x] 불필요한 import 정리
- [x] **Project 엔티티 컬럼 매핑 수정 (`reviewUrl` → `reviewurl`)**

### ✅ 검증 완료
- [x] **DB 스키마 확인 완료** (DBeaver 스크린샷)
- [x] **컴파일 성공** (BUILD SUCCESSFUL)
- [x] **Entity-DB 컬럼 매핑 검증**

### ⏳ 실행 테스트 필요
- [ ] **서버 실행 및 로그 확인**
- [ ] **API 통합 테스트 실행**
- [ ] **프론트엔드 연동 테스트**
