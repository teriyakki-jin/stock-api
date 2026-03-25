package com.nh.stockapi.domain.stock.client;

import com.nh.stockapi.domain.stock.dto.OhlcvBar;
import com.nh.stockapi.domain.stock.dto.StockPriceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Yahoo Finance Chart API v8 클라이언트.
 * 한국 주식 심볼 예시: 005930.KS (KOSPI), 035420.KQ (KOSDAQ)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d";
    private static final String HISTORY_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1y";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Optional<StockPriceData> fetchPrice(String yahooSymbol) {
        try {
            String url = CHART_URL.replace("{symbol}", yahooSymbol);
            String json = restTemplate.getForObject(url, String.class);
            return Optional.ofNullable(parsePrice(json, yahooSymbol));
        } catch (Exception e) {
            log.warn("[Yahoo Finance] 시세 조회 실패: {} - {}", yahooSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    /** 최근 1년 일별 OHLCV 히스토리 조회 */
    public List<OhlcvBar> fetchHistory(String yahooSymbol) {
        try {
            String url = HISTORY_URL.replace("{symbol}", yahooSymbol);
            String json = restTemplate.getForObject(url, String.class);
            return parseHistory(json, yahooSymbol);
        } catch (Exception e) {
            log.warn("[Yahoo Finance] 히스토리 조회 실패: {} - {}", yahooSymbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private StockPriceData parsePrice(String json, String symbol) {
        try {
            JsonNode meta = objectMapper.readTree(json)
                    .path("chart")
                    .path("result")
                    .get(0)
                    .path("meta");

            if (meta.isMissingNode()) {
                log.warn("[Yahoo Finance] meta 노드 없음: {}", symbol);
                return null;
            }

            BigDecimal price = toBigDecimal(meta.path("regularMarketPrice").asDouble());
            double changePercent = meta.path("regularMarketChangePercent").asDouble();
            long volume = meta.path("regularMarketVolume").asLong();
            BigDecimal dayHigh = toBigDecimal(meta.path("regularMarketDayHigh").asDouble());
            BigDecimal dayLow = toBigDecimal(meta.path("regularMarketDayLow").asDouble());

            return new StockPriceData(price, changePercent, volume, dayHigh, dayLow);

        } catch (Exception e) {
            log.warn("[Yahoo Finance] 응답 파싱 실패: {} - {}", symbol, e.getMessage());
            return null;
        }
    }

    private List<OhlcvBar> parseHistory(String json, String symbol) {
        List<OhlcvBar> bars = new ArrayList<>();
        try {
            JsonNode result = objectMapper.readTree(json)
                    .path("chart").path("result").get(0);

            JsonNode timestamps = result.path("timestamp");
            JsonNode quote = result.path("indicators").path("quote").get(0);

            if (timestamps.isMissingNode() || quote == null) return bars;

            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            ZoneId kst = ZoneId.of("Asia/Seoul");
            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i).isNull()) continue;

                LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong())
                        .atZone(kst).toLocalDate();
                bars.add(new OhlcvBar(
                        date,
                        toBigDecimal(opens.get(i).asDouble()),
                        toBigDecimal(highs.get(i).asDouble()),
                        toBigDecimal(lows.get(i).asDouble()),
                        toBigDecimal(closes.get(i).asDouble()),
                        volumes.get(i).asLong()
                ));
            }
        } catch (Exception e) {
            log.warn("[Yahoo Finance] 히스토리 파싱 실패: {} - {}", symbol, e.getMessage());
        }
        return bars;
    }

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
