package com.nh.stockapi.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포트폴리오 심화 분석 응답
 * - 기간별 수익률 곡선 (일별 누적 PnL)
 * - 섹터 비중
 * - 핵심 지표: 승률, MDD, 총 거래 횟수
 */
public record PortfolioAnalysisResponse(
        // ── 핵심 지표 ──────────────────────────────────────
        double winRate,          // 수익 거래 비율 (%)
        double mdd,              // 최대 낙폭 (%)
        int totalTrades,         // 총 체결 거래 수
        int profitTrades,        // 수익 거래 수
        int lossTrades,          // 손실 거래 수

        // ── 섹터 비중 ──────────────────────────────────────
        List<SectorWeight> sectorWeights,

        // ── 수익률 곡선 ─────────────────────────────────────
        List<PnlPoint> pnlCurve
) {
    public record SectorWeight(
            String sector,
            BigDecimal evaluatedAmount,
            double weightPct
    ) {}

    public record PnlPoint(
            String date,          // yyyy-MM-dd HH:mm
            BigDecimal cumulativePnl,
            double cumulativeRate
    ) {}
}
