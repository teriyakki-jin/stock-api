package com.nh.stockapi.domain.stock.service;

import com.nh.stockapi.domain.stock.client.YahooFinanceClient;
import com.nh.stockapi.domain.stock.dto.OhlcvBar;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.repository.StockRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 실제 1년치 일별 데이터 기반으로 각 종목의 변동성, 드리프트, 종목 간 상관관계를 계산한다.
 * 결과는 StockPriceSimulator에서 GBM 파라미터로 사용된다.
 *
 * - σ (annualized vol)  : 일별 로그수익률 표준편차 * sqrt(252)
 * - μ (annualized drift): 일별 로그수익률 평균 * 252
 * - Cholesky matrix     : 상관행렬의 Cholesky 분해 → 상관 난수 생성용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VolatilityCalibrationService {

    private final StockRepository stockRepository;
    private final YahooFinanceClient yahooFinanceClient;

    /** 종목 순서 (calibration 결과 배열 인덱스 기준) */
    @Getter private List<String> tickers = new ArrayList<>();

    /** ticker → 연간 변동성 (σ) */
    @Getter private Map<String, Double> annualVol = new HashMap<>();

    /** ticker → 연간 드리프트 (μ) */
    @Getter private Map<String, Double> annualDrift = new HashMap<>();

    /** Cholesky 하삼각행렬 L (상관행렬 분해) */
    @Getter private double[][] choleskyL = new double[0][0];

    /** 캘리브레이션 완료 여부 */
    @Getter private volatile boolean calibrated = false;

    /** 서버 완전 기동 후 (모든 빈 초기화 완료) 캘리브레이션 시작 */
    @EventListener(ApplicationReadyEvent.class)
    public void calibrateOnStartup() {
        new Thread(this::calibrate, "calibration-init").start();
    }

    /** 매일 장 시작 전 08:30 KST에 재캘리브레이션 */
    @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Seoul")
    public void recalibrateDaily() {
        calibrate();
    }

    private void calibrate() {
        log.info("[캘리브레이션] 시작 — Yahoo Finance 1년 히스토리 조회 중...");
        try {
            List<Stock> stocks = stockRepository.findAll();
            List<String> newTickers = stocks.stream().map(Stock::getTicker).toList();
            int n = newTickers.size();

            // 종목별 일별 로그수익률 수집
            Map<String, double[]> logReturnsMap = new LinkedHashMap<>();
            for (Stock stock : stocks) {
                List<OhlcvBar> history = yahooFinanceClient.fetchHistory(stock.getYahooSymbol());
                if (history.size() < 20) {
                    log.warn("[캘리브레이션] {} 히스토리 데이터 부족 ({}건)", stock.getTicker(), history.size());
                    continue;
                }
                double[] lr = computeLogReturns(history);
                logReturnsMap.put(stock.getTicker(), lr);
                Thread.sleep(300); // Yahoo Finance 레이트리밋 방지
            }

            if (logReturnsMap.isEmpty()) {
                log.warn("[캘리브레이션] 유효 데이터 없음 — 기본값 사용");
                return;
            }

            // 공통 길이 맞추기
            int minLen = logReturnsMap.values().stream().mapToInt(a -> a.length).min().orElse(0);
            if (minLen < 10) return;

            Map<String, Double> newVol   = new HashMap<>();
            Map<String, Double> newDrift = new HashMap<>();
            for (var entry : logReturnsMap.entrySet()) {
                double[] lr = Arrays.copyOfRange(entry.getValue(), entry.getValue().length - minLen, entry.getValue().length);
                logReturnsMap.put(entry.getKey(), lr);
                newVol.put(entry.getKey(),   std(lr)  * Math.sqrt(252));
                newDrift.put(entry.getKey(), mean(lr) * 252);
                log.info("[캘리브레이션] {} σ={:.4f} μ={:.4f}",
                        entry.getKey(), newVol.get(entry.getKey()), newDrift.get(entry.getKey()));
            }

            // 상관행렬 계산
            List<String> validTickers = new ArrayList<>(logReturnsMap.keySet());
            int m = validTickers.size();
            double[][] corr = new double[m][m];
            for (int i = 0; i < m; i++) {
                corr[i][i] = 1.0;
                for (int j = i + 1; j < m; j++) {
                    double r = pearsonCorr(logReturnsMap.get(validTickers.get(i)),
                                          logReturnsMap.get(validTickers.get(j)));
                    corr[i][j] = r;
                    corr[j][i] = r;
                }
            }

            double[][] L = cholesky(corr);

            // 원자적으로 결과 갱신
            synchronized (this) {
                this.tickers   = validTickers;
                this.annualVol  = newVol;
                this.annualDrift = newDrift;
                this.choleskyL  = L;
                this.calibrated = true;
            }
            log.info("[캘리브레이션] 완료 — {}개 종목, minLen={}", m, minLen);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[캘리브레이션] 실패", e);
        }
    }

    // ────────────────── 수학 유틸 ──────────────────

    private double[] computeLogReturns(List<OhlcvBar> bars) {
        double[] lr = new double[bars.size() - 1];
        for (int i = 1; i < bars.size(); i++) {
            double prev = bars.get(i - 1).close().doubleValue();
            double curr = bars.get(i).close().doubleValue();
            if (prev > 0) lr[i - 1] = Math.log(curr / prev);
        }
        return lr;
    }

    private double mean(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    private double std(double[] arr) {
        double m = mean(arr);
        double sq = 0;
        for (double v : arr) sq += (v - m) * (v - m);
        return Math.sqrt(sq / (arr.length - 1));
    }

    private double pearsonCorr(double[] a, double[] b) {
        double ma = mean(a), mb = mean(b);
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < a.length; i++) {
            num += (a[i] - ma) * (b[i] - mb);
            da  += (a[i] - ma) * (a[i] - ma);
            db  += (b[i] - mb) * (b[i] - mb);
        }
        double denom = Math.sqrt(da * db);
        return denom == 0 ? 0 : num / denom;
    }

    /**
     * Cholesky-Banachiewicz 분해.
     * L*L^T = A (A는 양정치 대칭행렬)
     */
    private double[][] cholesky(double[][] A) {
        int n = A.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = A[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];
                if (i == j) {
                    L[i][j] = sum > 0 ? Math.sqrt(sum) : 1e-10;
                } else {
                    L[i][j] = L[j][j] > 0 ? sum / L[j][j] : 0;
                }
            }
        }
        return L;
    }
}
