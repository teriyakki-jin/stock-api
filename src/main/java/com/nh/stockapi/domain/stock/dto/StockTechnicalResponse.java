package com.nh.stockapi.domain.stock.dto;

import java.util.List;

/** 종목 기술적 분석 + 히스토리 응답 */
public record StockTechnicalResponse(
        String ticker,
        String name,
        List<OhlcvBar> history,       // 최근 252 거래일 OHLCV
        TechnicalData technicals,     // 최신 지표값
        double annualizedVolatility,  // 연간 변동성 (%)
        double annualizedReturn       // 연간 수익률 (%)
) {}
