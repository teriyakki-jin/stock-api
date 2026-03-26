-- ═══════════════════════════════════════════════════════════════════════
-- V1__init_schema.sql
-- 초기 스키마 생성 (Hibernate DDL 자동생성 대체)
-- ═══════════════════════════════════════════════════════════════════════

-- ── members ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS members (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(100) NOT NULL UNIQUE,
    password     VARCHAR(255),
    name         VARCHAR(50)  NOT NULL,
    phone        VARCHAR(20)  UNIQUE,
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    supabase_uid VARCHAR(36)  UNIQUE,
    provider     VARCHAR(10)  NOT NULL DEFAULT 'LOCAL',
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

-- ── accounts ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    id             BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20)     NOT NULL UNIQUE,
    member_id      BIGINT          NOT NULL REFERENCES members(id),
    balance        NUMERIC(20, 2)  NOT NULL DEFAULT 0,
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

-- ── stocks ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stocks (
    id           BIGSERIAL PRIMARY KEY,
    ticker       VARCHAR(10)     NOT NULL UNIQUE,
    name         VARCHAR(100)    NOT NULL,
    market       VARCHAR(20)     NOT NULL,
    sector       VARCHAR(30),
    base_price   NUMERIC(18, 2)  NOT NULL,
    total_shares BIGINT          NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

-- ── orders ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id            BIGSERIAL PRIMARY KEY,
    account_id    BIGINT          NOT NULL REFERENCES accounts(id),
    stock_id      BIGINT          NOT NULL REFERENCES stocks(id),
    order_type    VARCHAR(15)     NOT NULL,
    status        VARCHAR(15)     NOT NULL,
    quantity      BIGINT          NOT NULL,
    unit_price    NUMERIC(18, 2)  NOT NULL,
    total_amount  NUMERIC(18, 2)  NOT NULL,
    limit_price   NUMERIC(18, 2),
    remaining_qty BIGINT,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_account       ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_order_stock         ON orders(stock_id);
CREATE INDEX IF NOT EXISTS idx_order_status_stock  ON orders(status, stock_id);

-- ── holdings ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS holdings (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT          NOT NULL REFERENCES accounts(id),
    stock_id   BIGINT          NOT NULL REFERENCES stocks(id),
    quantity   BIGINT          NOT NULL,
    avg_price  NUMERIC(18, 2)  NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (account_id, stock_id)
);

CREATE INDEX IF NOT EXISTS idx_holding_account ON holdings(account_id);

-- ── price_alerts ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_alerts (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT          NOT NULL REFERENCES accounts(id),
    ticker       VARCHAR(10)     NOT NULL,
    target_price NUMERIC(15, 2)  NOT NULL,
    condition    VARCHAR(3)      NOT NULL,
    active       BOOLEAN         NOT NULL DEFAULT TRUE,
    triggered    BOOLEAN         NOT NULL DEFAULT FALSE,
    acknowledged BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP,
    triggered_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_price_alerts_ticker_active ON price_alerts(ticker, active);
