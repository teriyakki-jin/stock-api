package com.nh.stockapi.domain.ranking.dto;

import java.math.BigDecimal;

public record RankingEntryResponse(
        int rank,
        String nickname,
        String avatarUrl,
        double pnlRate,
        BigDecimal totalPnl,
        int totalTrades
) {}
