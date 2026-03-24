package com.nh.stockapi.domain.stock.scheduler;

import com.nh.stockapi.domain.stock.client.YahooFinanceClient;
import com.nh.stockapi.domain.stock.repository.StockRepository;
import com.nh.stockapi.domain.stock.service.StockPricePublisher;
import com.nh.stockapi.domain.stock.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 Yahoo Finance에서 실시간 시세를 조회해 Redis 캐시를 갱신하고
 * WebSocket 구독자에게 브로드캐스트한다.
 * test 프로파일에서는 실행되지 않는다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final YahooFinanceClient yahooFinanceClient;
    private final StockPriceService stockPriceService;
    private final StockPricePublisher stockPricePublisher;

    @Scheduled(fixedRate = 60_000, initialDelay = 5_000)
    public void updateAllPrices() {
        var stocks = stockRepository.findAll();

        long success = stocks.stream()
                .filter(stock -> {
                    String symbol = stock.getYahooSymbol();
                    return yahooFinanceClient.fetchPrice(symbol)
                            .map(data -> {
                                stockPriceService.updatePrice(stock.getTicker(), data);
                                stockPricePublisher.publish(stock.getTicker(), data);
                                return true;
                            })
                            .orElse(false);
                })
                .count();

        log.info("[시세 스케줄러] 갱신·브로드캐스트 완료 {}/{} 종목", success, stocks.size());
    }
}
