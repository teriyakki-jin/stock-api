package com.nh.stockapi.domain.stock.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.stock.dto.StockResponse;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.repository.StockRepository;
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

    @Transactional(readOnly = true)
    public List<StockResponse> searchStocks(String keyword) {
        List<Stock> stocks = keyword == null || keyword.isBlank()
                ? stockRepository.findAll()
                : stockRepository.findByNameContainingIgnoreCaseOrTickerContaining(keyword, keyword);

        return stocks.stream()
                .map(stock -> {
                    BigDecimal price = stockPriceService.getCurrentPriceOrBase(stock.getTicker(), stock.getBasePrice());
                    return StockResponse.of(stock, price);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(String ticker) {
        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        BigDecimal price = stockPriceService.getCurrentPriceOrBase(ticker, stock.getBasePrice());
        return StockResponse.of(stock, price);
    }

    // 주문 서비스에서 사용
    public Stock findStockOrThrow(String ticker) {
        return stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
    }

    // 현재가 직접 조회 (주문 체결 시 사용)
    public BigDecimal getCurrentPrice(String ticker, BigDecimal basePrice) {
        return stockPriceService.getCurrentPriceOrBase(ticker, basePrice);
    }
}
