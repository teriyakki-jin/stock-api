package com.nh.stockapi.domain.portfolio.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.service.AccountService;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.entity.Order;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.order.repository.OrderRepository;
import com.nh.stockapi.domain.portfolio.dto.PortfolioAnalysisResponse;
import com.nh.stockapi.domain.portfolio.dto.PortfolioAnalysisResponse.PnlPoint;
import com.nh.stockapi.domain.portfolio.dto.PortfolioAnalysisResponse.SectorWeight;
import com.nh.stockapi.domain.portfolio.dto.PortfolioSummaryResponse;
import com.nh.stockapi.domain.portfolio.dto.PortfolioSummaryResponse.HoldingDetail;
import com.nh.stockapi.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final AccountService accountService;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    // 포트폴리오 요약
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long accountId, Member member) {
        Account account = accountService.findById(accountId);
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<Holding> holdings = holdingRepository.findByAccountIdWithStock(accountId);

        record HoldingWithPrice(Holding h, BigDecimal price) {}
        List<HoldingWithPrice> withPrices = holdings.stream()
                .map(h -> new HoldingWithPrice(h,
                        stockService.getCurrentPrice(h.getStock().getTicker(), h.getStock().getBasePrice())))
                .toList();

        BigDecimal totalInvested = withPrices.stream()
                .map(hp -> hp.h().getAvgPrice().multiply(BigDecimal.valueOf(hp.h().getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentValue = withPrices.stream()
                .map(hp -> hp.price().multiply(BigDecimal.valueOf(hp.h().getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPnl = currentValue.subtract(totalInvested);

        double pnlRate = totalInvested.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .doubleValue();

        List<HoldingDetail> details = withPrices.stream()
                .map(hp -> {
                    Holding h = hp.h();
                    BigDecimal price = hp.price();
                    BigDecimal evaluated = price.multiply(BigDecimal.valueOf(h.getQuantity()));

                    double profitRate = h.getAvgPrice().compareTo(BigDecimal.ZERO) == 0 ? 0.0
                            : price.subtract(h.getAvgPrice())
                                   .divide(h.getAvgPrice(), 6, RoundingMode.HALF_UP)
                                   .multiply(BigDecimal.valueOf(100))
                                   .doubleValue();

                    double allocationPct = currentValue.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                            : evaluated.divide(currentValue, 6, RoundingMode.HALF_UP)
                                       .multiply(BigDecimal.valueOf(100))
                                       .doubleValue();

                    return new HoldingDetail(
                            h.getId(),
                            h.getStock().getTicker(),
                            h.getStock().getName(),
                            h.getQuantity(),
                            h.getAvgPrice(),
                            price,
                            evaluated,
                            profitRate,
                            allocationPct
                    );
                })
                .toList();

        return new PortfolioSummaryResponse(totalInvested, currentValue, totalPnl, pnlRate, details);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 포트폴리오 심화 분석
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse getAnalysis(Long accountId, Member member, int days) {
        Account account = accountService.findById(accountId);
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<Order> filled = orderRepository.findFilledSince(accountId, from);

        // ── 1. 수익률 곡선 (체결 순서대로 누적 PnL) ────────────────────────
        List<PnlPoint> pnlCurve = buildPnlCurve(filled);

        // ── 2. 승률 계산 (SELL 체결 기준) ──────────────────────────────────
        List<Order> sells = filled.stream()
                .filter(o -> o.getOrderType().name().startsWith("SELL"))
                .toList();

        // 매도 단가 > 매수 평단가(unitPrice) 이면 수익
        // unitPrice 는 체결 단가이므로, 매도 unitPrice > 매수 avgPrice 가 아닌
        // totalAmount 부호로는 알 수 없음 → 간단하게 매도 unitPrice vs holding avgPrice 비교
        // 여기서는 Order.unitPrice 기준으로 +/- 판단 (BUY 체결가보다 SELL 체결가가 높으면 수익)
        // 편의상: 매도 시점 unitPrice > 직전 BUY 의 unitPrice 이면 수익으로 봄
        // 실제 정확한 방법: holding avgPrice 를 별도 계산해야 하나 복잡하므로
        // 매도 totalAmount - 해당 수량 × BUY avgPrice 로 판단
        int profit = 0, loss = 0;
        // BUY 주문들로 종목별 평단가 추적
        Map<String, BigDecimal> runningAvg = new HashMap<>();
        Map<String, Long> runningQty = new HashMap<>();
        for (Order o : filled) {
            String ticker = o.getStock().getTicker();
            if (o.getOrderType().name().startsWith("BUY")) {
                long prevQty  = runningQty.getOrDefault(ticker, 0L);
                BigDecimal prevAvg = runningAvg.getOrDefault(ticker, BigDecimal.ZERO);
                long addQty  = o.getQuantity();
                BigDecimal newAvg = prevQty == 0
                        ? o.getUnitPrice()
                        : prevAvg.multiply(BigDecimal.valueOf(prevQty))
                                 .add(o.getUnitPrice().multiply(BigDecimal.valueOf(addQty)))
                                 .divide(BigDecimal.valueOf(prevQty + addQty), 2, RoundingMode.HALF_UP);
                runningQty.put(ticker, prevQty + addQty);
                runningAvg.put(ticker, newAvg);
            } else { // SELL
                BigDecimal avg = runningAvg.getOrDefault(ticker, o.getUnitPrice());
                if (o.getUnitPrice().compareTo(avg) > 0) profit++;
                else loss++;
                long remaining = runningQty.getOrDefault(ticker, 0L) - o.getQuantity();
                runningQty.put(ticker, Math.max(0, remaining));
            }
        }
        int totalTrades = profit + loss;
        double winRate = totalTrades == 0 ? 0.0
                : (double) profit / totalTrades * 100;

        // ── 3. MDD (수익률 곡선 기준) ──────────────────────────────────────
        double mdd = computeMdd(pnlCurve);

        // ── 4. 섹터 비중 (현재 보유) ─────────────────────────────────────
        List<SectorWeight> sectorWeights = buildSectorWeights(accountId);

        return new PortfolioAnalysisResponse(
                winRate, mdd, filled.size(), profit, loss,
                sectorWeights, pnlCurve
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private List<PnlPoint> buildPnlCurve(List<Order> filledOrders) {
        BigDecimal cumPnl = BigDecimal.ZERO;
        BigDecimal cumInvested = BigDecimal.ZERO;
        List<PnlPoint> points = new ArrayList<>();

        for (Order o : filledOrders) {
            boolean isBuy  = o.getOrderType().name().startsWith("BUY");
            boolean isSell = o.getOrderType().name().startsWith("SELL");

            if (isBuy) {
                cumInvested = cumInvested.add(o.getTotalAmount());
            } else if (isSell) {
                // 간단화: 매도금액 - 수량×해당 시점 avgPrice (위에서 계산한 runningAvg 재활용 불가)
                // 여기서는 totalAmount 자체를 수익으로 표기 (단순 현금 흐름 방식)
                cumPnl = cumPnl.add(o.getTotalAmount());
            }

            double rate = cumInvested.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : cumPnl.divide(cumInvested, 6, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100)).doubleValue();

            points.add(new PnlPoint(
                    o.getCreatedAt().format(DT_FMT),
                    cumPnl,
                    rate
            ));
        }
        return points;
    }

    private double computeMdd(List<PnlPoint> curve) {
        if (curve.isEmpty()) return 0.0;
        double peak = Double.NEGATIVE_INFINITY;
        double mdd  = 0.0;
        for (PnlPoint p : curve) {
            double rate = p.cumulativeRate();
            if (rate > peak) peak = rate;
            double drawdown = peak - rate;
            if (drawdown > mdd) mdd = drawdown;
        }
        return mdd;
    }

    private List<SectorWeight> buildSectorWeights(Long accountId) {
        List<Holding> holdings = holdingRepository.findByAccountIdWithStock(accountId);

        Map<String, BigDecimal> sectorAmount = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Holding h : holdings) {
            BigDecimal price = stockService.getCurrentPrice(
                    h.getStock().getTicker(), h.getStock().getBasePrice());
            BigDecimal evaluated = price.multiply(BigDecimal.valueOf(h.getQuantity()));
            String sector = Optional.ofNullable(h.getStock().getSector()).orElse("기타");
            sectorAmount.merge(sector, evaluated, BigDecimal::add);
            total = total.add(evaluated);
        }

        final BigDecimal finalTotal = total;
        return sectorAmount.entrySet().stream()
                .map(e -> {
                    double w = finalTotal.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                            : e.getValue().divide(finalTotal, 6, RoundingMode.HALF_UP)
                                         .multiply(BigDecimal.valueOf(100)).doubleValue();
                    return new SectorWeight(e.getKey(), e.getValue(), w);
                })
                .sorted(Comparator.comparingDouble(SectorWeight::weightPct).reversed())
                .toList();
    }
}
