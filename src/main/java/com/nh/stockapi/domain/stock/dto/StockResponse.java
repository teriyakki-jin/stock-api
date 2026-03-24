package com.nh.stockapi.domain.stock.dto;

import com.nh.stockapi.domain.stock.entity.Stock;

import java.math.BigDecimal;

public record StockResponse(
        Long id,
        String ticker,
        String name,
        String market,
        BigDecimal basePrice,
        BigDecimal currentPrice,
        double changeRate,     // 전일 대비 등락률 (%)
        Long volume,           // 거래량 (null = 시세 미수신)
        BigDecimal dayHigh,    // 당일 고가
        BigDecimal dayLow      // 당일 저가
) {
    /** Yahoo Finance 실시간 시세 적용 */
    public static StockResponse of(Stock stock, StockPriceData priceData) {
        return new StockResponse(
                stock.getId(),
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getBasePrice(),
                priceData.price(),
                priceData.changePercent(),
                priceData.volume(),
                priceData.dayHigh(),
                priceData.dayLow()
        );
    }

    /** 시세 미수신 시 basePrice fallback */
    public static StockResponse ofFallback(Stock stock) {
        return new StockResponse(
                stock.getId(),
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getBasePrice(),
                stock.getBasePrice(),
                0.0,
                null,
                null,
                null
        );
    }
}
