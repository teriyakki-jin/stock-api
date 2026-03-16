package com.nh.stockapi.domain.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 현재가 캐시 서비스.
 * Key: stock:price:{ticker}  / TTL: 10초 (실시간성 보장)
 * 실제 환경에서는 증권사 시세 피드(WebSocket/FTP)가 이 캐시를 갱신한다.
 */
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final long PRICE_TTL_SECONDS = 10L;

    private final StringRedisTemplate redisTemplate;

    public Optional<BigDecimal> getCurrentPrice(String ticker) {
        String value = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + ticker);
        return Optional.ofNullable(value).map(BigDecimal::new);
    }

    public void updatePrice(String ticker, BigDecimal price) {
        redisTemplate.opsForValue().set(
                PRICE_KEY_PREFIX + ticker,
                price.toPlainString(),
                PRICE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * 현재가가 없으면 basePrice를 fallback으로 사용.
     */
    public BigDecimal getCurrentPriceOrBase(String ticker, BigDecimal basePrice) {
        return getCurrentPrice(ticker).orElse(basePrice);
    }
}
