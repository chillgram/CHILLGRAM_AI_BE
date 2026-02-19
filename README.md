# Chillgram AI Backend

> **AI-Powered Advertising Content Platform**
> 광고 카피·배너 이미지를 자동으로 생성하는 완전 비동기 AI 백엔드

---

## 🧩 프로젝트 개요

Chillgram AI Backend는 기업 마케터가 상품 정보를 입력하면 **AI 기반 광고 카피·배너 이미지를 자동 생성**해 주는 핵심 서버입니다. 전통적인 블로킹 방식 대신, **Spring WebFlux** 완전 비동기 리액티브 파이프라인 위에서 돌아가며, 무거운 이미지 AI 작업은 **RabbitMQ**를 통해 Python AI Worker로 위임하여 서버 자원을 최대한 효율적으로 사용합니다.

---

## 🏗️ MSA (Microservices Architecture)

Chillgram은 **단일 모놀리스가 아닌 역할별로 분리된 마이크로서비스 구조**로 설계되었습니다. 각 서비스는 독립적으로 배포·확장 가능하며, 서비스 간 결합도를 최소화합니다.

### 서비스 구성

```
┌─────────────────────────────────────────────────────────────────┐
│                     GCP Infrastructure                          │
│                                                                 │
│  ┌──────────────────┐      ┌──────────────────┐                │
│  │  Frontend (Next) │      │  Spring AI BE     │                │
│  │  (React / Next)  │─────▶│  (Java 21 /       │                │
│  └──────────────────┘ REST │   WebFlux)        │                │
│                            └────────┬──────────┘                │
│                                     │  AMQP (RabbitMQ)          │
│                                     ▼                           │
│                            ┌──────────────────┐                │
│                            │  Python AI Worker │                │
│                            │  (CHILLGRAM_AI)   │                │
│                            │  Gemini / Diffusion│               │
│                            └────────┬──────────┘                │
│                                     │                           │
│          ┌──────────────────────────┼────────────────┐         │
│          ▼                          ▼                 ▼         │
│  ┌──────────────┐        ┌──────────────┐  ┌──────────────┐   │
│  │  PostgreSQL   │        │    Redis     │  │     GCS      │   │
│  │  (R2DBC)      │        │ (RefreshTok) │  │ (이미지 저장) │   │
│  └──────────────┘        └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 서비스 역할 분리

| 서비스 | 언어 / 프레임워크 | 역할 |
|---|---|---|
| **CHILLGRAM_AI_BE** | Java 21 / Spring WebFlux | API 게이트웨이, 비즈니스 로직, 인증, Job 관리 |
| **CHILLGRAM_AI** | Python / FastAPI | AI 이미지/영상 생성 (Gemini, Diffusion) |
| **RabbitMQ** | AMQP | 서비스 간 비동기 메시지 브로커 |
| **PostgreSQL** | R2DBC | 비즈니스 데이터 (광고, 상품, 유저) |
| **Redis** | ReactiveRedisTemplate | Refresh Token 저장소, 세션 상태 관리 |
| **GCS** | Google Cloud Storage | AI 입출력 이미지·영상 파일 저장소 |

### 서비스 간 통신 전략

Chillgram MSA에서 서비스 통신은 **동기(REST)** 와 **비동기(AMQP)** 를 목적에 따라 명확히 구분합니다.

| 통신 유형 | 사용 경로 | 이유 |
|---|---|---|
| **REST (동기)** | Frontend → Spring AI BE | 사용자 요청/응답, 빠른 피드백 필요 |
| **AMQP/RabbitMQ (비동기)** | Spring AI BE ↔ Python Worker | 무거운 AI 추론, HTTP 타임아웃 회피 |
| **GCS (공유 스토리지)** | Spring AI BE ↔ Python Worker | 대용량 이미지 파일 전달 (URI 공유) |

> **설계 원칙**: 응답 지연이 허용되는 AI 이미지 생성 작업은 **절대 HTTP 동기 호출로 처리하지 않습니다.** Job ID를 즉시 반환하고, Worker가 완료하면 RabbitMQ를 통해 결과를 Spring BE에 전달합니다.

### 공유 스토리지 전략 (GCS)

Python Worker와 Spring BE가 **직접 파일을 HTTP로 주고받지 않습니다.** 대신 GCS 버킷을 공유 스토리지로 활용하여 대용량 바이너리 전송 오버헤드를 제거합니다.

```
Spring BE ──(gs://bucket/input.png)──▶ RabbitMQ ──▶ Python Worker
                                                          │
                                       RabbitMQ ◀── (gs://bucket/output.png)
Spring BE ◀──(결과 수신 후 HTTPS URL로 변환)──────────────┘
```

### CI/CD: GitHub Actions + Docker Compose (Self-hosted)

서버 배포는 **GitHub Actions**와 **GCP VM의 Self-hosted Runner**를 연동해 완전 자동화했습니다.

```
git push main
     │
     ▼
GitHub Actions (Self-hosted Runner on GCP VM)
     │
     ├── 1. 최신 코드 체크아웃
     ├── 2. GitHub Secrets → .env 파일 생성 (민감 설정 주입)
     ├── 3. docker compose down (기존 서버 종료)
     ├── 4. docker compose up --build (새 이미지 빌드 + 실행)
     └── 5. 불필요한 이미지 prune (디스크 절약)
```

```yaml
# .github/workflows/deploy.yml
on:
  push:
    branches: ["main"]  # main 브랜치 푸시 시 자동 실행

jobs:
  deploy:
    runs-on: self-hosted  # GCP VM에 설치된 GitHub Runner
    steps:
      - uses: actions/checkout@v3
      - run: echo "${{ secrets.ENV_FILE }}" > .env   # 비밀값 주입
      - run: sudo docker compose -f ~/docker-compose.yml --project-directory . up -d --build
```

- **브랜치 전략**: `main` 브랜치에만 자동 배포를 트리거, 개발 중 우발적 배포를 방지합니다.
- **Secrets 관리**: DB 비밀번호, API Key 등 민감 정보는 GitHub Secrets에 저장 후 `.env` 파일로 주입해 코드와 완전히 분리합니다.
- **Docker Compose 오케스트레이션**: Spring BE, Python Worker, RabbitMQ, PostgreSQL, Redis 컨테이너를 단일 `docker-compose.yml`로 관리해 인프라를 코드화(IaC)합니다.

---

## 🛠️ 기술 스택 요약

| Category | Stack |
|---|---|
| Language | Java 21 (Virtual Threads 호환 구조) |
| Framework | Spring Boot 3.5, **Spring WebFlux** (Reactive, Non-blocking) |
| Database | PostgreSQL + **R2DBC** (Reactive JDBC) |
| Cache / Auth Store | **Redis** (ReactiveRedisTemplate) |
| Message Broker | **RabbitMQ** (AMQP, `spring-boot-starter-amqp`) |
| AI / LLM | **Spring AI** + Google Gemini API (Generative AI) |
| Security | Spring Security WebFlux + **JWT** (JJWT, HS256) |
| Cloud Storage | **Google Cloud Storage (GCS)** |
| Secret Management | **Google Cloud Secret Manager** (gRPC) |
| API Docs | **SpringDoc OpenAPI** (WebFlux 전용) |
| Build | Gradle, Docker |

---

## ⚡ 핵심 아키텍처: 완전 비동기 리액티브 파이프라인

### Spring WebFlux + R2DBC — 논블로킹 I/O

MVC의 블로킹 서블릿 방식 대신, **Project Reactor** (`Mono` / `Flux`) 기반 리액티브 스트림을 전 레이어에 적용했습니다.

- **HTTP 처리**: 전통적인 `@Controller` 대신 **Functional Router + Handler** 패턴 채택으로 파이프라인이 선언적이고 명확합니다.
- **DB 접근**: `spring-boot-starter-data-r2dbc`로 PostgreSQL과의 모든 쿼리를 비동기적으로 수행합니다. **스레드를 점유하지 않는** 리액티브 커넥션 풀을 사용합니다.
- **보안 필터**: `ReactiveAuthenticationManager` 기반의 인증 체인도 완전한 `Mono<Authentication>` 파이프라인으로 구성되어 블로킹이 없습니다.

```
Request
  └─ BearerTokenServerAuthenticationConverter (Mono<Authentication>)
       └─ JwtAuthenticationManager (Reactive, non-blocking)
            └─ RouterFunction → Handler → Service → R2DBC Repository → DB
```

### Functional Router / Handler 패턴

Spring MVC의 `@Controller` 어노테이션 방식 대신, **WebFlux의 함수형 라우팅** 방식을 채택했습니다.

```java
// AdRouter.java
RouterFunctions.route()
    .path("/api/v1/products/{id}", b -> b
        .GET("/ad-trends",   handler::getAdTrends)
        .POST("/ad-guides",  handler::createAdGuides)
        .POST("/ad-copies",  handler::createAdCopies)
        .POST("/contents",   handler::createAdProjectAndContents)
    )
    .build();
```

이 패턴은 라우트-핸들러를 분리하고, 핸들러를 단위 테스트하기 용이하게 만들며, 요청 흐름을 명시적으로 표현합니다.

---

## 🐇 비동기 AI 작업 파이프라인 (RabbitMQ)

무거운 AI 이미지 생성 작업은 HTTP 요청 내에서 직접 처리하지 않습니다. RabbitMQ 메시지 브로커를 통해 Python AI Worker와 비동기로 협력합니다.

```
[API Server]           [RabbitMQ]          [Python AI Worker]
  ┌─────────┐   Publish  ┌──────────┐  Consume  ┌────────────────┐
  │ Job     │ ─────────► │ jobs     │ ────────► │ Gemini / Diffusion│
  │ Service │            │ queue    │           └────────────────┘
  └─────────┘            └──────────┘                   │ Result
       ▲                 ┌──────────┐  Publish           │
       │  Consume        │ results  │ ◄──────────────────┘
       └─────────────── │ queue    │
                        └──────────┘
```

### 구현 포인트

1. **Job 상태 관리**: `PENDING → PROCESSING → SUCCEEDED / FAILED` 상태 머신을 DB로 관리합니다.
2. **RabbitMQ Consumer**: `@RabbitListener`로 결과 큐를 구독하고, 결과를 Job에 반영합니다. `AmqpRejectAndDontRequeueException`을 활용해 파싱 불가능한 메시지의 **무한 재큐 루프를 방지**합니다.
3. **Reactive 브릿지**: Consumer는 AMQP 스레드에서 동작하므로 `Schedulers.boundedElastic()`으로 Reactive 파이프라인을 안전하게 실행합니다.
4. **콜백 보안**: Worker에서 직접 HTTP로 결과를 올릴 때는 `X-Job-Secret` 헤더를 검증해 무단 접근을 차단합니다.

```java
// JobResultsConsumer.java
@RabbitListener(queues = "${app.jobs.result-queue}")
public void onMessage(String body) {
    ...
    jobService.applyResult(jobId, req)
        .subscribeOn(Schedulers.boundedElastic())
        .block(); // ACK 보장을 위해 결과 완료까지 블로킹
}
```

---

## 🗄️ DB 설계 & Transactional Outbox + Debezium CDC

### PostgreSQL (R2DBC)

Chillgram의 데이터베이스는 **PostgreSQL + R2DBC**로 모든 I/O를 비동기 처리합니다. 주요 테이블 구조는 다음과 같습니다.

| 테이블 | 역할 |
|---|---|
| `app_user` | 사용자 계정 (이메일, 역할, 회사 연결) |
| `company` | 광고주 기업 정보 |
| `product` | 광고 대상 상품 (이미지, 설명 등) |
| `project` | 광고 프로젝트 (상품 + 생성 결과 묶음) |
| `content` | AI 생성 광고 컨텐츠 (배너, 영상, SNS 등) |
| `content_asset` | 컨텐츠에 연결된 이미지 에셋 |
| `ad_gen_log` | AI 광고 생성 이력 로그 |
| `job_task` | AI 작업 요청 및 상태 추적 (FSM) |
| `outbox_event` | Transactional Outbox 이벤트 (Debezium CDC용) |
| `qa_category` / `qa_question` / `qa_answer` / `qa_question_attachment` | Q&A 시스템 |
| `social_account` | YouTube 등 소셜 계정 연동 |

---

### 핵심 테이블 스키마

#### `job_task` — AI 작업 상태 머신

```sql
CREATE TABLE job_task (
    job_id       UUID        PRIMARY KEY,
    project_id   BIGINT,
    job_type     TEXT,                -- BANNER | SNS | VIDEO | DIELINE | BASIC
    status       TEXT        NOT NULL, -- REQUESTED | RUNNING | SUCCEEDED | FAILED
    payload      JSONB,               -- 작업 입력 파라미터 (inputUrl, prompt 등)
    output_uri   TEXT,                -- AI 생성 결과 URL (HTTPS 정규화 완료)
    error_code   TEXT,
    error_message TEXT,
    requested_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ
);
```

> **멱등성 보장**: `SUCCEEDED / FAILED` 상태에 도달한 Job에 대한 결과 재반영은 자동으로 무시됩니다.

#### `outbox_event` — Transactional Outbox 테이블

```sql
CREATE TABLE outbox_event (
    id             UUID        PRIMARY KEY,
    aggregate_type TEXT,       -- 이벤트 발행 주체 (예: "JOB")
    aggregate_id   UUID,       -- 연결된 엔티티 ID (job_id 등)
    event_type     TEXT,       -- 이벤트 종류 (예: "JOB_REQUESTED")
    routing_key    TEXT,       -- RabbitMQ 라우팅 키 (Debezium이 읽어서 전달)
    payload        JSONB,      -- 메시지 본문 (jobId, jobType, inputUrl 등)
    created_at     TIMESTAMPTZ
);
```

---

### Transactional Outbox + Debezium CDC 패턴

RabbitMQ로 직접 메시지를 발행하면 **DB 저장과 메시지 발행 사이에 실패가 발생할 경우 이중 실패(데이터 불일치)** 가 생깁니다. 이를 해결하기 위해 **Transactional Outbox 패턴**과 **Debezium CDC**를 조합했습니다.

```
┌──────────────────────────────────────────────────────────┐
│  Spring AI BE — 하나의 DB 트랜잭션 안에서 동시에 저장      │
│                                                          │
│  INSERT job_task (status=REQUESTED)                      │
│  INSERT outbox_event (routing_key, payload)  ─── 커밋    │
└──────────────────────────────────────────────────────────┘
                    │
                    ▼ (Debezium이 outbox_event 테이블 변경을 감지)
┌──────────────────────────────────────────────────────────┐
│  Debezium (PostgreSQL WAL → Kafka/RabbitMQ CDC)         │
│  outbox_event 레코드를 읽어 routing_key로 RabbitMQ 발행  │
└──────────────────────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────┐
│  Python AI Worker                                        │
│  chillgram.jobs 큐 consume → AI 처리 → 결과 발행         │
└──────────────────────────────────────────────────────────┘
```

#### 왜 이 패턴이 중요한가?

| 문제 | 해결 방법 |
|---|---|
| DB 저장 성공 + MQ 발행 실패 → AI 작업 누락 | Outbox에 같은 트랜잭션으로 적어두면 CDC가 반드시 발행 |
| MQ 발행 성공 + DB 롤백 → 유령 메시지 | DB 트랜잭션이 커밋될 때만 Debezium이 CDC 이벤트 생성 |
| 중복 메시지 처리 | `job_task` 멱등 업데이트로 방어 (`SUCCEEDED/FAILED` 상태 무시) |

#### 코드 구현 포인트

```java
// JobService.java — 단일 TransactionalOperator로 원자적 처리
return tx.transactional(
    jobRepo.insertRequested(jobId, ...)          // job_task 행 삽입
        .then(outboxRepo.insertOutbox(outboxId, // outbox_event 행 삽입
            "JOB", jobId, "JOB_REQUESTED",
            jobsRoutingKey, eventPayload, now))
        .thenReturn(jobId)
);
```

> `TransactionalOperator`를 사용해 R2DBC의 **리액티브 트랜잭션** 내에서 두 테이블에 원자적으로 쓰기를 수행합니다. HTTP 응답에는 `jobId`를 즉시 반환하고, 실제 AI 작업은 Debezium → Worker로 위임됩니다.

---

### Outbox 이벤트 정리 스케줄러

처리된 `outbox_event` 레코드는 Debezium이 읽은 후 더 이상 필요 없으므로, **10분 주기**로 N분 이전의 레코드를 자동 삭제합니다.

```java
// OutboxCleanupScheduler.java
@Scheduled(fixedDelayString = "${app.outbox.cleanup-interval-ms:600000}")
public void cleanup() {
    outboxRepo.deleteOlderThanMinutes(retentionMinutes).subscribe();
}
```

- 보관 기간: `app.outbox.retention-minutes` (기본값 10분)
- 실행 주기: `app.outbox.cleanup-interval-ms` (기본값 10분)
- DB 비용 절감 및 테이블 비대화 방지에 중요한 역할을 합니다.

---

## ☁️ GCP 클라우드 인프라

전체 서비스는 **Google Cloud Platform(GCP) Compute Engine VM** 위에서 **Docker 컨테이너**로 운영됩니다. 각 서비스는 역할별 VM에 분리 배포되었으며, IAP·방화벽·내부 VPC 네트워크·Load Balancer·Cloud CDN·Cloud Armor를 활용한 **보안 계층형 인프라**로 구성됩니다.

---

### 전체 네트워크 토폴로지

```
                            Internet
                               │
                 ┌─────────────┴──────────────┐
                 │                            │
          HTTP :80 (Redirect)         HTTPS :443
                 │                            │
         ┌───────▼────────────────────────────▼───────┐
         │         chillgram-lb                        │
         │  Global External HTTP(S) Load Balancer      │
         │  IP: 34.111.100.160                         │
         │  ┌─────────────────────────────────────┐   │
         │  │ HTTP → HTTPS 자동 리다이렉트         │   │
         │  │ SSL 종료 (chillgram-cert)            │   │
         │  │ Cloud CDN 활성화                     │   │
         │  │ Cloud Armor 보안 정책                │   │
         │  └──────────────┬──────────────────────┘   │
         └─────────────────┼───────────────────────────┘
                           │
              ┌────────────▼────────────┐
              │ chillgram-front-group   │  Instance Group
              │ (chillgram-front-backend-service)
              └────────────┬────────────┘
                           │
          ┌────────────────┴────────────────────────────┐
          │               GCP VPC (us-west1-b)          │
          │  default subnet / 10.138.0.0/20             │
          │                                             │
          │  ┌────────────┐  ┌────────────┐             │
          │  │chillgram-  │  │spring-     │             │
          │  │front       │  │backend     │             │
          │  │10.138.0.5  │  │10.138.0.4  │             │
          │  │(외부IP:    │  │(외부IP없음)│             │
          │  │136.117.    │  │LB 뒤에 위치│             │
          │  │224.89)     │  └──────┬─────┘             │
          │  └────────────┘         │                   │
          │                 ┌───────▼────────┐          │
          │                 │chillgram-      │          │
          │                 │pythonworker    │          │
          │                 │10.138.0.7      │          │
          │                 │e2-highmem-2    │          │
          │                 │(16GB RAM)      │          │
          │                 └───────┬────────┘          │
          │                         │ RabbitMQ AMQP     │
          │                 ┌───────▼────────┐          │
          │                 │postgres        │          │
          │                 │10.138.0.2      │          │
          │                 │(외부IP없음/완전격리)      │
          │                 └────────────────┘          │
          └─────────────────────────────────────────────┘

  ※ IAP SSH: 공인 IP 없이 GCP IAP 터널로만 VM 관리 접속
```

---

### VM 인스턴스 전체 현황

| VM 이름 | 역할 | 머신 유형 | 내부 IP | 외부 IP | OS |
|---|---|---|---|---|---|
| `spring-backend` | Spring AI BE + Redis + RabbitMQ | e2-standard-2 (2vCPU / 8GB) | `10.138.0.4` | **없음** | Debian 12 |
| `postgres` | PostgreSQL + Debezium | e2-medium (2vCPU / 4GB) | `10.138.0.2` | **없음** 🔒 | Debian 12 |
| `chillgram-front` | Next.js 프론트엔드 | e2-micro (2vCPU / 1GB) | `10.138.0.5` | `136.117.224.89` | Debian 12 |
| `chillgram-pythonworker` | Python AI Worker | **e2-highmem-2** (2vCPU / **16GB**) | `10.138.0.7` | `34.105.65.201` (임시) | Ubuntu 22.04 |

---

### VM 인스턴스 상세

#### `spring-backend` — Spring AI BE 서버

| 항목 | 상세 |
|---|---|
| 위치 | `us-west1-b` |
| 머신 유형 | **e2-standard-2** (vCPU 2, 메모리 8GB) |
| 부팅 디스크 | Debian 12 Bookworm, 50GB Balanced PD, 일일 자동 백업 |
| 내부 IP | `10.138.0.4` |
| 외부 IP | **없음** (로드밸런서 뒤에 위치) |
| 인스턴스 그룹 | `instance-group-1` (**Unmanaged**, `spring-backend-service` 백엔드) |
| 네트워크 태그 | `chillgram`, `spring`, `http-server`, `https-server`, `iap-ssh` |
| 자동 재시작 | 사용 / 라이브 마이그레이션 |

**Docker Compose 스택:**
```yaml
services:
  spring-be:   # Spring WebFlux API 서버 (Java 21)
  redis:       # Refresh Token 저장소 (ReactiveRedisTemplate)
  rabbitmq:    # AI Job 비동기 메시지 브로커
```

---

#### `postgres` — PostgreSQL 데이터베이스 서버

| 항목 | 상세 |
|---|---|
| 위치 | `us-west1-b` |
| 머신 유형 | **e2-medium** (vCPU 2, 메모리 4GB) |
| 부팅 디스크 | Debian 12 Bookworm, 50GB Standard PD |
| 내부 IP | `10.138.0.2` |
| 외부 IP | **없음** (완전 격리, 내부 통신만 허용) |
| 네트워크 태그 | `http-server`, `https-server`, `iap-ssh`, `postgres-db` |
| Cloud API 범위 | 모든 Cloud API 전체 액세스 (Debezium WAL 연동) |

> **DB는 외부 인터넷으로부터 구조적으로 완전 차단**됩니다. Spring BE → PostgreSQL 통신은 GCP VPC 내부(`10.138.0.4 → 10.138.0.2`)로만 이루어집니다.

---

#### `chillgram-front` — Next.js 프론트엔드 서버

| 항목 | 상세 |
|---|---|
| 위치 | `us-west1-b` |
| 머신 유형 | **e2-micro** (vCPU 2, 메모리 1GB) |
| 부팅 디스크 | Debian 12 Bookworm, 10GB Balanced PD, 일일 자동 백업 |
| 내부 IP | `10.138.0.5` |
| 외부 IP (고정) | `136.117.224.89` (`chillgram-front`) |
| 인스턴스 그룹 | `chillgram-front-group` (**Unmanaged**, `chillgram-front-backend-service` 백엔드) |
| 네트워크 태그 | `chillgram-front`, `http-server`, `https-server` |
| 자동 재시작 | 사용 / 라이브 마이그레이션 |

> 프론트엔드는 고정 외부 IP(`136.117.224.89`)를 가지고 있으며, `chillgram-lb` Load Balancer를 통해 HTTPS 트래픽을 수신합니다.

---

#### `chillgram-pythonworker` — Python AI 워커 서버

| 항목 | 상세 |
|---|---|
| 위치 | `us-west1-b` |
| 머신 유형 | **e2-highmem-2** (vCPU 2, **메모리 16GB**) |
| 부팅 디스크 | **Ubuntu 22.04 LTS** (Jammy), 30GB Balanced PD |
| 내부 IP | `10.138.0.7` |
| 외부 IP | `34.105.65.201` (임시) |
| 네트워크 태그 | `http-server`, `https-server` |
| 자동 재시작 | 사용 / 라이브 마이그레이션 |

> AI 이미지 생성(Gemini Vision, Diffusion 등) 워크로드는 **메모리 집약적**이므로 High-Memory 머신 유형(16GB)을 선택했습니다. Python Worker는 RabbitMQ 큐를 구독하여 비동기로 이미지를 생성하고 결과를 GCS에 저장합니다.

---

### 🔒 보안 아키텍처

#### 1. IAP (Identity-Aware Proxy) — 외부 IP 없는 안전한 SSH

VM에 공인 IP를 부여하지 않고, **GCP IAP SSH 터널**을 통해서만 관리 접속합니다.

```bash
# IAP 터널을 통한 SSH 접속 (공인 IP 없이)
gcloud compute ssh spring-backend         --tunnel-through-iap --zone=us-west1-b
gcloud compute ssh postgres               --tunnel-through-iap --zone=us-west1-b
gcloud compute ssh chillgram-pythonworker --tunnel-through-iap --zone=us-west1-b
```

- 모든 보안 VM에 `iap-ssh` 네트워크 태그 적용
- SSH 포트(22)를 인터넷에 직접 노출하지 않음 → 포트 스캔·무차별 대입 공격 원천 차단
- GCP IAM 권한이 있는 계정만 터널 접속 가능

#### 2. 방화벽 규칙

| 규칙 | 적용 대상 | 목적 |
|---|---|---|
| `http-server` | 모든 VM | HTTP 80 허용 (LB 헬스체크) |
| `https-server` | 모든 VM | HTTPS 443 허용 |
| `iap-ssh` | spring-backend, postgres | IAP IP 대역(`35.235.240.0/20`)에서만 SSH 허용 |
| `postgres-db` | postgres VM | 내부 VPC에서만 PostgreSQL 5432 허용 |
| `chillgram` | spring-backend | 서비스 전용 커스텀 규칙 |
| `chillgram-front` | chillgram-front | 프론트엔드 전용 커스텀 규칙 |

> **postgres와 spring-backend VM은 외부 IP가 없으므로**, 방화벽 규칙과 무관하게 인터넷에서 직접 접근하는 것이 구조적으로 불가능합니다.

#### 3. VPC 내부 네트워크 통신

```
chillgram-front (10.138.0.5) ──[REST/HTTPS]──▶ spring-backend (10.138.0.4)
spring-backend  (10.138.0.4) ──[5432]───────▶ postgres        (10.138.0.2)
spring-backend  (10.138.0.4) ──[5672 AMQP]──▶ RabbitMQ (self: Spring VM)
pythonworker    (10.138.0.7) ──[5672 AMQP]──▶ RabbitMQ (spring-backend)
spring-backend  (10.138.0.4) ──[6379]───────▶ Redis    (self: Spring VM)
```

모든 서비스 간 통신은 **GCP VPC 내부 프라이빗 네트워크**를 통해 이루어집니다.

---

### ⚖️ 로드밸런싱 — `chillgram-lb`

GCP **전역 외부 애플리케이션 부하 분산기(Global External HTTP(S) LB)**를 프론트엔드 서비스에 적용했습니다.

```
사용자 브라우저
      │
      ├─ HTTP :80  ──▶ chillgram-lb-redirect (자동 HTTPS 리다이렉트)
      │                         │ 301 Redirect
      └─ HTTPS :443 ◀──────────┘
              │
              ▼
    chillgram-lb (IP: 34.111.100.160)
      ├── SSL 종료 (chillgram-cert 인증서)
      ├── Cloud CDN (정적 자산 캐싱 활성화)
      ├── Cloud Armor 보안 정책 (default-security-policy-for-chillgram-front)
      └── Backend → chillgram-front-backend-service
                        │
                        ▼
              chillgram-front-group (Instance Group)
                        │
                        ▼
              chillgram-front VM (10.138.0.5, :80)
                    Next.js 서버
```

#### Load Balancer 상세 구성

| 항목 | 값 |
|---|---|
| LB 유형 | 전역 외부 애플리케이션 부하 분산기 (L7) |
| 프론트엔드 IP | `34.111.100.160` |
| HTTPS 프로토콜 | `:443` / `chillgram-cert` SSL 인증서 |
| HTTP 리다이렉트 | `:80` → `:443` 자동 301 리다이렉트 (`chillgram-lb-redirect`) |
| 네트워크 등급 | 프리미엄 (Premium) |
| SSL 정책 | GCP 기본값 |
| 헬스체크 | `chillgram-healthcheck` |
| 백엔드 프로토콜 | HTTP (LB→VM 구간 HTTP, LB에서 SSL 종료) |
| 백엔드 최대 사용률 | 80% |
| 연결 유지 제한 시간 | 610초 |
| Cloud CDN | **활성화** (정적 자산 글로벌 캐싱) |
| Cloud Armor | **활성화** (default 보안 정책, DDoS·봇 방어) |
| 라우팅 | 모든 URL → `chillgram-front-backend-service` |

#### Cloud CDN
프론트엔드 정적 파일(JS, CSS, 이미지 등)을 **GCP 글로벌 엣지 노드에 캐싱**하여 전세계 사용자에게 빠른 응답을 제공합니다.

#### Cloud Armor (DDoS / 봇 방어)
백엔드 서비스에 **`default-security-policy-for-chillgram-front`** Cloud Armor 보안 정책을 적용해 비정상 트래픽을 LB 레벨에서 차단합니다.

---

### 📦 Docker 기반 컨테이너 운영

모든 서비스는 VM 위에서 **Docker + Docker Compose**로 패키징·배포됩니다.

| VM | 컨테이너 | OS |
|---|---|---|
| `spring-backend` | Spring WebFlux BE, Redis, RabbitMQ | Debian 12 |
| `chillgram-front` | Next.js 프론트엔드 (Docker) | Debian 12 |
| `chillgram-pythonworker` | Python AI Worker (Gemini/Diffusion) | **Ubuntu 22.04** |
| `postgres` | PostgreSQL + Debezium CDC | Debian 12 |

```dockerfile
# spring-backend Dockerfile
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew bootJar -x test          # 테스트 제외 빌드
CMD ["java", "-jar", "build/libs/chillgram-0.0.1-SNAPSHOT.jar"]
```

> **빌드가 Docker 안에서 실행**되므로 VM에 Java를 별도 설치할 필요 없이, 빌드 환경과 실행 환경이 완전히 동일합니다. GitHub Actions의 Self-hosted Runner가 VM에서 직접 `docker compose up --build`를 실행해 재배포합니다.

---

## 🚨 예외 처리 아키텍처

단순히 `@ExceptionHandler`를 쓰는 것이 아니라, **WebFlux 전용 예외 처리 체계**를 직접 설계했습니다.

### 예외 계층 구조

```
BusinessException (abstract)          ← 비즈니스 예외 최상위
    └── ApiException                  ← 코드에서 직접 throw하는 예외

WebFlux 예외
    ├── ResponseStatusException       ← Spring 프레임워크 발생
    ├── WebExchangeBindException      ← @Valid 유효성 검사 실패
    └── ErrorResponseException        ← 기타 프레임워크 예외
```

### ErrorCode Enum — HTTP 상태 중앙 관리

HTTP 상태 코드와 에러 메시지를 **`ErrorCode` enum 하나에서 중앙 관리**합니다. 분산된 상태코드 하드코딩을 방지하고, 일관된 에러 응답 포맷을 보장합니다.

```java
public enum ErrorCode {
    // 400 — 입력 오류
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),

    // 401/403 — 인증/인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    ACCOUNT_UNVERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    ACCOUNT_DORMANT(HttpStatus.FORBIDDEN, "휴면 계정입니다."),

    // 404/409 — 리소스
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),

    // 502/504 — 외부 연동
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "외부 시스템 오류입니다."),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 시스템 응답이 지연되었습니다."),
    AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버 호출에 실패했습니다."),

    // 500 — 서버 내부
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류입니다.");
    // ...총 30+ 에러코드
}
```

### GlobalErrorWebExceptionHandler

Spring WebFlux에는 `@ControllerAdvice`가 동작하지 않습니다. 대신 `ErrorWebExceptionHandler`를 구현하여 **WebFlux 전체 파이프라인의 예외를 단일 지점에서 처리**합니다.

```java
@Component
@Order(-2)  // Spring Boot 기본 핸들러(-1)보다 먼저 처리
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 1. 예외 타입에 따라 ApiErrorResponse 구성
        ApiErrorResponse body = toErrorResponse(exchange.getRequest(), ex);

        // 2. 5xx는 스택 트레이스 포함 error 레벨 로깅
        //    4xx는 스택 없이 warn 레벨 로깅 (보안 정보 과다 노출 방지)
        logByStatus(body, ex);

        // 3. JSON 직렬화 후 응답 기록
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
```

#### 예외 타입별 처리 전략

| 예외 타입 | 처리 방식 | HTTP 상태 |
|---|---|---|
| `WebExchangeBindException` | 필드별 유효성 오류를 `fieldErrors`로 상세 반환 | 400 |
| `BusinessException` (ApiException) | `ErrorCode`에서 상태/메시지 결정 | ErrorCode 기준 |
| `ResponseStatusException` | Spring 프레임워크 예외 처리 | 원래 상태코드 |
| 기타 모든 예외 | 500 Fallback, 스택 트레이스 포함 로깅 | 500 |

#### 로그 레벨 분리 전략

```java
private void logByStatus(ApiErrorResponse body, Throwable ex) {
    if (body.status() >= 500) {
        // 5xx — 스택 트레이스 포함 (버그 추적)
        log.error("Unhandled error: {} {} traceId={} code={}", ..., ex);
    } else {
        // 4xx — 스택 없이 warn만 (클라이언트 오류, 보안 정보 노출 최소화)
        log.warn("Client error: {} {} traceId={} code={}", ...);
    }
}
```

> **설계 원칙**: 4xx 클라이언트 에러에 스택 트레이스를 남기면 노이즈가 되고, 민감 정보가 노출될 수 있습니다. 5xx 서버 에러만 스택을 기록해 의미 있는 경고 신호를 유지합니다.

#### 에러 응답 포맷

```json
{
  "status": 404,
  "code": "JOB_NOT_FOUND",
  "message": "작업을 찾을 수 없습니다.",
  "path": "/api/v1/jobs/abc-123",
  "method": "GET",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "details": {}
}
```

| 필드 | 설명 |
|---|---|
| `code` | `ErrorCode` enum 이름 (클라이언트 기계 판별용) |
| `message` | 사용자 표시용 메시지 (한국어) |
| `traceId` | 요청 추적 ID (로그와 매핑) |
| `details` | 유효성 오류 시 `fieldErrors` 포함 |

---

## 📋 요청 로깅 & 감사(Audit) 로깅 시스템

**Spring WebFlux의 비동기·논블로킹 특성**에 맞춰 모든 로깅 컴포넌트를 리액티브 방식으로 구현했습니다. `WebFilter`를 기반으로 HTTP 메타데이터 로그와 감사 로그를 분리하여 기록합니다.

### 로깅 파이프라인 구조

```
HTTP 요청
   │
   ▼
RequestLoggingWebFilter (WebFilter)
   ├── TraceIdResolver       → X-Request-Id 헤더 우선, 없으면 UUID 생성
   ├── RequestClassifier     → 로깅 제외 경로 판단 (actuator, static 등)
   ├── RequestBodyCaptor     → 요청 바디 캡처 (스트림 재구독 문제 해결)
   │
   ├── chain.filter(decorated)  ← 실제 요청 처리
   │
   └── doFinally { 요청 완료 후 }
          ├── LogEmitter.emitHttp()   → HTTP 메타 로그
          └── AuditHandlerResolver   → @AuditLog 여부 확인
                 └── LogEmitter.emitAudit()  → 감사 로그
```

### 컴포넌트별 역할

| 컴포넌트 | 역할 |
|---|---|
| `TraceIdResolver` | `X-Request-Id` 헤더 우선 사용, 없으면 UUID 생성. B3, W3C traceparent도 지원 |
| `RequestClassifier` | actuator, static 파일 등 로깅 불필요 경로 판별 |
| `RequestBodyCaptor` | WebFlux 스트림 특성상 body를 한 번밖에 읽을 수 없어 캡처 |
| `RequestBodyCapturePolicy` | 대용량 파일 업로드/바이너리 등 바디 캡처 제외 정책 |
| `LogSanitizer` | 요청 바디에서 `password`, `token`, `accessToken`, `refreshToken` 마스킹 |
| `AuditHandlerResolver` | 핸들러 메서드의 `@AuditLog` 어노테이션 검사 |
| `LogEmitter` | 실제 `log.info()` 출력 전담 (단일 책임) |

### HTTP 로그 포맷

```
http traceId=550e8400 GET /api/v1/jobs/abc-123 status=200 latencyMs=42
```

모든 HTTP 요청에 대해 **메서드, 경로, 상태코드, 응답 시간, traceId**를 구조화된 형태로 기록합니다.

### 감사(Audit) 로그 — `@AuditLog` 어노테이션

보안·컴플라이언스 목적으로 **특정 API의 누가, 언제, 무엇을 했는지** 별도로 기록합니다.

```java
// 핸들러 메서드에 @AuditLog를 달면 자동으로 감사 로그 기록
@AuditLog("광고 카피 생성")
public Mono<ServerResponse> createAdCopy(ServerRequest request) { ... }
```

```
audit traceId=550e8400 feature=광고카피생성 user=42 handler=createAdCopy 
      status=200 latencyMs=1240 pathVars={projectId=7} queryParams={} 
      reqBody={"tone":"친근함","keywords":["여름","할인"],"password":"***"}
```

> `LogSanitizer`가 `password`, `token` 등 민감 필드를 `"***"`으로 마스킹하여 **감사 로그에 비밀번호가 남지 않도록** 보호합니다.

### TraceId 분산 추적 지원

`TraceIdResolver`는 다음 우선순위로 traceId를 결정합니다:

```
1순위: X-Request-Id 헤더 (클라이언트 또는 Load Balancer가 전달)
2순위: X-B3-TraceId 헤더 (Zipkin B3 포맷)
3순위: traceparent 헤더 (W3C 표준 포맷)
4순위: UUID 자동 생성
```

동일한 traceId가 **HTTP 로그 → 감사 로그 → 에러 응답 JSON → GlobalErrorWebExceptionHandler 로그** 전체에 일관되게 포함되어 로그 추적이 가능합니다.

---

## 📧 이메일 인증 — SendGrid + Redis 1회성 토큰

회원가입 후 이메일 인증을 강제하여 **미인증 계정의 API 접근을 차단**합니다. 인증 메일 발송은 SendGrid API를 통해 처리하며, 토큰 관리는 Redis를 사용합니다.

### 전체 이메일 인증 플로우

```
회원가입 요청
     │
     ▼
1. PENDING 상태로 계정 생성 (이메일 미인증)
     │
     ▼
2. EmailVerificationTokenService.issue()
   - UUID × 2 조합으로 raw token 생성
   - SHA-256(raw token) → Redis key로 저장 (TTL 적용)
   - verifyUrl = baseUrl + URLEncoded(rawToken)
     │
     ▼
3. SendGridMailSender.sendVerificationMail(email, verifyUrl)
   - WebClient로 SendGrid /mail/send API 비동기 호출
   - HTML 인증 링크 이메일 발송 (202 Accepted)
     │
     ▼
4. 사용자가 이메일 링크 클릭
     │
     ▼
5. EmailVerificationTokenService.consume(rawToken)
   - Lua 스크립트로 Redis GET + DEL 원자적 실행 (재사용 방지)
   - userId 조회 성공 → 계정 VERIFIED 상태로 업데이트
```

### SendGrid 연동 — `WebClient` 기반 Reactive HTTP

`SendGridMailSender`는 `WebClient`를 통해 SendGrid REST API를 **완전 비동기**로 호출합니다.

```java
// SendGridMailSender.java — WebClient로 SendGrid API 호출
@Component
public class SendGridMailSender implements MailSenderPort {

    private final WebClient client = builder
        .baseUrl("https://api.sendgrid.com/v3")
        .defaultHeader(AUTHORIZATION, "Bearer " + props.apiKey())
        .build();

    @Override
    public Mono<Void> sendVerificationMail(String toEmail, String verifyUrl) {
        Map<String, Object> payload = Map.of(
            "personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail)))),
            "from", Map.of("email", props.fromEmail(), "name", props.fromName()),
            "subject", "[Chillgram] 이메일 인증을 완료해주세요",
            "content", List.of(Map.of("type", "text/html", "value", html))
        );

        return client.post().uri("/mail/send")
            .contentType(APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()  // SendGrid 성공 응답: 202 Accepted
            .then();
    }
}
```

- **`MailSenderPort` 인터페이스**: 메일 발송 구현체를 인터페이스로 분리해, 추후 SendGrid → SES 등 다른 제공자로 교체 시 **Spring Bean만 변경**하면 됩니다.
- **응답 202**: SendGrid는 메일 발송 요청 수락 시 `202 Accepted`를 반환합니다. `toBodilessEntity()`로 바디 없이 상태만 확인합니다.

### 이메일 인증 토큰 — Redis + SHA-256 + Lua 원자적 소비

```java
// EmailVerificationTokenService.java

// 1. 토큰 발급: raw token은 이메일에만, Redis에는 해시만 저장
public Mono<IssuedToken> issue(Long userId) {
    String raw = UUID.randomUUID() + "-" + UUID.randomUUID();  // 64자 랜덤
    String key = sha256Hex(raw);  // Redis key = 해시값 (원문 저장 금지)
    String verifyUrl = baseUrl + URLEncoder.encode(raw, UTF_8);

    return redis.opsForValue()
        .set(key, String.valueOf(userId), EMAIL_TOKEN_TTL);  // TTL 적용
}

// 2. 토큰 소비: Lua로 GET + DEL을 원자적으로 실행 (레이스 컨디션 방지)
public Mono<Long> consume(String rawToken) {
    String key = sha256Hex(rawToken);
    RedisScript<String> getAndDel = RedisScript.of(
        "local v = redis.call('GET', KEYS[1]); " +
        "if v then redis.call('DEL', KEYS[1]); end; return v;",
        String.class
    );
    return redis.execute(getAndDel, List.of(key)).next();
}
```

| 보안 설계 포인트 | 내용 |
|---|---|
| **원문 토큰 비저장** | Redis에는 `SHA-256(rawToken)` 해시만 저장. Redis 유출 시 원문 복원 불가 |
| **1회성 토큰** | Lua 스크립트로 GET + DEL을 원자적으로 실행 → 중복 인증 클릭 레이스 방지 |
| **TTL 자동 만료** | Redis TTL로 인증 링크 유효 기간 자동 관리 |
| **URL 인코딩** | 이메일 링크의 raw token에 `URLEncoder`를 적용해 특수문자 안전 전달 |

---

## 🤖 AI 통합: Spring AI + Google Gemini

Spring AI의 `ChatClient` 추상화를 활용해 **Google Gemini API**와 통합했습니다.

- **광고 카피 생성**: 상품명, 톤앤매너, 타겟 정보를 기반으로 Gemini에게 광고 카피와 배너 프롬프트를 자동 생성시킵니다.
- **광고 트렌드 분석**: LLM을 통해 시장 트렌드를 분석하고 인사이트를 제공합니다.
- **광고 가이드라인 생성**: 브랜드 가이드에 맞는 광고 전략을 AI가 생성합니다.
- **Spring AI BOM 관리**: `spring-ai-bom:1.1.2`를 통해 AI 라이브러리 버전을 일원화하여 의존성 충돌을 방지합니다.

---

## 🔐 보안: JWT + Redis를 활용한 토큰 인증 시스템

단순한 JWT 발급/검증을 넘어, **보안성과 Reactive 환경을 동시에 고려한** 인증 시스템을 직접 구현했습니다.

### Dual-Token 전략 (Access + Refresh)

| 항목 | Access Token | Refresh Token |
|---|---|---|
| 알고리즘 | HS256 (HMAC-SHA256) | HS256 |
| 저장 위치 | 클라이언트 메모리 | Redis |
| TTL | 짧음 (분 단위) | 김 (일~주 단위) |
| 용도 | API 인증 | Access Token 재발급 |

### Redis를 활용한 Refresh Token 보안 강화

Refresh Token 원문을 Redis에 직접 저장하지 않고 **SHA-256 해시로 변환하여 저장**함으로써, Redis가 탈취되더라도 원본 토큰을 복원할 수 없도록 방어합니다.

```java
// RefreshTokenStore.java — Redis에 해시만 저장
public Mono<Void> saveHashed(long userId, String refreshTokenRaw) {
    String hash = AuthConst.sha256Hex(refreshTokenRaw);          // 해시 변환
    return redis.opsForValue()
        .set("rt:" + userId, hash, Duration.ofSeconds(props.refreshTtlSeconds()))
        .then();
}
```

- **ReactiveRedisTemplate** 사용: Redis I/O도 Reactive 파이프라인에 자연스럽게 통합됩니다.
- **로그아웃/강제 만료**: `redis.delete("rt:{userId}")`로 즉시 강제 만료 처리가 가능합니다.

### Reactive Security Pipeline

```java
// BearerTokenServerAuthenticationConverter
Authorization 헤더 파싱 → "Bearer <token>" 추출 → Mono<Authentication>
       ↓
// JwtAuthenticationManager
JWT 파싱(서명/만료/형식 검증) → typ=access 검증 → AuthPrincipal 생성
       ↓
Spring Security SecurityContext 바인딩
```

- `typ` 클레임 검증으로 **Refresh Token을 API 인증에 사용하는 것을 원천 차단**합니다.
- JWT 검증 실패의 원인(`ExpiredJwtException`, `JwtException` 등)은 모두 `UNAUTHORIZED`로 통일해 **보안 정보 과다 노출을 방지**합니다.

---

## ☁️ GCS 기반 하이브리드 파일 스토리지

AI Worker와 API 서버가 같은 GCS 버킷을 공유하지만, **통신 목적에 따라 URI 포맷을 분리**하는 전략을 택했습니다.

| 방향 | URI 형식 | 이유 |
|---|---|---|
| API → AI Worker | `gs://bucket/object` | 비공개 GCS 버킷 직접 접근 |
| API → Frontend | `https://storage.googleapis.com/...` | 브라우저 랜더링 가능한 공개 URL |

```java
// GcsFileStorage.java — gs:// 를 HTTPS로 자동 변환
public String toPublicUrl(String uri) {
    if (uri.startsWith("gs://")) {
        // 버킷명 검증 후 publicBaseUrl + object 경로로 변환
        return publicBaseUrl + "/" + objectPath;
    }
    return uri; // 이미 https면 pass-through
}
```

- **파일 업로드**: `FilePart`를 임시 파일로 받아 GCS에 스트리밍 업로드, `Schedulers.boundedElastic()`으로 블로킹 I/O를 Reactive 파이프라인 밖으로 격리합니다.
- **파일 삭제**: `Mono<Void>` 반환으로 비동기 삭제를 지원합니다.

---

## 🔑 Google Cloud Secret Manager

API Key, DB credentials 등 민감한 설정값을 **소스코드나 환경변수에 직접 노출시키지 않고**, GCP Secret Manager에서 런타임에 안전하게 주입합니다. gRPC 기반의 Secret Manager 클라이언트와 Spring Netty를 연동하여 사용합니다.

---

## 📋 요청 감사 로깅 시스템

단순한 콘솔 출력이 아닌, **운영 수준의 감사(Audit) 로깅 시스템**을 직접 설계·구현했습니다.

- **`RequestLoggingWebFilter`**: 최우선 순위(`HIGHEST_PRECEDENCE + 10`)로 등록된 단일 WebFilter가 모든 요청의 `traceId`, 메서드, 경로, 상태 코드, 응답 지연(ms)을 기록합니다.
- **TraceId 전파**: 인입 요청에 Trace ID를 부여하거나 업스트림에서 전달된 ID를 수신·MDC에 등록해 **분산 추적**을 지원합니다.
- **`@AuditLog` 기반 선택적 감사**: 특정 핸들러에 `@AuditLog` 어노테이션을 붙이면 path variables, query params, 요청 body(마스킹 처리)까지 추가로 기록합니다.
- **`LogSanitizer`**: 비밀번호, 토큰 등 민감 필드를 로그에서 자동으로 마스킹합니다.

---

## 📂 패키지 구조

```
com.example.chillgram
├── common/
│   ├── config/         # RabbitMQ, Redis, Security 설정
│   ├── exception/      # 글로벌 예외 처리 (ApiException, ErrorCode)
│   ├── google/         # GCS FileStorage (Reactive 래퍼)
│   ├── logging/        # 감사 로그 시스템 (TraceId, AuditLog, LogSanitizer)
│   ├── mail/           # 이메일 인증 토큰 서비스
│   └── security/       # JWT 인증 파이프라인 (Reactive)
├── domain/
│   ├── advertising/    # 광고 프로젝트 / 컨텐츠 / 트렌드 / 가이드
│   ├── ai/             # AI 작업 (JobService, Spring AI 통합)
│   ├── auth/           # 로그인 / 회원가입 / 토큰 재발급
│   ├── company/        # 기업 정보
│   ├── content/        # 생성된 광고 컨텐츠
│   ├── product/        # 상품 정보
│   └── qa/             # Q&A
```

---

## 🚀 로컬 실행

```bash
# 1. 환경변수 설정
export GEMINI_API_KEY=<your-gemini-api-key>
export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/chillgram
export SPRING_REDIS_HOST=localhost

# 2. 실행
./gradlew bootRun

# 3. API 문서 확인
open http://localhost:8080/swagger-ui.html
```

---

## 🐳 Docker

```bash
docker build -t chillgram-ai-be .
docker run -p 8080:8080 \
  -e GEMINI_API_KEY=... \
  -e SPRING_R2DBC_URL=... \
  chillgram-ai-be
```

---

## 📌 주요 기술 선택 근거 요약

| 기술 결정 | 이유 |
|---|---|
| WebFlux (Reactive) | 다수의 AI API / GCS / DB 동시 I/O를 스레드 낭비 없이 처리 |
| R2DBC | 리액티브 파이프라인 내에서 DB 작업을 완전 비동기로 처리 |
| RabbitMQ | 무거운 AI 이미지 생성을 HTTP 타임아웃 없이 비동기 위임 |
| Redis (ReactiveRedisTemplate) | Refresh Token의 비동기 저장/검증, Reactive 흐름 유지 |
| JWT typ 클레임 | Refresh Token의 API 인증 남용 원천 차단 |
| GCS gs:// vs https:// 분리 | 내부 AI 통신은 비공개 접근, 프론트는 공개 URL 사용 |
| Secret Manager | 민감 환경변수를 소스 코드/이미지에서 완전 분리 |
| 단일 WebFilter 로깅 | 필터 순서 꼬임·중복 로그 없이 일관된 감사 추적 보장 |

---

## 📁 패키지 구조 (도메인 중심 설계)

기능이 아닌 **비즈니스 도메인 단위**로 패키지를 분리하여, 각 도메인이 독립적인 계층 구조(`router → handler → service → repository`)를 갖습니다.

```
com.example.chillgram
├── common/                        # 전역 공통 (도메인 무관)
│   ├── config/                    # R2dbcConfig, SecurityConfig, RedisConfig, ...
│   ├── exception/                 # ErrorCode, GlobalErrorWebExceptionHandler, ...
│   ├── logging/                   # RequestLoggingWebFilter, AuditLog, LogEmitter, ...
│   ├── security/                  # JwtTokenService, RefreshTokenStore, ...
│   ├── google/                    # GcsFileStorage, FileStorage (인터페이스)
│   └── mail/                      # SendGridMailSender, EmailVerificationTokenService
│
└── domain/                        # 비즈니스 도메인
    ├── ai/                        # AI Job (이미지 생성 작업, Outbox, RabbitMQ)
    │   ├── router/ handler/ service/ repository/ messaging/ controller/
    ├── advertising/               # 광고 트렌드·가이드·카피 생성
    │   ├── router/ handler/ service/ repository/ engine/ dto/
    ├── auth/                      # 로그인·회원가입·이메일 인증
    ├── user/                      # 사용자 프로필
    ├── company/                   # 브랜드/기업 관리
    ├── product/                   # 광고 대상 상품
    ├── project/                   # 광고 프로젝트
    ├── content/                   # 생성된 콘텐츠·에셋
    ├── qa/                        # Q&A (고객 문의)
    └── social/                    # SNS 계정 연동 (YouTube 등)
```

| 설계 원칙 | 내용 |
|---|---|
| **도메인 응집도** | 같은 도메인의 Router·Handler·Service·Repository를 한 패키지 안에 |
| **공통 관심사 분리** | 인증·로깅·예외처리 등 횡단 관심사는 `common/`으로 격리 |
| **의존 방향** | `domain` → `common` 단방향 의존. 도메인 간 직접 의존 최소화 |

---

## 🛣️ Functional Router / Handler 패턴

Spring MVC의 `@RestController` + `@GetMapping` 대신, **WebFlux 권장 방식**인 `RouterFunction` + `HandlerFunction` 패턴으로 API를 정의합니다.

### 왜 Functional Router를 사용하는가?

| 비교 항목 | `@RestController` | Functional Router / Handler |
|---|---|---|
| 라우팅 선언 | 메서드 어노테이션 (`@GetMapping`) | Java 코드 (`route()` 빌더) |
| 테스트 | `@WebMvcTest` | `RouterFunction` 단위 테스트 용이 |
| 필터 적용 | `@WebFilter` 또는 AOP | `.filter()` 체이닝으로 명시적 적용 |
| WebFlux 친화성 | 지원하지만 권장 아님 | WebFlux 공식 권장 방식 |

### 실제 구현 — `AdRouter` + `AdHandler`

```java
// AdRouter.java — 라우팅만 담당 (경로 선언)
@Configuration
public class AdRouter {
    @Bean
    public RouterFunction<ServerResponse> adRoutes(AdHandler adHandler) {
        return route()
            .path("/api/advertising", builder -> builder
                .route(POST("/{id}/ad-trends"),  adHandler::getAdTrends)
                .route(POST("/{id}/ad-guides"),  adHandler::createAdGuides)
                .route(POST("/{id}/ad-copies"),  adHandler::createAdCopies)
                .route(POST("/{id}/ads"),         adHandler::createAdProjectAndContents)
                .route(POST("/{id}/log"),          adHandler::createAdLog))
            .build();
    }
}
```

```java
// AdHandler.java — 요청 처리만 담당 (비즈니스 로직은 Service로 위임)
@Component
public class AdHandler {

    public Mono<ServerResponse> getAdTrends(ServerRequest request) {
        long productId = Long.parseLong(request.pathVariable("id"));

        return request.bodyToMono(AdTrendsRequest.class)
            .flatMap(req -> adService.getAdTrends(productId, req.baseDate()))
            .flatMap(resp -> ServerResponse.ok()
                .contentType(APPLICATION_JSON)
                .bodyValue(resp));
    }
}
```

> **`ServerRequest` / `ServerResponse`** 를 직접 다루므로, 헤더·쿠키·파트 등 HTTP 스펙 전체를 세밀하게 제어할 수 있습니다.

### 혼용 구조 — 도메인에 따라 `@RestController`도 병행

일부 도메인(`product`, `project`, `social`)은 상황에 따라 `@RestController`를 사용합니다. 두 방식은 Spring WebFlux 내에서 **공존**이 가능하며 SecurityConfig에서 동일하게 보호됩니다.

```
Functional Router 사용: ai/, advertising/, content/, qa/
@RestController 사용:   product/, project/, social/, auth/
```

---

## 🗃️ R2DBC + DatabaseClient — Raw SQL & JSONB 처리

Spring Data R2DBC의 `ReactiveCrudRepository`로 해결하기 어려운 복잡한 쿼리(상태 전이 UPDATE, JSONB 타입 삽입)는 **`DatabaseClient`로 Raw SQL을 직접 작성**합니다.

### 왜 `DatabaseClient`를 쓰는가?

| 상황 | 이유 |
|---|---|
| **JSONB 컬럼 삽입** | `cast(:payload as jsonb)` 캐스팅이 필요 — JPA/Spring Data로 자동 처리 불가 |
| **상태 조건부 UPDATE** | `WHERE status IN ('REQUESTED', 'RUNNING')` — 동시성 안전한 상태 전이 |
| **부분 응답 매핑** | 특정 컬럼만 선택적으로 DTO에 매핑 |

### `JobTaskRepository` — JSONB 삽입 실제 코드

```java
@Repository
public class JobTaskRepository {

    private final DatabaseClient db;
    private final ObjectMapper om;

    // 1. JSONB 컬럼에 JsonNode 삽입 — cast(:payload as jsonb) 필수
    public Mono<Void> insertRequested(UUID jobId, long projectId,
                                      JobType jobType, JsonNode payload,
                                      OffsetDateTime now) {
        return db.sql("""
                insert into job_task(job_id, project_id, job_type, status, payload, requested_at, updated_at)
                values (:jobId, :projectId, :jobType, :status, cast(:payload as jsonb), :now, :now)
                """)
            .bind("jobId",    jobId)
            .bind("jobType",  jobType.name())
            .bind("status",   JobStatus.REQUESTED.name())
            .bind("payload",  payload.toString())   // JsonNode → String → cast to jsonb
            .bind("now",      now)
            .fetch().rowsUpdated().then();
    }

    // 2. 조건부 상태 전이 UPDATE — 동시에 두 요청이 와도 한 번만 성공
    public Mono<Long> markSucceeded(UUID jobId, String outputUri, OffsetDateTime now) {
        return db.sql("""
                update job_task
                set status = :succeeded, output_uri = :outputUri, updated_at = :now
                where job_id = :jobId and status in (:requested, :running)
                """)
            .bind("succeeded", JobStatus.SUCCEEDED.name())
            .bind("requested", JobStatus.REQUESTED.name())
            .bind("running",   JobStatus.RUNNING.name())
            // ...
            .fetch().rowsUpdated();  // 영향받은 행 수로 성공 여부 판별
    }

    // 3. JSONB 읽기 — String으로 꺼내 ObjectMapper로 역직렬화
    public Mono<JobResponse> findById(UUID jobId) {
        return db.sql("select * from job_task where job_id = :jobId")
            .bind("jobId", jobId)
            .map((row, meta) -> {
                String payloadStr = row.get("payload", String.class);
                JsonNode payload = om.readTree(payloadStr);  // String → JsonNode
                return new JobResponse(..., payload, ...);
            })
            .one();
    }
}
```

> `cast(:payload as jsonb)` 없이 String을 직접 바인딩하면 PostgreSQL이 `text` 타입으로 인식해 **타입 불일치 오류**가 발생합니다.

---

## ⚡ TransactionalOperator — 리액티브 트랜잭션

WebFlux + R2DBC 환경에서 `@Transactional`은 **동작은 하지만 리액티브 파이프라인을 단절**시킬 수 있습니다. 이 프로젝트에서는 `TransactionalOperator`로 **명시적으로 트랜잭션 범위를 지정**합니다.

### Bean 설정 — `R2dbcConfig`

```java
@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    @Bean
    public R2dbcTransactionManager r2dbcTransactionManager(ConnectionFactory cf) {
        return new R2dbcTransactionManager(cf);
    }

    @Bean
    public TransactionalOperator transactionalOperator(R2dbcTransactionManager tm) {
        return TransactionalOperator.create(tm);  // 프로젝트 전역 TX 연산자
    }
}
```

### 실제 사용 — `JobService` (job_task + outbox_event 원자적 삽입)

```java
@Service
public class JobService {

    private final TransactionalOperator tx;

    public Mono<UUID> requestJob(long projectId, JobType type, JsonNode payload) {
        UUID jobId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        return tx.transactional(
            // 두 INSERT가 하나의 트랜잭션으로 묶임
            jobTaskRepository.insertRequested(jobId, projectId, type, payload, now)
                .then(outboxEventRepository.insert(jobId, type, payload, now))
        ).thenReturn(jobId);
    }
}
```

### `@Transactional` vs `TransactionalOperator`

| | `@Transactional` | `TransactionalOperator` |
|---|---|---|
| 동작 방식 | AOP 프록시 | 명시적 연산자 |
| Reactive 안전성 | `subscribe()` 경계에서 TX 누락 가능 | Mono/Flux 체인 전체를 하나의 TX로 보장 |
| 테스트 용이성 | 통합 테스트 필요 | 단위 테스트에서 mock 가능 |
| 사용 위치 | 메서드 레벨 | 파이프라인 내 명시적 범위 지정 |

> 이 프로젝트에서 **`job_task` 삽입 + `outbox_event` 삽입**은 반드시 하나의 트랜잭션이어야 합니다. 둘 중 하나라도 실패하면 전체 롤백되어 Debezium이 불완전한 이벤트를 발행하지 않습니다.

---

## 🔑 ReactiveSecurityContext & R2DBC Auditing

### ReactiveSecurityContextHolder — Reactor Context 기반 인증 정보 전파

일반 Spring MVC는 `SecurityContextHolder` (ThreadLocal)로 인증 정보를 전파하지만, WebFlux에서는 스레드가 바뀌기 때문에 **Reactor `Context`를 통해** 인증 정보를 전파합니다.

```java
// 핸들러에서 현재 인증된 사용자 ID 꺼내기
public Mono<ServerResponse> createAdProjectAndContents(ServerRequest request) {
    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> (AuthPrincipal) ctx.getAuthentication().getPrincipal())
        .flatMap(principal -> {
            long userId = principal.userId();
            long companyId = principal.companyId();
            // ... 비즈니스 로직
        });
}
```

> `ReactiveSecurityContextHolder.getContext()`는 **Reactor `Context` 체인을 통해 전파**됩니다. `Mono.subscriberContext()` 없이 `flatMap` 체인 어디서나 꺼낼 수 있습니다.

### R2DBC Auditing — `created_by` 자동 입력

`@EnableR2dbcAuditing`을 활성화하면, `@CreatedBy` 어노테이션이 붙은 컬럼에 **현재 로그인한 사용자 ID를 자동으로 삽입**합니다.

```java
// R2dbcConfig.java
@Bean
public ReactiveAuditorAware<Long> auditorAware() {
    return () -> ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(p -> p instanceof Long)
        .map(p -> (Long) p);  // userId를 created_by로 자동 주입
}
```

```java
// Entity에서 사용
@CreatedBy
private Long createdBy;  // INSERT 시 ReactiveAuditorAware에서 자동 채움

@CreatedDate
private OffsetDateTime createdAt;  // INSERT 시 현재 시각 자동 채움
```

| 기능 | 어노테이션 | 자동 채워지는 값 |
|---|---|---|
| 생성자 ID | `@CreatedBy` | `ReactiveAuditorAware`가 반환한 userId |
| 생성 시각 | `@CreatedDate` | `OffsetDateTime.now()` |
| 수정자 ID | `@LastModifiedBy` | 최근 수정한 userId |
| 수정 시각 | `@LastModifiedDate` | 최근 수정 시각 |

> `ReactiveAuditorAware`가 `ReactiveSecurityContextHolder`와 연결되어 있어, **서비스 코드에서 userId를 수동으로 넘기지 않고도** DB에 `created_by`가 자동으로 기록됩니다.

---

## 🔌 YouTube OAuth 2.0 + GCP Secret Manager 토큰 보안

YouTube 채널 연동 기능을 **OAuth 2.0 Authorization Code Flow**로 구현하되, 토큰 저장을 **GCP Secret Manager**에 위임하여 DB·Redis에 민감 토큰이 남지 않도록 설계했습니다.

### OAuth 인증 플로우

```
사용자 (YouTube 연결 요청)
     │
     ▼
1. YoutubeOAuthService.issueAuthUrl()
   - UUID state 생성 → Redis에 (state → companyId) TTL 저장
   - Google OAuth 인증 URL 생성 (access_type=offline, prompt=consent)
     │
     ▼
2. 사용자가 Google 로그인 → 동의 → redirect_uri로 code + state 전달
     │
     ▼
3. YoutubeOAuthService.exchange(companyId, code, state)
   - Redis에서 state 소비 (GET + DELETE) → CSRF 방어
   - GoogleOAuthClient.exchangeCode(code) → access/refresh 토큰 획득
     │
     ▼
4. YouTube Data API (/youtube/v3/channels?mine=true) 호출
   - 채널명, 통계 등 메타데이터 수집
     │
     ▼
5. YoutubeTokenStore.save(companyId, payload)
   - GCP Secret Manager에 "yt-company-{id}" Secret 생성/버전 추가
   - access_token + refresh_token + expiresAt을 JSON으로 암호화 저장
     │
     ▼
6. social_account 테이블에 tokenRef(Secret 경로)만 저장
   - DB에 토큰 원문이 남지 않음
```

### 토큰 자동 갱신 메커니즘

```java
// YoutubeOAuthService.java — 만료 2분 전에 자동 갱신
private Mono<String> ensureValidToken(SocialAccount acc) {
    return tokenStore.readLatest(acc.getTokenRef())
        .flatMap(payload -> {
            // 만료 여유 시간(2분) 이전이면 그대로 사용
            if (Instant.now().isBefore(payload.expiresAt().minus(TOKEN_SKEW))) {
                return Mono.just(payload.accessToken());
            }
            // 만료 임박 → refresh_token으로 갱신
            return oauth.refresh(payload.refreshToken())
                .flatMap(refreshed -> tokenStore.save(companyId, newPayload)
                    .thenReturn(refreshed.accessToken()));
        });
}
```

| 보안 설계 포인트 | 내용 |
|---|---|
| **DB에 토큰 비저장** | `social_account` 테이블에는 Secret Manager 경로(`tokenRef`)만 저장. 토큰 원문은 GCP Secret Manager에만 존재 |
| **OAuth State CSRF 방어** | Redis에 (state → companyId) TTL 저장 후 1회성 소비로 CSRF 공격 차단 |
| **토큰 자동 갱신** | `TOKEN_SKEW(2분)` 여유를 두어 만료 직전에 자동으로 `refresh_token` → `access_token` 갱신 |
| **refresh_token 복원** | Google이 갱신 응답에 `refresh_token`을 포함하지 않을 경우, Secret Manager 기존 버전에서 복원 |

---

## 🧩 Port/Adapter (Hexagonal Architecture) 패턴

인프라 구현체를 **인터페이스(Port)** 뒤에 숨겨, 비즈니스 로직이 특정 기술에 직접 의존하지 않도록 설계했습니다. 구현체 교체 시 **Bean만 변경**하면 서비스 코드는 수정 없이 동작합니다.

### 적용된 Port/Adapter

| Port (인터페이스) | Adapter (구현체) | 역할 | 교체 시나리오 |
|---|---|---|---|
| `FileStorage` | `GcsFileStorage` | 파일 업로드/삭제/URL 변환 | GCS → S3, MinIO 등 |
| `MailSenderPort` | `SendGridMailSender` | 인증 메일 발송 | SendGrid → AWS SES, SMTP 등 |
| `TrendRuleEngine` | `DefaultTrendRuleEngine` | 광고 트렌드 키워드/해시태그 분석 | 규칙 기반 → ML 기반 엔진 등 |

```java
// FileStorage.java — 파일 저장소 Port
public interface FileStorage {
    Mono<StoredFile> store(FilePart filePart);
    Mono<StoredFile> store(FilePart filePart, String folder);
    Mono<Void> delete(String uri);

    record StoredFile(String fileUrl, String mimeType, String gsUri, Long fileSize) {}
}

// MailSenderPort.java — 메일 발송 Port
public interface MailSenderPort {
    Mono<Void> sendVerificationMail(String toEmail, String verifyUrl);
}

// TrendRuleEngine.java — 트렌드 분석 Port
public interface TrendRuleEngine {
    TrendResult analyze(long productId, LocalDate baseDate, List<EventRow> events);
}
```

> **설계 원칙**: 서비스 계층은 `FileStorage`, `MailSenderPort` 같은 추상 인터페이스에만 의존하고, 실제 GCS·SendGrid 구현은 Spring Bean 주입으로 연결됩니다. 테스트 시에는 Mock 구현체를 주입하여 외부 의존 없이 단위 테스트가 가능합니다.

---

## 📊 Strategy Pattern — 트렌드 분석 규칙 엔진

광고 트렌드 키워드·해시태그 생성 로직을 **Strategy 패턴**으로 분리하여, 서비스 계층은 "흐름(조회/조립)"만 담당하고 실제 분석 로직은 엔진에 위임합니다.

```java
// TrendRuleEngine.java — 전략 인터페이스
public interface TrendRuleEngine {
    TrendResult analyze(long productId, LocalDate baseDate, List<EventRow> events);

    record TrendResult(List<TrendKeyword> trendKeywords, List<String> hashtags, String styleSummary) {}
    record TrendKeyword(String name, String description) {}
}

// DefaultTrendRuleEngine.java — 이벤트 캘린더 기반 규칙 엔진
@Component
public class DefaultTrendRuleEngine implements TrendRuleEngine {
    @Override
    public TrendResult analyze(long productId, LocalDate baseDate, List<EventRow> events) {
        // 설/추석 → "선물", "프리미엄" 키워드
        // 크리스마스/연말 → "파티" 키워드
        // 어린이날 → "키즈" 키워드
        // 새해 → "건강" 키워드
        // 기본 폴백: 건강/친환경/프리미엄
    }
}
```

> 향후 ML 기반 트렌드 엔진이 필요하면 `TrendRuleEngine` 인터페이스의 **새 구현체를 Bean으로 등록**하면 됩니다. 기존 서비스 코드는 변경 없이 새 엔진으로 전환됩니다.

---

## 🎨 2-Step AI Prompt Engineering 파이프라인

광고 생성은 **단일 프롬프트가 아닌 2단계 파이프라인**으로 설계되어, 사용자가 중간 결과를 검토·선택한 후 최종 결과를 생성합니다.

### Flow A: 가이드라인 → 최종 카피

```
[1단계] generateAdGuides()
   - 입력: 제품명, 트렌드, 리뷰, focus(0~4), target(0~4)
   - 출력: 5개 광고 가이드라인 (제목, 요약, 배지, 점수, 톤/구조/CTA)
   - 파싱: [GUIDE_START]...[GUIDE_END] 마커 → 정규식 파싱
         │
         ▼ (사용자가 가이드라인 1개 선택)
         │
[2단계] generateFinalCopies(selectedGuideline)
   - 입력: 선택된 가이드라인 JSON
   - 출력: 5개 최종 광고 카피 (제목 + 본문)
   - 파싱: [COPY_START]...[COPY_END] 마커 → 정규식 파싱
```

### Flow B: 비주얼 가이드 → 카피 베리에이션

```
[1단계] generateVisualGuidesMono()
   - 입력: 제품명, 트렌드, 리뷰, focus(0~4)
   - 출력: 5개 비주얼 가이드 (제품/장소/효과/재질/스타일)
   - 파싱: [OPTION N] 마커 → 정규식 파싱
         │
         ▼ (사용자가 비주얼 가이드 1개 선택)
         │
[2단계] generateCopyVariationsMono(selectedOption, target)
   - 입력: 선택된 비주얼 가이드 + 광고 목표(0~4)
   - 출력: 5개 임팩트 카피 (10자 이내 강렬한 문구)
   - 파싱: [COPY N] 마커 → 정규식 파싱
```

### Focus/Target 가중치 시스템

프롬프트에 **트렌드 vs 리뷰 비중(focus)**과 **광고 목표 톤(target)**을 동적으로 주입하여 LLM 출력을 제어합니다.

| focus 값 | 트렌드 비중 | 리뷰 비중 | 설명 |
|---|---|---|---|
| 0 | 100% | 0% | 트렌드 중심 |
| 1 | 80% | 20% | 트렌드 우선 |
| 2 | 50% | 50% | 균형 |
| 3 | 20% | 80% | 제품 우선 |
| 4 | 0% | 100% | 제품 특징 중심 |

| target 값 | 광고 목표 | CTA 방향 |
|---|---|---|
| 0 | 인지 (Awareness) | 브랜드/제품 인지도 확산 |
| 1 | 공감 (Empathy) | 감성적 연결, 스토리텔링 |
| 2 | 보상 (Benefit) | 혜택/할인 강조 |
| 3 | 참여 (Engagement) | 이벤트/댓글/공유 유도 |
| 4 | 행동 (Conversion) | 즉시 구매 유도, 긴급성 |

### Structured Output Parsing

LLM 응답에서 **커스텀 마커 태그(`[GUIDE_START]`, `[COPY_START]`, `[OPTION N]`)**를 정의하고, 정규식으로 파싱하여 구조화된 DTO로 변환합니다. JSON 출력을 강제하는 대신 마커 기반 파싱을 선택한 이유:

- LLM이 JSON 포맷을 깨뜨리는 경우가 잦음 (이스케이프 누락, trailing comma 등)
- 마커 방식은 **부분 파싱 실패에도 나머지 블록을 살릴 수 있어** 더 견고함
- Fallback 로직으로 파싱 실패 시에도 raw 텍스트를 반환하여 서비스 중단을 방지

---

## 🏭 DomainExceptionFactory — Reactor 친화적 예외 팩토리

Reactive 파이프라인에서 예외를 던질 때 `throw`와 `Mono.error()` 두 가지 방식이 필요합니다. `DomainExceptionFactory`는 **동일한 예외를 두 형태로 제공**하여 사용 맥락에 따라 선택할 수 있도록 합니다.

```java
public final class DomainExceptionFactory {

    // ✅ throw 용 — 일반 메서드에서 사용
    public static BusinessException notFoundEx(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.NOT_FOUND, message, details);
    }

    // ✅ Reactor 체인 용 — flatMap/switchIfEmpty 등에서 사용
    public static <T> Mono<T> notFound(String message, Map<String, Object> details) {
        return Mono.error(notFoundEx(message, details));
    }
}
```

```java
// 사용 예시
return repository.findById(id)
    .switchIfEmpty(DomainExceptionFactory.notFound(  // Mono.error() 반환
        "상품을 찾을 수 없습니다.",
        Map.of("productId", id)
    ));
```

| 메서드 | 반환 타입 | 사용 위치 |
|---|---|---|
| `notFoundEx()` | `BusinessException` | 일반 Java 메서드 (throw) |
| `notFound()` | `Mono<T>` | Reactor 체인 (switchIfEmpty, flatMap) |
| `conflictEx()` / `conflict()` | 동일 구조 | 409 Conflict |
| `forbiddenEx()` / `forbidden()` | 동일 구조 | 403 Forbidden |
| `invalidRequestEx()` / `invalidRequest()` | 동일 구조 | 400 Bad Request |

> **설계 의도**: WebFlux 환경에서 `Mono.error(new XxxException(...))`를 매번 작성하면 코드가 장황해집니다. 팩토리로 추상화하면 **예외 생성 규칙을 한 곳에서 관리**하고, 사용부는 간결해집니다.

---

## 🔀 블로킹 I/O 격리 패턴 — `Schedulers.boundedElastic()`

WebFlux의 이벤트 루프 스레드에서 블로킹 호출을 하면 **전체 서버가 멈출 수 있습니다.** 이 프로젝트에서는 불가피한 블로킹 I/O를 `Schedulers.boundedElastic()`으로 **전용 스레드 풀에 격리**합니다.

### 격리 대상과 이유

| 블로킹 I/O | 격리 방식 | 이유 |
|---|---|---|
| **Spring AI (Gemini API 호출)** | `Mono.fromCallable(() -> chatClient.prompt()...call()).subscribeOn(Schedulers.boundedElastic())` | Spring AI의 `ChatClient.call()`이 동기 블로킹 |
| **GCP Secret Manager** | `Mono.fromCallable(() -> saveBlocking(...)).subscribeOn(Schedulers.boundedElastic())` | gRPC 클라이언트가 동기 블로킹 |
| **GCS 파일 업로드** | `Mono.fromCallable(() -> storage.create(...)).subscribeOn(Schedulers.boundedElastic())` | GCS SDK의 `Storage.create()`가 동기 블로킹 |
| **RabbitMQ Consumer** | `jobService.applyResult(...).subscribeOn(Schedulers.boundedElastic()).block()` | AMQP 스레드에서 Reactive 파이프라인 실행 |

```java
// AdCopyService.java — AI 호출을 boundedElastic으로 격리
public Mono<AdGuidesResponse> generateAdGuidesMono(AdGuideAiRequest request) {
    return Mono.fromCallable(() -> generateAdGuides(request))  // 블로킹 AI 호출
        .subscribeOn(Schedulers.boundedElastic());              // 전용 스레드 풀에서 실행
}

// YoutubeTokenStore.java — Secret Manager 호출을 격리
public Mono<String> save(long companyId, OAuthTokenPayload payload) {
    return Mono.fromCallable(() -> saveBlocking(companyId, payload))
        .subscribeOn(Schedulers.boundedElastic());
}
```

```
이벤트 루프 스레드 (Netty)         boundedElastic 스레드 풀
─────────────────────────         ──────────────────────────
  HTTP 요청 수신                     │
  ├─ JWT 검증 (non-blocking)         │
  ├─ R2DBC 조회 (non-blocking)       │
  ├─ subscribeOn() ──────────────▶  AI API 호출 (blocking)
  │   (스레드 전환)                   Secret Manager (blocking)
  │                                  GCS 업로드 (blocking)
  ◀─────────────────────────────── 결과 반환
  └─ 응답 전송 (non-blocking)
```

> **핵심 원칙**: Netty 이벤트 루프는 **절대 블로킹하지 않는다.** 블로킹이 불가피한 외부 SDK 호출은 `Schedulers.boundedElastic()`으로 전환하여 이벤트 루프를 보호합니다.
