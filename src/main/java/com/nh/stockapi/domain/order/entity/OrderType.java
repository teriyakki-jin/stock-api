package com.nh.stockapi.domain.order.entity;

public enum OrderType {
    BUY,        // 매수 시장가 — 즉시 체결
    SELL,       // 매도 시장가 — 즉시 체결
    BUY_LIMIT,  // 매수 지정가 — limitPrice 이하일 때 체결
    SELL_LIMIT  // 매도 지정가 — limitPrice 이상일 때 체결
}
