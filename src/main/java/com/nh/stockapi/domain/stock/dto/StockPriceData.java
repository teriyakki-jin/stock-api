package com.nh.stockapi.domain.stock.dto;

import java.math.BigDecimal;

/**
 * Yahoo Finance에서 조회한 실시간 시세 데이터.
 * Redis에 JSON으로 직렬화되어 캐시된다 (TTL: 70초).
 */
public record StockPriceData(
        BigDecimal price,          // 현재가
        double changePercent,      // 전일 대비 등락률 (%)
        long volume,               // 거래량
        BigDecimal dayHigh,        // 당일 고가
        BigDecimal dayLow          // 당일 저가
) {}
