package com.nh.stockapi.domain.stock.scheduler;

import com.nh.stockapi.domain.order.service.OrderMatchingService;
import com.nh.stockapi.domain.stock.client.YahooFinanceClient;
import com.nh.stockapi.domain.stock.dto.StockPriceData;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.repository.StockRepository;
import com.nh.stockapi.domain.stock.service.StockPricePublisher;
import com.nh.stockapi.domain.stock.service.StockPriceService;
import com.nh.stockapi.infrastructure.kis.KisRestClient;
import com.nh.stockapi.infrastructure.kis.KisTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 실시간 시세 스케줄러 (운영/스테이징)
 *
 * ▶ KIS OpenAPI가 설정된 경우 (kis.app-key 비어있지 않음):
 *   KIS API 우선 조회 → 실패 시 Yahoo Finance fallback
 *
 * ▶ KIS 미설정 시:
 *   Yahoo Finance 단독 사용
 *
 * 10초마다 시세 조회 → Redis 갱신 → WebSocket 브로드캐스트 → 지정가 체결
 */
@Slf4j
@Component
@Profile("!test & !local")
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockRepository      stockRepository;
    private final YahooFinanceClient   yahooFinanceClient;
    private final KisRestClient        kisRestClient;
    private final KisTokenManager      kisTokenManager;
    private final StockPriceService    stockPriceService;
    private final StockPricePublisher  stockPricePublisher;
    private final OrderMatchingService orderMatchingService;

    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void updateAllPrices() {
        List<Stock> stocks = stockRepository.findAll();

        long success = stocks.stream()
                .filter(this::fetchAndPublish)
                .count();

        log.debug("[시세 스케줄러] {}/{} 종목 갱신", success, stocks.size());
    }

    private boolean fetchAndPublish(Stock stock) {
        Optional<StockPriceData> priceData = Optional.empty();

        // 1순위: KIS API (설정된 경우)
        if (kisTokenManager.isConfigured()) {
            priceData = kisRestClient.getCurrentPrice(stock.getTicker())
                    .map(kis -> new StockPriceData(
                            kis.currentPrice(),
                            kis.changeRate(),
                            kis.volume(),
                            kis.dayHigh(),
                            kis.dayLow()
                    ));
        }

        // 2순위: Yahoo Finance
        if (priceData.isEmpty()) {
            priceData = yahooFinanceClient.fetchPrice(stock.getYahooSymbol());
        }

        return priceData.map(data -> {
            stockPriceService.updatePrice(stock.getTicker(), data);
            stockPricePublisher.publish(stock.getTicker(), data);
            orderMatchingService.matchOrders(stock.getTicker(), data.price());
            return true;
        }).orElse(false);
    }
}
