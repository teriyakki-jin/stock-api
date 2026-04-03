# NEXUS 프로젝트 기술 경험 정리

> 기간: 2026-03-16 ~ 2026-03-26 (10일)
> 역할: 풀스택 (백엔드 / 프론트엔드 / AI 파이프라인)

---

## 프로젝트 개요

Bloomberg Terminal 스타일 모의주식거래 플랫폼 + 증권사 리포트 기반 AI 분석 시스템.
3개 레포지토리, 10개 Phase, 30+ REST 엔드포인트를 10일 안에 설계·구현·배포.

---

## 기술 스택별 경험

### Spring Boot (Java 17)

**도메인 설계**
- `Member → Account → Holding → Order → PriceAlert` 4계층 도메인 모델
- 엔티티에 도메인 로직 응집 (`Account.withdraw()`, `PriceAlert.checkAndTrigger()`)
- `@Profile("!test")` 을 사용한 환경별 빈 분리 전략

**동시성 제어**
- `@Lock(PESSIMISTIC_WRITE)` + `@Transactional` 로 select-for-update 구현
- 10개 스레드 동시 매수 주문 → 잔액 정확성 `CountDownLatch`로 검증

**인증/보안**
- JWT Access(30분) + Refresh(7일) 이중 토큰 구조
- Redis 기반 Access Token 블랙리스트 (로그아웃 처리)
- IDOR 취약점 수정: `@AuthenticationPrincipal` + 서비스 레이어 소유권 검증

**데이터 레이어**
- Primary DataSource (PostgreSQL/H2) + Secondary DataSource (Supabase JDBC) 이중 구성
- Flyway V1 마이그레이션으로 DDL 버전 관리 도입
- Spring Data Auditing (`@CreatedDate`, `@LastModifiedDate`) 공통 엔티티 적용

**실시간 데이터**
- STOMP WebSocket으로 시세 브로드캐스트
- KIS OpenAPI → Yahoo Finance → GBM 시뮬레이션 3단 폴백 체인
- SSE(Server-Sent Events)로 체결 알림 스트리밍

**테스트**
- Mockito + AssertJ 단위 테스트 (PriceAlertService, PortfolioService, OrderService)
- `ReflectionTestUtils`로 JPA `@Id` 필드 주입
- H2 인메모리 DB + `@ActiveProfiles("test")` CI 환경 구성

---

### React / TypeScript

**상태 관리**
- TanStack Query v5: 서버 상태(캐싱·폴링·낙관적 업데이트)와 로컬 상태 완전 분리
- Zustand: 인증 상태 전역 관리 + localStorage persist

**실시간 UI**
- STOMP(`@stomp/stompjs`)로 WebSocket 시세 구독 → 가격 변동 애니메이션
- `usePendingAlerts` 훅: 10초 폴링 + 자동 acknowledge + 토스트 콜백

**입력 검증**
- Zod 스키마로 폼 입력 런타임 검증 (`alertSchema.safeParse()`)
- 에러 메시지 컴포넌트 수준에서 인라인 표시

**차트/시각화**
- Recharts로 수익률 곡선, 섹터 파이차트, 볼린저밴드 오버레이 구현
- RSI 게이지, MACD 패널 커스텀 컴포넌트

**테스트 (Vitest + RTL)**
- jsdom 환경에서 React 컴포넌트 렌더링 테스트
- `vi.mock`으로 API 레이어 모킹
- `renderHook` + `waitFor`로 비동기 커스텀 훅 테스트
- 18개 테스트 작성 (PriceAlertPanel 9, usePendingAlerts 6, ragApi 3)

---

### Python / FastAPI

**RAG 파이프라인**
- BM25(키워드) + Semantic(벡터) 하이브리드 검색 구현
- RRF(Reciprocal Rank Fusion)로 이종 검색 결과 융합: `score = Σ 1/(k + rank_i)`
- Cross-Encoder Reranker로 최종 재정렬 (2단계 파이프라인)

**LLM 통합**
- GPT-4o + LangChain 기반 RAG 체인
- 공격형/중립형/안정형 3개 투자 페르소나별 system prompt 설계
- 할루시네이션 방지 지시문 + 법적 면책 문구 자동 삽입

**PDF 처리**
- PyMuPDF(텍스트) + pdfplumber(표) 조합으로 증권사 리포트 파싱
- 페이지별 청크 분할 + 메타데이터(ticker, date, analyst) 추출

**API 설계**
- FastAPI + Pydantic 모델 기반 입력 검증
- API Key 헤더 인증 (`X-API-Key`): 환경변수 미설정 시 개발 모드 자동 전환
- CORS 미들웨어 설정

---

### DevOps / 인프라

**컨테이너화**
- Docker 멀티스테이지 빌드 (Spring Boot JAR, React+Nginx, Python uvicorn)
- docker-compose로 6개 서비스 오케스트레이션 (API, frontend, RAG, PostgreSQL, Redis, Qdrant)

**CI/CD**
- GitHub Actions: JDK 17 + Gradle + Redis 서비스 컨테이너
- CI 실패 3회 → 원인 분석 → 수정 패턴 확립

**외부 서비스 연동**
- KIS OpenAPI (한국투자증권) 모의투자 토큰 발급 + 실시간 시세
- Supabase: OAuth 인증, PostgreSQL JDBC, 프로필/소셜 데이터
- OpenAI GPT-4o API
- Yahoo Finance (yfinance): 1년 히스토리 + 종목 정보

---

## 문제 해결 사례

| 문제 | 원인 | 해결 |
|------|------|------|
| CI `NoSuchBeanDefinitionException` | `@Profile` 누락으로 test 환경서 Supabase 빈 로드 시도 | 의존 체인 6개 빈 전체에 `@Profile("!test")` 추가 |
| CI H2 `ScriptStatementFailedException` | `data.sql`이 Hibernate DDL보다 먼저 실행 | `defer-datasource-initialization: true` + `sql.init.mode: never` |
| CI `WeakKeyException` | JJWT 0.12.x는 256비트 최소 요구, `"placeholder"` Base64 = 64비트 | test 프로파일에 46바이트 더미 시크릿 추가 |
| IDOR 취약점 | `accountId` 경로 파라미터 소유권 미검증 | `@AuthenticationPrincipal` + 서비스 레이어 `validateOwnership()` |
| 순환 의존성 | `StockPricePublisher → PriceAlertService` 순환 | `@Lazy` 지연 주입으로 프록시 경유 |
| 하이브리드 검색 점수 스케일 불일치 | BM25(0~수십) vs Cosine(0~1) 스케일 차이로 BM25 지배 | RRF로 전환: 점수 대신 순위 기반 융합 |
| Vitest/Vite 버전 충돌 | Vitest 4.x는 Vite 6.x 필요, 프로젝트는 Vite 5 | Vitest `2.1.9`로 다운그레이드 |

---

## 수치로 본 결과

| 항목 | 수치 |
|------|------|
| 개발 기간 | 10일 |
| 레포지토리 | 3개 |
| 커밋 수 (합산) | 60+ |
| REST 엔드포인트 | 30+ |
| Java 소스 라인 | 5,800+ |
| TypeScript 소스 라인 | 3,700+ |
| Python 소스 라인 | 2,400+ |
| 테스트 수 (합산) | Java 23 + TypeScript 18 |
| 외부 API 연동 | 4개 (KIS, Yahoo Finance, OpenAI, Supabase) |
| Docker 서비스 | 6개 |

---

## 핵심 인사이트

**설계 단계에 투자한 시간이 후반에 회수된다**
Phase 1에서 도메인 모델을 꼼꼼히 설계한 덕분에 Phase 10에서 `PriceAlert`를 추가할 때 기존 구조를 전혀 뜯지 않았다. 빠른 구현보다 도메인 경계가 먼저다.

**CI는 day 1 작업이다**
로컬에서 완벽하던 코드가 CI에서 3연속으로 다른 이유로 실패했다. `@Profile`, `data.sql` 실행 순서, 환경변수 기본값 — 모두 로컬에서는 보이지 않는 문제들이었다. CI를 가장 먼저 구성했다면 각 Phase에서 즉시 피드백을 받았을 것.

**보안 패턴은 일관성이 핵심이다**
IDOR 취약점은 기존 컨트롤러의 소유권 검증 패턴을 새 도메인에 복제하지 않아서 발생했다. 신규 도메인 작성 시 기존 패턴을 체크리스트로 확인하는 습관이 필요하다.

**이종 시스템 결합은 추상화 레이어가 필요하다**
`StockPricePublisher`가 `PriceAlertService`를 직접 호출하면서 `@Lazy` 패치가 필요했다. 가격 이벤트를 Spring Application Event로 발행하고, 알림 서비스가 리스너로 구독하는 구조였다면 순환 의존성 자체가 없었을 것.
