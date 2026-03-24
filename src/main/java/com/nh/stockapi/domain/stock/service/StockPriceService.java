package com.nh.stockapi.domain.stock.service;

import com.nh.stockapi.domain.stock.dto.StockPriceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 실시간 시세 캐시 서비스.
 * Key: stock:price:{ticker}  /  TTL: 70초 (스케줄러 주기 60s + 여유)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final long PRICE_TTL_SECONDS = 70L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<StockPriceData> getCurrentPriceData(String ticker) {
        String json = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + ticker);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, StockPriceData.class));
        } catch (Exception e) {
            log.warn("[시세 캐시] 역직렬화 실패: {}", ticker, e);
            return Optional.empty();
        }
    }

    public void updatePrice(String ticker, StockPriceData data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(
                    PRICE_KEY_PREFIX + ticker, json, PRICE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[시세 캐시] 저장 실패: {}", ticker, e);
        }
    }

    /** 주문 체결 시 fallback 포함 현재가 조회 */
    public BigDecimal getCurrentPriceOrBase(String ticker, BigDecimal basePrice) {
        return getCurrentPriceData(ticker)
                .map(StockPriceData::price)
                .orElse(basePrice);
    }
}
