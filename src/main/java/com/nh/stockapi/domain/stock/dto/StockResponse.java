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
        Double changeRate
) {
    public static StockResponse of(Stock stock, BigDecimal currentPrice) {
        double changeRate = currentPrice.subtract(stock.getBasePrice())
                .divide(stock.getBasePrice(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        return new StockResponse(
                stock.getId(),
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getBasePrice(),
                currentPrice,
                changeRate
        );
    }
}
