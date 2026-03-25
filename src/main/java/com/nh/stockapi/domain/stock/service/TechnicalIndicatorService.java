package com.nh.stockapi.domain.stock.service;

import com.nh.stockapi.domain.stock.dto.OhlcvBar;
import com.nh.stockapi.domain.stock.dto.TechnicalData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 기술적 분석 지표 계산 서비스.
 * - RSI (14기간)
 * - MACD (12, 26, 9)
 * - 볼린저밴드 (20기간, ±2σ)
 */
@Service
public class TechnicalIndicatorService {

    private static final int RSI_PERIOD      = 14;
    private static final int MACD_FAST       = 12;
    private static final int MACD_SLOW       = 26;
    private static final int MACD_SIGNAL     = 9;
    private static final int BB_PERIOD       = 20;
    private static final double BB_MULT      = 2.0;

    /**
     * OHLCV 시계열에서 최신 기술적 지표를 계산한다.
     * bars는 오래된 순서로 정렬되어 있어야 한다 (index 0 = 가장 오래된 날).
     */
    public TechnicalData calculate(List<OhlcvBar> bars) {
        if (bars.size() < MACD_SLOW + MACD_SIGNAL) {
            return neutralIndicators(bars.isEmpty() ? BigDecimal.ZERO : bars.get(bars.size() - 1).close());
        }

        double[] closes = bars.stream().mapToDouble(b -> b.close().doubleValue()).toArray();

        double rsi          = computeRsi(closes);
        double[] macdResult = computeMacd(closes);
        BigDecimal[] bb     = computeBollingerBands(closes);
        String signal       = deriveSignal(rsi, macdResult[2]);

        return new TechnicalData(
                round2(rsi),
                round4(macdResult[0]),
                round4(macdResult[1]),
                round4(macdResult[2]),
                bb[0], bb[1], bb[2],
                signal
        );
    }

    // ── RSI ──────────────────────────────────────────────────────────────────

    private double computeRsi(double[] closes) {
        if (closes.length < RSI_PERIOD + 1) return 50.0;

        // Wilder's smoothed EMA 방식
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= RSI_PERIOD; i++) {
            double delta = closes[i] - closes[i - 1];
            if (delta > 0) avgGain += delta; else avgLoss -= delta;
        }
        avgGain /= RSI_PERIOD;
        avgLoss /= RSI_PERIOD;

        for (int i = RSI_PERIOD + 1; i < closes.length; i++) {
            double delta = closes[i] - closes[i - 1];
            double gain  = Math.max(delta, 0);
            double loss  = Math.max(-delta, 0);
            avgGain = (avgGain * (RSI_PERIOD - 1) + gain) / RSI_PERIOD;
            avgLoss = (avgLoss * (RSI_PERIOD - 1) + loss) / RSI_PERIOD;
        }

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    // ── MACD ─────────────────────────────────────────────────────────────────

    private double[] computeMacd(double[] closes) {
        double[] ema12 = ema(closes, MACD_FAST);
        double[] ema26 = ema(closes, MACD_SLOW);

        // MACD line (ema12 - ema26, 길이 = closes.length - MACD_SLOW + 1)
        int macdLen = ema12.length - (MACD_SLOW - MACD_FAST);
        double[] macdLine = new double[Math.min(ema12.length, macdLen)];
        int offset = ema12.length - ema26.length;
        for (int i = 0; i < ema26.length; i++) {
            macdLine[i] = ema12[i + offset] - ema26[i];
        }

        // Signal line: EMA(9) of MACD
        double[] signalLine = ema(macdLine, MACD_SIGNAL);

        double macdVal    = macdLine[macdLine.length - 1];
        double signalVal  = signalLine[signalLine.length - 1];
        double histogram  = macdVal - signalVal;

        return new double[]{macdVal, signalVal, histogram};
    }

    /** 지수이동평균 (EMA). multiplier = 2/(period+1) */
    private double[] ema(double[] data, int period) {
        double[] result = new double[data.length - period + 1];
        double multiplier = 2.0 / (period + 1);

        // 첫 EMA = SMA
        double sum = 0;
        for (int i = 0; i < period; i++) sum += data[i];
        result[0] = sum / period;

        for (int i = period; i < data.length; i++) {
            result[i - period + 1] = (data[i] - result[i - period]) * multiplier + result[i - period];
        }
        return result;
    }

    // ── 볼린저밴드 ─────────────────────────────────────────────────────────────

    private BigDecimal[] computeBollingerBands(double[] closes) {
        int n = closes.length;
        if (n < BB_PERIOD) {
            BigDecimal last = BigDecimal.valueOf(closes[n - 1]);
            return new BigDecimal[]{last, last, last};
        }

        // 최근 BB_PERIOD 종가의 SMA, 표준편차
        double sum = 0;
        for (int i = n - BB_PERIOD; i < n; i++) sum += closes[i];
        double sma = sum / BB_PERIOD;

        double sqSum = 0;
        for (int i = n - BB_PERIOD; i < n; i++) sqSum += Math.pow(closes[i] - sma, 2);
        double stdDev = Math.sqrt(sqSum / BB_PERIOD);

        return new BigDecimal[]{
                toBd(sma + BB_MULT * stdDev),
                toBd(sma),
                toBd(sma - BB_MULT * stdDev)
        };
    }

    // ── 시그널 도출 ────────────────────────────────────────────────────────────

    private String deriveSignal(double rsi, double histogram) {
        boolean rsiBuy  = rsi < 35;
        boolean rsiSell = rsi > 65;
        boolean macdBuy  = histogram > 0;
        boolean macdSell = histogram < 0;

        if (rsiBuy  && macdBuy)  return "BUY";
        if (rsiSell && macdSell) return "SELL";
        return "NEUTRAL";
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private TechnicalData neutralIndicators(BigDecimal price) {
        return new TechnicalData(50, 0, 0, 0, price, price, price, "NEUTRAL");
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
    private BigDecimal toBd(double v) { return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP); }
}
