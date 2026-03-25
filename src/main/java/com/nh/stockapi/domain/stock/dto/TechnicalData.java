package com.nh.stockapi.domain.stock.dto;

import java.math.BigDecimal;

/**
 * 기술적 분석 지표 스냅샷.
 * - RSI 14기간
 * - MACD (12, 26, 9)
 * - 볼린저밴드 (20기간, ±2σ)
 */
public record TechnicalData(
        double rsi,
        double macd,
        double macdSignal,
        double macdHistogram,
        BigDecimal bbUpper,
        BigDecimal bbMiddle,
        BigDecimal bbLower,
        String signal        // "BUY" | "SELL" | "NEUTRAL"
) {}
