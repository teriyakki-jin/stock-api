package com.nh.stockapi.domain.stock.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.stock.client.YahooFinanceClient;
import com.nh.stockapi.domain.stock.dto.OhlcvBar;
import com.nh.stockapi.domain.stock.dto.StockPriceData;
import com.nh.stockapi.domain.stock.dto.StockResponse;
import com.nh.stockapi.domain.stock.dto.StockTechnicalResponse;
import com.nh.stockapi.domain.stock.dto.TechnicalData;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.repository.StockRepository;
import com.nh.stockapi.domain.stock.service.TechnicalIndicatorService;
import com.nh.stockapi.domain.stock.service.VolatilityCalibrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;
    private final YahooFinanceClient yahooFinanceClient;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final VolatilityCalibrationService calibrationService;

    @Transactional(readOnly = true)
    public List<StockResponse> searchStocks(String keyword) {
        List<Stock> stocks = keyword == null || keyword.isBlank()
                ? stockRepository.findAll()
                : stockRepository.findByNameContainingIgnoreCaseOrTickerContaining(keyword, keyword);

        return stocks.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(String ticker) {
        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        return toResponse(stock);
    }

    public Stock findStockOrThrow(String ticker) {
        return stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
    }

    /** 기술적 분석 + 1년 히스토리 */
    @Transactional(readOnly = true)
    public StockTechnicalResponse getTechnicals(String ticker) {
        Stock stock = findStockOrThrow(ticker);
        List<OhlcvBar> history = yahooFinanceClient.fetchHistory(stock.getYahooSymbol());
        TechnicalData technicals = history.isEmpty()
                ? new TechnicalData(50, 0, 0, 0, stock.getBasePrice(), stock.getBasePrice(), stock.getBasePrice(), "NEUTRAL")
                : technicalIndicatorService.calculate(history);

        double annualVol   = calibrationService.getAnnualVol()
                .getOrDefault(ticker, 0.0) * 100;
        double annualDrift = calibrationService.getAnnualDrift()
                .getOrDefault(ticker, 0.0) * 100;

        return new StockTechnicalResponse(
                stock.getTicker(), stock.getName(),
                history, technicals,
                Math.round(annualVol * 100.0) / 100.0,
                Math.round(annualDrift * 100.0) / 100.0
        );
    }

    /** 주문 체결 시 현재가 조회 (basePrice fallback 포함) */
    public BigDecimal getCurrentPrice(String ticker, BigDecimal basePrice) {
        return stockPriceService.getCurrentPriceOrBase(ticker, basePrice);
    }

    private StockResponse toResponse(Stock stock) {
        return stockPriceService.getCurrentPriceData(stock.getTicker())
                .map(data -> StockResponse.of(stock, data))
                .orElse(StockResponse.ofFallback(stock));
    }
}
