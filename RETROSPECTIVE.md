# NEXUS 프로젝트 회고

> 작성일: 2026-03-26
> 프로젝트: [stock-api](https://github.com/teriyakki-jin/stock-api) · [stock-frontend](https://github.com/teriyakki-jin/stock-frontend) · [rag-report](https://github.com/teriyakki-jin/investment-report-rag)

---

## 무엇을 만들었나

Bloomberg Terminal 스타일 모의주식거래 플랫폼 + 증권사 리포트 기반 AI 분석 시스템.

| 레포 | 기술 | 역할 |
|------|------|------|
| `stock-api` | Spring Boot 4.0.3, Java 17, PostgreSQL 16, Redis 7 | 주문/계좌/실시간 시세/소셜 REST API |
| `stock-frontend` | React 18, TypeScript, Vite, Tailwind, Recharts | Bloomberg 테마 SPA |
| `rag-report` | Python 3.11, FastAPI, GPT-4o, Qdrant, BM25 | 하이브리드 RAG 챗봇 |

---

## 타임라인

```
Phase 1  — 백엔드 뼈대 (도메인 설계, 회원/계좌/주문)
Phase 2  — 프론트엔드 초기 구현 (Bloomberg 테마, 거래 UI)
Phase 3  — 테스트 + CI 구축 (GitHub Actions, H2 in-memory)
Phase 4  — 보안 강화 (JWT 블랙리스트, 비관적 락)
Phase 5  — GBM 시뮬레이션 (장외 시간 가격 모델링)
Phase 6  — 포트폴리오 고도화 (PnL 곡선, MDD, 섹터 비중)
Phase 7  — 소셜 기능 (Supabase — 팔로우/랭킹/프로필)
Phase 8  — KIS OpenAPI 실시간 시세 (Yahoo Finance 폴백)
Phase 9  — RAG 파이프라인 (네이버 금융 크롤링 → Qdrant → GPT-4o)
Phase 10 — 가격 알림 (목표주가 도달 시 실시간 알림)
           + Docker 컨테이너화 + 보안 수정
```

---

## 잘 된 것들

### 1. 도메인 모델이 Phase 10까지 버텼다

`Member → Account → Holding → Order` 의 4계층 구조가 Phase 1에서 설계됐는데, Phase 10에서 `PriceAlert`를 `Account`에 붙이는 것도 자연스러웠다. 엔티티에 도메인 로직을 응집시킨 패턴 (`Account.withdraw()`, `PriceAlert.checkAndTrigger()`) 덕분에 서비스 레이어가 얇게 유지됐다.

### 2. 비관적 락으로 동시성을 정확하게 잡았다

10개 스레드가 동시에 매수 주문을 넣어도 잔액이 정확히 차감됨을 `CountDownLatch`로 검증했다. `PESSIMISTIC_WRITE` + `@Transactional` 조합이 select-for-update를 깔끔하게 구현했다.

### 3. GBM 시뮬레이션이 실제처럼 작동했다

장외 시간에도 거래가 가능하도록 1년치 실데이터 기반으로 σ(연간 변동성)·μ(드리프트)를 자동 추정하고, Cholesky 분해로 종목 간 상관관계를 보존했다. 시간대별 변동성 배율(장 시작 1.8×, 마감 1.4×, 점심 0.8×)도 실제 시장 패턴과 유사하게 나왔다.

### 4. Supabase 이중 DataSource 패턴이 깔끔했다

로컬 PostgreSQL(계좌/주문) + Supabase(프로필/소셜/랭킹) 를 Primary/Secondary DataSource로 분리해서 각 도메인에 맞는 저장소를 선택했다. Spring의 `@Primary` + 별도 `EntityManagerFactory`로 JPA와 JDBC 두 경로를 공존시켰다.

### 5. 하이브리드 RAG 파이프라인이 의도대로 동작했다

BM25(키워드) + Semantic(벡터) 검색을 RRF(Reciprocal Rank Fusion)로 결합하고, Cross-Encoder Reranker로 재정렬하는 3단계 파이프라인이 투자 리포트 특성(숫자·종목명·섹터 혼재)에 잘 맞았다.

### 6. TanStack Query v5로 서버 상태를 깔끔하게 관리했다

낙관적 업데이트(`onMutate`), 쿼리 무효화(`invalidateQueries`), 자동 폴링(`refetchInterval`)을 조합해서 알림 시스템·실시간 시세·포트폴리오 데이터를 별도 전역 상태 없이 관리했다.

---

## 삽질한 것들

### 1. CI 3연속 실패 — 각각 다른 원인

**Round 1 — `NoSuchBeanDefinitionException`**

`SupabaseClient`가 `@Profile` 없이 빈으로 등록됐는데, 의존하는 `supabaseJdbc` DataSource가 `@Profile("!test")`였다. 의존 체인 (`SupabaseClient → SocialController → ProfileController → RankingService → RankingScheduler`)의 모든 빈에 `@Profile("!test")` 추가.

**Round 2 — `ScriptStatementFailedException`**

H2가 기본적으로 `data.sql`을 Hibernate DDL보다 먼저 실행하려 했다. 테이블이 없는 상태에서 INSERT → 에러. `defer-datasource-initialization: true` + `sql.init.mode: never` 로 해결.

**Round 3 — `WeakKeyException at Keys.java:83`**

`SupabaseJwtVerifier`가 `@Profile` 없이 모든 환경에서 로드되는데, test 프로파일의 `supabase.jwt-secret` 기본값 `"placeholder"`가 Base64 디코딩 시 8바이트(64비트) — JJWT 최소 요구 256비트 미달. test 프로파일에 46바이트짜리 placeholder 추가.

**교훈**: CI는 로컬과 미묘하게 다르다. `@Profile` 누락, `data.sql` 실행 순서, 환경변수 기본값 — 세 가지 모두 로컬에선 문제없던 것들이었다.

---

### 2. IDOR 취약점을 뒤늦게 발견했다

`PriceAlertController`에서 `accountId`를 경로 파라미터로 받으면서 로그인한 유저의 계좌인지 검증하지 않았다. 타인의 `accountId`를 직접 입력하면 다른 사람의 알림을 조회·삭제할 수 있었다.

**원인**: Phase 10 구현 시 `AccountController`의 `validateOwner()` 패턴을 참고하지 않고 독립적으로 작성했다.

**해결**: 모든 엔드포인트에 `@AuthenticationPrincipal Member member` 주입 + 서비스 레이어에서 `account.getMember().getId().equals(member.getId())` 검증.

**교훈**: 신규 도메인 컨트롤러 작성 시 기존 소유권 검증 패턴을 체크리스트로 확인해야 한다. 코드 리뷰 자동화(security-reviewer)를 커밋 전에 반드시 실행하자.

---

### 3. `@Lazy` 없이 순환 의존성이 생겼다

`StockPricePublisher`가 `PriceAlertService`를 주입받고, `PriceAlertService`가 `AccountRepository`를 주입받는 과정에서 스프링 컨텍스트 초기화 순환이 발생했다.

**해결**: `StockPricePublisher`의 `PriceAlertService` 주입에 `@Lazy` 적용. 첫 호출 시점에 프록시를 통해 실제 빈을 주입받도록 지연.

**교훈**: 이벤트 발행자(Publisher)가 특정 도메인 서비스를 직접 의존하는 구조는 순환 위험이 있다. Spring Application Event나 별도 리스너 구조가 더 깔끔할 수 있다.

---

### 4. PDF 추출에서 표와 텍스트 혼용이 까다로웠다

증권사 리포트는 같은 페이지에 텍스트, 표, 차트 이미지가 섞인다. pdfplumber만 쓰면 표 외 텍스트가 누락되고, PyMuPDF만 쓰면 표 구조가 깨진다.

**해결**: PyMuPDF로 전체 텍스트 추출 → pdfplumber로 표만 추출 → 페이지별로 병합 후 중복 제거.

**교훈**: PDF 라이브러리는 각각 강점이 다르다. 두 라이브러리를 조합하는 게 복잡하지만 결과 품질이 확연히 달랐다.

---

### 5. BM25와 Semantic 검색의 점수 스케일 차이

RRF(Reciprocal Rank Fusion) 이전에 단순 가중 합산을 시도했다. BM25 점수(0~수십)와 Cosine 유사도(0~1)의 스케일 차이로 항상 BM25가 지배했다.

**해결**: RRF — 각 검색 결과의 순위(rank)만 사용해 `1/(k + rank)` 합산. 점수 절댓값 무관하게 순위 기반으로 결합.

**교훈**: 이종 검색 시스템 결합은 점수 정규화보다 순위 기반 융합이 훨씬 안정적이다.

---

## 기술적으로 배운 것

| 주제 | 내용 |
|------|------|
| Spring `@Profile` | Bean과 Configuration 모두 프로파일 제어 필요. 의존 체인 전체를 추적해야 함 |
| H2 + PostgreSQL 호환 | `MODE=PostgreSQL` + `defer-datasource-initialization` + `sql.init.mode: never` 3개 세트 |
| JJWT 0.12.x 키 요구사항 | `Keys.hmacShaKeyFor()` 최소 256비트. Base64 디코딩 후 바이트 수 기준 |
| GBM 시뮬레이션 | `dS = μS·dt + σS·dW`, dt=1s, σ는 1년 수익률 표준편차에서 연환산 |
| Cholesky 분해 | 상관행렬 → 하삼각행렬 분해 → 독립 노이즈에 곱해 상관 노이즈 생성 |
| RRF Fusion | `score = Σ 1/(k + rank_i)`, k=60이 경험적 최적값 |
| Cross-Encoder Reranker | Bi-encoder(속도) → Cross-encoder(정확도) 2단계 파이프라인 |
| FastAPI + uvicorn | Python 비동기 API 서버. `async def` 엔드포인트로 GPT-4o 호출 대기 최소화 |
| IDOR 방어 | 경로 파라미터 ID는 항상 인증된 사용자 소유 여부를 서비스 레이어에서 검증 |
| Nginx SPA proxy | `try_files $uri /index.html` + location별 upstream 프록시 |

---

## 수치로 보는 결과물

| 항목 | 수치 |
|------|------|
| API 엔드포인트 수 | 30+ |
| 도메인 모듈 수 | 10 (account, alert, member, order, portfolio, profile, ranking, social, stock, KIS) |
| 프론트엔드 페이지 수 | 6 |
| RAG 지원 페르소나 | 3 (공격형 / 중립형 / 안정형) |
| CI 파이프라인 | GitHub Actions (Java 17 + Gradle + H2) |
| Docker 서비스 수 | 5 (stock-api, frontend, rag-report, postgres, redis, qdrant) |
| 외부 API 연동 수 | 4 (KIS OpenAPI, Yahoo Finance, OpenAI GPT-4o, Supabase) |

---

## 아쉬운 점 / 다음에는

**테스트 커버리지가 아직 낮다**
`PriceAlertService`, `PortfolioService`, `HybridSearch` 단위 테스트가 없다. Controller 레이어 `@WebMvcTest`도 미작성. 다음엔 기능 구현과 테스트를 동시에.

**이벤트 기반 아키텍처 도입이 필요하다**
`StockPricePublisher`가 `PriceAlertService`를 직접 호출하면서 `@Lazy` 패치가 필요했다. Spring Application Event 또는 Kafka 토픽으로 완전히 분리했으면 의존성 문제가 없었을 것.

**RAG 평가 지표를 실측해야 한다**
RAGAS 목표값을 설정했지만 (`faithfulness ≥ 0.85`, `answer_relevancy ≥ 0.80`) 실제 리포트 데이터로 측정하지 않았다. 평가 데이터셋 구축 → runner.py 실행 → 지표 개선 루프가 필요하다.

**환경변수 관리가 분산됐다**
3개 레포에 `.env.example`이 따로 있다. 실제 배포 시 HashiCorp Vault나 AWS Secrets Manager로 중앙화하는 게 낫다.

**프론트 에러 바운더리가 없다**
API 실패 시 토스트 알림은 있지만 React Error Boundary가 없어서 컴포넌트 에러가 전체 화면 붕괴로 이어질 수 있다.

---

## 한 줄 요약

> 3개 레포, 10개 Phase, 30+ 엔드포인트 — 설계는 처음부터 끝까지 버텼다.
> CI 실패 3연속, IDOR 취약점, 순환 의존성 — 실수는 반드시 기록에 남긴다.
