package com.nh.stockapi.domain.ranking.dto;

import java.math.BigDecimal;

public record MyRankingResponse(
        int rank,
        double pnlRate,
        BigDecimal totalPnl,
        int totalTrades,
        String period,
        String snapshotDate
) {}
