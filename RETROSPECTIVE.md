# 주식 매매 API 프로젝트 회고

> 작성일: 2026-03-16
> 프로젝트: [stock-api](https://github.com/teriyakki-jin/stock-api) + [stock-frontend](https://github.com/teriyakki-jin/stock-frontend)

---

## 무엇을 만들었나

Spring Boot 기반 주식 모의투자 REST API와 Bloomberg Terminal 스타일의 React 프론트엔드.

| 항목 | 내용 |
|------|------|
| 백엔드 | Spring Boot 4.0.3, Java 17, PostgreSQL 16, Redis 7 |
| 프론트엔드 | React 18, TypeScript, Vite, Zustand, React Query, Tailwind |
| 주요 기능 | 회원가입/로그인, 계좌 개설/입금, 주식 매수/매도, 보유 종목 조회 |
| 보안 | JWT Access(30분) + Refresh(7일), Access Token 블랙리스트, 비관적 락 |
| 인프라 | Docker Compose, Dockerfile 멀티스테이지 빌드, GitHub Actions CI |

---

## 타임라인

```
1. 백엔드 초기 구현 (Spring Boot 뼈대 + 도메인 설계)
2. GitHub 배포 (teriyakki-jin/stock-api)
3. 앱 실행 및 API 검증
4. 프론트엔드 구현 (Bloomberg Terminal 테마)
5. 모노레포 재구성 (stock-frontend: backend/ + frontend/)
6. 프로젝트 평가 + 우선순위 기반 개선
7. 테스트 작성 + CI 구축 + 버그 수정
```

---

## 잘 된 것들

### 1. 도메인 설계가 깔끔했다
`Member → Account → Holding → Order` 흐름이 명확했고, 각 엔티티 책임이 분리되어 있어서 기능 추가 시 충돌이 없었다. `Account.withdraw()` 안에서 잔액 검증을 하는 방식이 도메인 로직을 엔티티에 응집시킨 좋은 사례.

### 2. 비관적 락으로 동시성 문제를 깔끔하게 해결했다
10개 스레드가 동시에 매수 주문을 넣어도 잔액이 정확히 차감됨을 `CountDownLatch`로 검증했다. `PESSIMISTIC_WRITE` 락을 리포지토리 쿼리 메서드 레벨에서 걸어서 서비스 코드는 깨끗하게 유지됐다.

### 3. Redis를 이중으로 활용했다
Refresh Token 저장 + Access Token 블랙리스트, 두 가지 역할을 Redis 하나로 처리. prefix(`RT:`, `BL:`)로 키 충돌 없이 깔끔하게 분리했다.

### 4. 프론트 UX가 의도한 방향으로 나왔다
Bloomberg Terminal 스타일 — 초록 텍스트, 검정 배경, 모노스페이스 폰트 — 을 Tailwind 커스텀 팔레트로 구현. React Query로 서버 상태와 로컬 상태를 명확하게 구분했다.

---

## 삽질한 것들

### 1. 테스트에서 엔티티 ID가 null이었다 (NullPointerException 연쇄)

**상황**
`OrderServiceTest`에서 `account.getMember().getId().equals(member.getId())` 호출 시 NPE 발생.

**원인**
JPA가 관리하지 않는 순수 Java 객체(`Member.create(...)`)는 `@Id` 필드가 null. 실제 DB에 저장하지 않으니 당연한 일인데, 처음엔 이걸 놓쳤다.

**해결**
```java
ReflectionTestUtils.setField(member, "id", 1L);
ReflectionTestUtils.setField(account, "id", 100L);
ReflectionTestUtils.setField(stock, "id", 10L);
```

**교훈**
단위 테스트에서 엔티티 ID를 쓰는 로직이 있으면 ReflectionTestUtils로 반드시 주입해야 한다. Mockito `anyLong()`은 null을 매칭하지 않는다는 것도 함께 파악했다.

---

### 2. Mockito STRICT_STUBS가 발목을 잡았다

**상황**
`PotentialStubbingProblem` — stubbing 했는데 실제로 호출되지 않았다는 경고가 테스트 실패로 이어짐.

**원인**
stock ID가 null이라 `anyLong()` stub이 매칭되지 않았고, 그러면서 호출 안 된 stub으로 취급됨.

**해결**
`@MockitoSettings(strictness = Strictness.LENIENT)` 추가 + stock ID 주입으로 근본 원인도 함께 해결.

**교훈**
STRICT_STUBS는 좋은 습관을 강제하지만, 실패 메시지가 직접적이지 않아서 진짜 원인을 찾는 데 시간이 걸린다. 오히려 NPE 스택트레이스를 먼저 보는 게 빨랐다.

---

### 3. CI에서 PostgreSQL 컨테이너가 죽었다

**상황**
GitHub Actions에서 PostgreSQL 서비스 컨테이너가 health check 실패로 시작조차 안 됨.

**원인**
test 프로파일은 H2 인메모리 DB를 쓰는데, CI yml에 PostgreSQL 서비스가 불필요하게 설정되어 있었다.

**해결**
```yaml
services:
  # test 프로파일은 H2 인메모리 DB 사용 → PostgreSQL 불필요
  redis:
    image: redis:7-alpine
```
PostgreSQL 블록 전체 제거.

**교훈**
CI 환경과 로컬 환경의 차이를 항상 의식해야 한다. 로컬에서 docker-compose로 PostgreSQL 띄우고 테스트해도 CI는 별도 환경이다. test 프로파일이 어떤 DB를 쓰는지 CI 설정과 맞춰야 한다.

---

### 4. `@SpringBootTest`가 환경변수를 요구했다

**상황**
`StockApiApplicationTests`에서 `PlaceholderResolutionException: JWT_SECRET 환경변수 없음`.

**원인**
application.yml에서 `jwt.secret: ${JWT_SECRET}` (필수 환경변수)로 바꾼 후, 테스트가 default 프로파일을 로드하면서 환경변수를 찾지 못함.

**해결**
```java
@SpringBootTest
@ActiveProfiles("test")  // 추가
class StockApiApplicationTests { ... }
```
test 프로파일에 `jwt.secret` 하드코딩값 추가.

**교훈**
보안 설정을 바꿀 때는 테스트 프로파일도 함께 챙겨야 한다. 보안 강화 → 테스트 깨짐 → 프로파일 설정 누락 패턴은 흔히 발생한다.

---

### 5. OrderRequest 파라미터 순서 실수

**상황**
`new OrderRequest("005930", 5L, OrderType.BUY)` — 컴파일 에러.

**원인**
record 정의가 `(ticker, OrderType, quantity)` 순서인데 테스트에서 `(ticker, quantity, OrderType)`으로 작성.

**해결**
테스트 전체에서 파라미터 순서 수정.

**교훈**
record는 파라미터 순서가 API 계약이다. IDE 자동완성에 의존하면 이런 실수를 방지할 수 있다. 타입이 다른 파라미터끼리 순서가 바뀌면 컴파일 에러가 나지만, 같은 타입끼리 바뀌면 런타임까지 모른다.

---

## 기술적으로 배운 것

| 주제 | 내용 |
|------|------|
| JWT 블랙리스트 | 로그아웃 시 Access Token 남은 유효시간만큼 Redis에 보관, 검증 시 블랙리스트 체크 |
| 비관적 락 | `@Lock(PESSIMISTIC_WRITE)` + `@Transactional` 조합으로 select-for-update 구현 |
| H2 + PostgreSQLDialect | H2 위에서 PostgreSQL 문법 호환 (`MODE=PostgreSQL`), 단 dialect warning은 감수 |
| Mockito LENIENT | 불필요한 stubbing 경고를 억제할 때 쓰지만, 근본 원인은 따로 있을 수 있음 |
| ReflectionTestUtils | 비공개 필드 (특히 JPA `@Id`) 주입 — 단위 테스트의 필수 도구 |
| Vite proxy | `vite.config.ts`의 `/api` proxy로 CORS 없이 개발 가능 |
| Zustand persist | JWT 토큰을 localStorage에 안전하게 저장, 새로고침 시에도 로그인 유지 |

---

## 아쉬운 점 / 다음에는

- **테스트를 나중에 썼다** — TDD로 시작했으면 OrderRequest 파라미터 순서 같은 실수를 초반에 잡았을 것. 다음 프로젝트는 서비스 메서드 작성 전 테스트 먼저.
- **CI를 처음부터 구성했으면** — 로컬에서 잘 되던 게 CI에서 터지는 경험을 줄일 수 있었다. Dockerfile + GitHub Actions는 day 1 작업.
- **프론트 에러 처리가 얕다** — Axios 인터셉터에서 메시지 정규화는 했지만, 에러 바운더리나 토스트 알림이 없다. 실제 서비스라면 사용자 피드백이 필수.
- **테스트 커버리지** — 8개 테스트는 시작이지만, Controller 레이어 통합 테스트(`@WebMvcTest`)와 Repository 테스트가 없다.

---

## 한 줄 요약

> 설계는 깔끔했고, 보안과 동시성은 제대로 잡았다.
> 테스트와 CI는 처음부터 함께 가야 한다는 걸 몸으로 배웠다.
