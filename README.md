# NEXUS Stock API

> Bloomberg Terminal 스타일 모의주식거래 플랫폼 — 백엔드 서버

[![CI](https://github.com/teriyakki-jin/stock-api/actions/workflows/ci.yml/badge.svg)](https://github.com/teriyakki-jin/stock-api/actions/workflows/ci.yml)

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Runtime | Java 17, Spring Boot 4.0.3 |
| Build | Gradle 8 |
| Database | PostgreSQL 16 + Supabase (외부) |
| Cache | Redis 7 |
| Auth | JWT (Access 30min + Refresh 7days) + Supabase OAuth |
| Realtime | WebSocket (STOMP) + SSE |
| External | KIS OpenAPI → Yahoo Finance (fallback) |

---

## 주요 기능

### 시세 시스템
- **실시간 모드** (장중): KIS OpenAPI → Yahoo Finance fallback → Redis 캐시 → WebSocket 브로드캐스트
- **시뮬레이션 모드** (장외): GBM(기하 브라운 운동) 기반 가격 시뮬레이션
  - 1년치 실데이터 기반 σ(연간 변동성)·μ(드리프트) 자동 추정
  - Cholesky 분해로 종목 간 상관관계 보존
  - 시간대별 변동성 배율 (장 시작 1.8×, 마감 1.4×, 점심 0.8×)
- **자동 캘리브레이션**: 기동 시 + 매일 08:30 KST 재계산

### 주문 엔진
- 비관적 락(Pessimistic Lock) 기반 동시성 제어
- 시장가 즉시 체결 + 지정가 예약 후 가격 조건 만족 시 자동 체결
- SSE(Server-Sent Events)로 체결 알림 Push

### 포트폴리오 분석
- 기간별 수익률 곡선(PnL Curve)
- MDD(Maximum Drawdown) 계산
- 매매 승률 통계
- 섹터별 비중 분석

### 소셜 기능 (Supabase)
- 팔로우 / 팔로워 / 팔로잉
- 사용자 프로필 조회·수정
- 기간별 수익률 랭킹 리더보드 (DAILY / WEEKLY / MONTHLY / ALL_TIME)

### 가격 알림
- 목표주가 도달 시 알림 발동 (GTE/LTE 조건)
- 가격 업데이트마다 실시간 체크
- 미확인 알림 폴링 API

### 기술적 분석
- RSI (14), MACD (12/26/9), 볼린저 밴드 (20, ±2σ)
- 매매 신호 자동 산출 (BUY / SELL / NEUTRAL)

---

## API 엔드포인트

### 인증
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/auth/sign-up` | 이메일 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/oauth` | OAuth 로그인 (Google/GitHub) |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 |

### 계좌
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/accounts` | 계좌 개설 |
| GET | `/api/v1/accounts` | 계좌 조회 |
| POST | `/api/v1/accounts/{id}/deposit` | 입금 |

### 주식
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/stocks` | 종목 목록 |
| GET | `/api/v1/stocks/{ticker}` | 종목 상세 |
| GET | `/api/v1/stocks/{ticker}/technicals` | RSI / MACD / 볼린저 밴드 |
| WS | `/ws → /topic/prices/{ticker}` | 실시간 시세 (STOMP) |

### 주문
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/orders/buy` | 매수 |
| POST | `/api/v1/orders/sell` | 매도 |
| GET | `/api/v1/orders/holdings` | 보유 종목 |
| GET | `/api/v1/orders/history` | 주문 내역 |
| GET | `/api/v1/orders/sse` | 체결 알림 스트림 |

### 포트폴리오
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/accounts/{id}/portfolio` | 포트폴리오 요약 |
| GET | `/api/v1/accounts/{id}/portfolio/analysis?days=30` | 수익률 곡선 / MDD / 섹터 비중 |

### 가격 알림
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/accounts/{id}/alerts` | 알림 등록 |
| GET | `/api/v1/accounts/{id}/alerts` | 알림 목록 |
| GET | `/api/v1/accounts/{id}/alerts/pending` | 미확인 발동 알림 |
| DELETE | `/api/v1/accounts/{id}/alerts/{alertId}` | 알림 삭제 |
| PATCH | `/api/v1/accounts/{id}/alerts/{alertId}/acknowledge` | 알림 확인 처리 |

### 소셜
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/social/follow/{targetId}` | 팔로우 |
| DELETE | `/api/v1/social/follow/{targetId}` | 언팔로우 |
| GET | `/api/v1/social/followers` | 내 팔로워 목록 |
| GET | `/api/v1/social/followings` | 내 팔로잉 목록 |

---

## 실행 방법

### 1. 인프라 실행

```bash
docker compose up -d
```

PostgreSQL: `localhost:15432` (stock/stock), Redis: `localhost:6379`

### 2. 환경 변수 설정

`src/main/resources/application-secrets.yml` 생성:

```yaml
supabase:
  db:
    password: YOUR_SUPABASE_DB_PASSWORD

# 선택 — KIS OpenAPI 설정 시 실시간 시세 활성화
kis:
  app-key: YOUR_KIS_APP_KEY
  app-secret: YOUR_KIS_APP_SECRET
  account-no: YOUR_ACCOUNT_NO
  mock: true  # 모의투자: true, 실투자: false
```

### 3. 서버 실행

```bash
# 로컬 개발 (시뮬레이션 모드)
./gradlew bootRun --args='--spring.profiles.active=local'
```

서버: `http://localhost:8082` · Swagger: `http://localhost:8082/swagger-ui.html`

---

## 패키지 구조

```
src/main/java/com/nh/stockapi/
├── domain/
│   ├── account/       # 가상 계좌, 입금
│   ├── alert/         # 가격 알림 (PriceAlert 엔티티/서비스/컨트롤러)
│   ├── member/        # 회원, 인증 (JWT + Supabase OAuth)
│   ├── order/         # 주문 처리, 체결, 보유 종목
│   ├── portfolio/     # 포트폴리오 분석 (PnL / MDD / 섹터)
│   ├── profile/       # 사용자 프로필 (Supabase)
│   ├── ranking/       # 랭킹 집계 및 리더보드 (Supabase)
│   ├── social/        # 팔로우 (Supabase)
│   └── stock/
│       ├── client/    # YahooFinanceClient
│       ├── scheduler/ # StockPriceScheduler (KIS→Yahoo fallback)
│       └── service/   # 시세, 기술적 분석, GBM 시뮬레이션
├── infrastructure/
│   ├── kis/           # KIS OpenAPI (토큰 관리 + 시세 조회)
│   └── supabase/      # Supabase JDBC 클라이언트
├── security/          # JWT Provider, Filter, Supabase JWT 검증
└── config/            # DataSource(Primary/Supabase), Redis, WebSocket, Security
```

---

## 아키텍처

```
[클라이언트]
    │ REST / WebSocket(STOMP) / SSE
    ▼
[Spring Boot API]
    ├── PostgreSQL  ─── 계좌 / 주문 / 보유종목 / 알림
    ├── Redis       ─── 시세 캐시 / JWT Refresh/Blacklist
    └── Supabase    ─── 프로필 / 랭킹 / 소셜(팔로우)
         │
[외부 시세]
    KIS OpenAPI ──→ Yahoo Finance (fallback)
```
