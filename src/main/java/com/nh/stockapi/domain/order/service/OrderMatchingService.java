package com.nh.stockapi.domain.order.service;

import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.entity.Order;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 지정가 주문 체결 엔진.
 * StockPriceScheduler가 시세 갱신 후 호출 → PENDING 주문을 현재가와 비교해 체결 처리.
 *
 * 체결 조건:
 *  - BUY_LIMIT:  limitPrice >= currentPrice  (지정가 이하로 내려왔을 때 체결)
 *  - SELL_LIMIT: limitPrice <= currentPrice  (지정가 이상으로 올랐을 때 체결)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final SseEmitterService sseEmitterService;

    @Transactional
    public void matchOrders(String ticker, BigDecimal currentPrice) {
        orderRepository.findPendingBuyLimitToFill(ticker, currentPrice)
                .forEach(order -> executeBuyLimit(order, currentPrice));

        orderRepository.findPendingSellLimitToFill(ticker, currentPrice)
                .forEach(order -> executeSellLimit(order, currentPrice));
    }

    private void executeBuyLimit(Order order, BigDecimal fillPrice) {
        // 보유 수량 증가 (없으면 신규 생성)
        Holding holding = holdingRepository
                .findByAccountIdAndStockId(order.getAccount().getId(), order.getStock().getId())
                .orElseGet(() -> holdingRepository.save(
                        Holding.of(order.getAccount(), order.getStock(), 0L, fillPrice)));
        holding.addQuantity(order.getQuantity(), fillPrice);

        // 지정가 > 체결가인 경우 차액 환급
        BigDecimal refund = order.getLimitPrice().subtract(fillPrice)
                .multiply(BigDecimal.valueOf(order.getQuantity()));
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            order.getAccount().deposit(refund);
        }

        order.executeLimit(fillPrice);

        notifyFill(order, fillPrice);
        log.info("[체결엔진] BUY_LIMIT 체결: {} {}주 @{}", order.getStock().getTicker(), order.getQuantity(), fillPrice);
    }

    private void executeSellLimit(Order order, BigDecimal fillPrice) {
        // 보유 수량 차감
        holdingRepository
                .findByAccountIdAndStockIdWithLock(order.getAccount().getId(), order.getStock().getId())
                .ifPresent(h -> h.subtractQuantity(order.getQuantity()));

        // 매도 대금 입금
        order.getAccount().deposit(fillPrice.multiply(BigDecimal.valueOf(order.getQuantity())));

        order.executeLimit(fillPrice);

        notifyFill(order, fillPrice);
        log.info("[체결엔진] SELL_LIMIT 체결: {} {}주 @{}", order.getStock().getTicker(), order.getQuantity(), fillPrice);
    }

    private void notifyFill(Order order, BigDecimal fillPrice) {
        String email = order.getAccount().getMember().getEmail();
        sseEmitterService.send(email, Map.of(
                "event", "ORDER_FILLED",
                "orderId", order.getId(),
                "ticker", order.getStock().getTicker(),
                "stockName", order.getStock().getName(),
                "orderType", order.getOrderType().name(),
                "quantity", order.getQuantity(),
                "fillPrice", fillPrice
        ));
    }
}
