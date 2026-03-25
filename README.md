# stock-api

Bloomberg Terminal 스타일 주식 거래 플랫폼의 백엔드 서버

## 기술 스택

| 구분 | 기술 |
|------|------|
| Runtime | Java 17, Spring Boot 4.0.3 |
| Build | Gradle 8 |
| Database | PostgreSQL 16 (Docker, port 15432) |
| Cache | Redis 7 (Docker, port 6379) |
| Auth | JWT (Access 30min + Refresh 7days) |
| Realtime | WebSocket (STOMP) + SSE |
| External | Yahoo Finance v8 API |

## 주요 기능

### 시세 시스템
- **실시간 모드** (장중): Yahoo Finance v8 API → Redis TTL 70s 캐시 → WebSocket 브로드캐스트
- **시뮬레이션 모드** (장외): 캘리브레이션된 GBM(기하 브라운 운동)으로 가격 시뮬레이션
  - 1년치 실데이터 기반 σ(연간 변동성), μ(연간 드리프트) 자동 추정
  - Cholesky 분해로 종목 간 상관관계 보존 (삼성전자 ↔ SK하이닉스 동조)
  - 시간대별 변동성 배율: 장 시작 1.8×, 마감 1.4×, 점심 0.8×, 장외 0.35×
- **자동 캘리브레이션**: 서버 기동 시 + 매일 08:30 KST 재계산

### 주문 엔진
- 비관적 락(Pessimistic Lock) 기반 동시성 제어
- 즉시체결 방식 (지정가 → 현재가 기준 즉시 처리)
- SSE(Server-Sent Events)로 체결 알림 Push

### 기술적 분석
- **RSI (14)**: Wilder's Smoothed EMA
- **MACD (12, 26, 9)**: EMA 차이 + Signal + Histogram
- **볼린저 밴드 (20, ±2σ)**: SMA20 ± 표준편차 2배
- **매매 신호**: RSI<35 + MACD 상향 → BUY / RSI>65 + MACD 하향 → SELL

### 계좌 / 포트폴리오
- 가상 계좌 개설 및 입금
- 보유 종목 평가손익 실시간 계산
- 전체 주문 내역 조회

## API 명세

### 인증
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/auth/sign-up` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 |
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
| GET | `/api/v1/stocks/{ticker}/technicals` | 기술적 분석 (RSI/MACD/BB) |
| GET | `/api/v1/stocks/sim/status` | 시뮬레이션 상태 |
| WS | `/ws/prices` → `/topic/prices/{ticker}` | 실시간 시세 (STOMP) |

### 주문
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/orders` | 주문 (매수/매도) |
| GET | `/api/v1/orders/holdings` | 보유 종목 |
| GET | `/api/v1/orders/history` | 주문 내역 |
| GET | `/api/v1/orders/sse` | 체결 알림 스트림 (SSE) |

## 실행 방법

### 1. 인프라 실행

```bash
docker compose up -d
```

PostgreSQL: `localhost:15432` (stock/stock), Redis: `localhost:6379`

### 2. 백엔드 실행

```bash
# 로컬 개발 (시뮬레이션 모드 활성화)
./gradlew bootRun --args='--spring.profiles.active=local'

# 운영 (Yahoo Finance 실시간 스케줄러 활성화)
./gradlew bootRun
```

서버: `http://localhost:8082`

## 패키지 구조

```
src/main/java/com/nh/stockapi/
├── domain/
│   ├── auth/          # JWT 인증/인가, 토큰 관리
│   ├── account/       # 가상 계좌, 입금
│   ├── stock/
│   │   ├── client/    # YahooFinanceClient (현재가 + 히스토리)
│   │   ├── controller/# StockController (REST + WebSocket)
│   │   ├── dto/       # OhlcvBar, TechnicalData, StockTechnicalResponse
│   │   ├── entity/    # Stock
│   │   ├── repository/
│   │   ├── scheduler/ # StockPriceScheduler (운영), StockPriceSimulator (로컬)
│   │   └── service/   # StockService, TechnicalIndicatorService,
│   │                  # VolatilityCalibrationService, MarketHoursService
│   └── order/         # 주문 처리, 체결, SSE 알림
└── global/
    ├── config/        # Security, WebSocket, Redis, CORS
    └── exception/     # 글로벌 예외 처리
```

## 시뮬레이션 아키텍처

```
[ApplicationReadyEvent]
        │
        ▼
VolatilityCalibrationService
  ├─ Yahoo 1y history 조회 (전 종목)
  ├─ 일별 로그수익률 계산
  ├─ σ = std(log_returns) * √252
  ├─ μ = mean(log_returns) * 252
  └─ Cholesky(상관행렬) → L 행렬

[장외 / @Scheduled 3s]
StockPriceSimulator.simulate()
  ├─ 독립 표준정규 W ~ N(0,1)
  ├─ 상관 정규 Z = L * W
  ├─ GBM: dS = S*(μ·dt + σ·√dt·Z)
  └─ 평균회귀 보정 + WebSocket 브로드캐스트
```
