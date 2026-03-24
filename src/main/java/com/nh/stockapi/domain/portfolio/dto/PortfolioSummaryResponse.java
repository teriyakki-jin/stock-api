package com.nh.stockapi.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryResponse(
        BigDecimal totalInvested,
        BigDecimal currentValue,
        BigDecimal totalPnl,
        double pnlRate,
        List<HoldingDetail> holdings
) {
    public record HoldingDetail(
            Long holdingId,
            String ticker,
            String stockName,
            Long quantity,
            BigDecimal avgPrice,
            BigDecimal currentPrice,
            BigDecimal evaluatedAmount,
            double profitRate,
            double allocationPct
    ) {}
}
