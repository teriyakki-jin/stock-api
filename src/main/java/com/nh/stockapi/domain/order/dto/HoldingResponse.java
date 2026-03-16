package com.nh.stockapi.domain.order.dto;

import com.nh.stockapi.domain.order.entity.Holding;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record HoldingResponse(
        String ticker,
        String stockName,
        Long quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal evaluatedAmount,
        Double profitRate
) {
    public static HoldingResponse of(Holding holding, BigDecimal currentPrice) {
        BigDecimal evaluated = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
        double profitRate = currentPrice.subtract(holding.getAvgPrice())
                .divide(holding.getAvgPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        return new HoldingResponse(
                holding.getStock().getTicker(),
                holding.getStock().getName(),
                holding.getQuantity(),
                holding.getAvgPrice(),
                currentPrice,
                evaluated,
                profitRate
        );
    }
}
