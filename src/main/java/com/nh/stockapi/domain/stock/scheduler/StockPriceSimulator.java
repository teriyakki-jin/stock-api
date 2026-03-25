package com.nh.stockapi.domain.stock.scheduler;

import com.nh.stockapi.domain.order.service.OrderMatchingService;
import com.nh.stockapi.domain.stock.client.YahooFinanceClient;
import com.nh.stockapi.domain.stock.dto.StockPriceData;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.repository.StockRepository;
import com.nh.stockapi.domain.stock.service.MarketHoursService;
import com.nh.stockapi.domain.stock.service.StockPricePublisher;
import com.nh.stockapi.domain.stock.service.StockPriceService;
import com.nh.stockapi.domain.stock.service.VolatilityCalibrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 로컬 하이브리드 가격 스케줄러.
 *
 * ▶ 장 중 (KST 09:00~15:30, 월~금)
 *   - Yahoo Finance에서 10초마다 실시간 시세 조회
 *   - 장 intraday 변동성 패턴 적용
 *
 * ▶ 장 외 (야간/주말)
 *   - 실데이터 캘리브레이션(σ, μ, Cholesky 상관행렬) 기반 GBM 시뮬레이션
 *   - 3초마다 가격 생성
 *
 * 캘리브레이션 미완료 시 하드코딩 fallback 파라미터 사용.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class StockPriceSimulator {

    private final StockRepository stockRepository;
    private final YahooFinanceClient yahooFinanceClient;
    private final StockPriceService stockPriceService;
    private final StockPricePublisher stockPricePublisher;
    private final OrderMatchingService orderMatchingService;
    private final MarketHoursService marketHoursService;
    private final VolatilityCalibrationService calibration;

    /** 시뮬 상태 (장 외 모드 전용) */
    private final Map<String, SimState> states = new ConcurrentHashMap<>();

    /** Fallback 변동성 (캘리브레이션 전) */
    private static final Map<String, Double> FALLBACK_VOL = Map.of(
            "005930", 0.25, "000660", 0.38, "035720", 0.52,
            "005380", 0.30, "051910", 0.42
    );
    private static final double DEFAULT_FALLBACK_VOL = 0.35;
    private static final double DT = 3.0 / (252.0 * 6.5 * 3600.0); // 3초 in 연간 단위
    private static final double MAX_DRIFT_PCT = 0.25;

    // ─────────────────────────────────────────────────────────────────────────
    // 장 중: Yahoo Finance 실시간 (10초)
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void fetchRealtime() {
        if (!marketHoursService.isMarketOpen()) return;

        List<Stock> stocks = stockRepository.findAll();
        double volMult = marketHoursService.intradayVolatilityMultiplier();

        stocks.forEach(stock -> yahooFinanceClient.fetchPrice(stock.getYahooSymbol())
                .ifPresent(data -> {
                    stockPriceService.updatePrice(stock.getTicker(), data);
                    stockPricePublisher.publish(stock.getTicker(), data);
                    orderMatchingService.matchOrders(stock.getTicker(), data.price());

                    // 시뮬 상태도 실가격으로 동기화 (장 마감 후 시뮬 시작점)
                    SimState s = states.computeIfAbsent(stock.getTicker(),
                            k -> SimState.init(data.price()));
                    s.price = data.price();
                    s.dayHigh = data.price().max(s.dayHigh);
                    s.dayLow  = data.price().min(s.dayLow);
                }));

        log.debug("[실시간] {}종목 시세 갱신 (volMult={:.2f})", stocks.size(), volMult);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 장 외: 캘리브레이션 GBM 시뮬레이션 (3초)
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedRate = 3_000, initialDelay = 8_000)
    public void simulate() {
        if (marketHoursService.isMarketOpen()) return;

        List<Stock> stocks = stockRepository.findAll();
        double volMult = marketHoursService.afterHoursVolatilityMultiplier();

        if (calibration.isCalibrated()) {
            simulateCorrelated(stocks, volMult);
        } else {
            simulateIndependent(stocks, volMult);
        }
    }

    /** Cholesky 상관행렬 기반 correlated GBM */
    private void simulateCorrelated(List<Stock> stocks, double volMult) {
        List<String> calTickers = calibration.getTickers();
        double[][] L = calibration.getCholeskyL();
        int n = calTickers.size();

        // 독립 표준정규 생성
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double[] W = new double[n];
        for (int i = 0; i < n; i++) W[i] = rng.nextGaussian();

        // Z = L * W (correlated normals)
        double[] Z = new double[n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j <= i; j++)
                Z[i] += L[i][j] * W[j];

        for (int idx = 0; idx < n; idx++) {
            String ticker = calTickers.get(idx);
            Stock stock = stocks.stream().filter(s -> s.getTicker().equals(ticker)).findFirst().orElse(null);
            if (stock == null) continue;

            double sigma = calibration.getAnnualVol().getOrDefault(ticker, DEFAULT_FALLBACK_VOL);
            double mu    = calibration.getAnnualDrift().getOrDefault(ticker, 0.0);

            applyGbmStep(stock, Z[idx], sigma * volMult, mu);
        }
    }

    /** 독립 GBM (캘리브레이션 전 fallback) */
    private void simulateIndependent(List<Stock> stocks, double volMult) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        stocks.forEach(stock -> {
            double sigma = FALLBACK_VOL.getOrDefault(stock.getTicker(), DEFAULT_FALLBACK_VOL);
            double z = rng.nextGaussian();
            // 5% 확률 뉴스 이벤트
            if (rng.nextDouble() < 0.05) z *= 3.5;
            applyGbmStep(stock, z, sigma * volMult, 0.0);
        });
    }

    private void applyGbmStep(Stock stock, double z, double sigma, double mu) {
        SimState state = states.computeIfAbsent(stock.getTicker(),
                k -> SimState.init(stock.getBasePrice()));

        // GBM: dS = S * (μ*dt + σ*sqrt(dt)*Z)
        double drift   = (mu - 0.5 * sigma * sigma) * DT;
        double diffusion = sigma * Math.sqrt(DT) * z;
        double returnRate = drift + diffusion;

        // 평균 회귀 (basePrice 기준)
        double driftFromBase = state.price.doubleValue() / stock.getBasePrice().doubleValue() - 1.0;
        returnRate -= 0.015 * driftFromBase;

        BigDecimal newPrice = state.price
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(returnRate)))
                .setScale(0, RoundingMode.HALF_UP);

        // ±25% 밴드 제한
        BigDecimal upper = stock.getBasePrice().multiply(BigDecimal.valueOf(1 + MAX_DRIFT_PCT));
        BigDecimal lower = stock.getBasePrice().multiply(BigDecimal.valueOf(1 - MAX_DRIFT_PCT));
        newPrice = newPrice.max(lower).min(upper).max(BigDecimal.ONE);

        // 거래량 (변동폭 비례)
        double changePct = Math.abs(newPrice.doubleValue() / state.price.doubleValue() - 1.0);
        long volume = state.volume + (long)(ThreadLocalRandom.current().nextLong(5_000, 20_000)
                * (1 + changePct * 50));

        state.price   = newPrice;
        state.dayHigh = newPrice.max(state.dayHigh);
        state.dayLow  = newPrice.min(state.dayLow);
        state.volume  = volume;

        double changePercent = newPrice.subtract(stock.getBasePrice())
                .divide(stock.getBasePrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();

        StockPriceData data = new StockPriceData(
                newPrice, changePercent, volume, state.dayHigh, state.dayLow);

        stockPriceService.updatePrice(stock.getTicker(), data);
        stockPricePublisher.publish(stock.getTicker(), data);
        orderMatchingService.matchOrders(stock.getTicker(), newPrice);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static class SimState {
        BigDecimal price, dayHigh, dayLow;
        long volume;

        static SimState init(BigDecimal base) {
            SimState s = new SimState();
            double offset = 1.0 + ThreadLocalRandom.current().nextGaussian() * 0.005;
            s.price   = base.multiply(BigDecimal.valueOf(offset)).setScale(0, RoundingMode.HALF_UP);
            s.dayHigh = s.price;
            s.dayLow  = s.price;
            s.volume  = ThreadLocalRandom.current().nextLong(100_000, 500_000);
            return s;
        }
    }
}
