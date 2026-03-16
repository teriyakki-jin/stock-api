package com.nh.stockapi.domain.order.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.service.AccountService;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.dto.HoldingResponse;
import com.nh.stockapi.domain.order.dto.OrderRequest;
import com.nh.stockapi.domain.order.dto.OrderResponse;
import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.entity.Order;
import com.nh.stockapi.domain.order.entity.OrderType;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.order.repository.OrderRepository;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 서비스 — 매수/매도 동시성 제어
 *
 * 동시성 전략:
 *  - Account: PESSIMISTIC_WRITE 락 (잔액 차감/증가)
 *  - Holding: PESSIMISTIC_WRITE 락 (보유 수량 차감/증가)
 *  - 두 락의 순서를 Account → Holding으로 고정해 데드락 방지
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final AccountService accountService;
    private final StockService stockService;

    /**
     * 매수 주문
     * 1. Account 락 → 잔액 차감
     * 2. Holding 락 → 보유 수량 증가 (없으면 신규 생성)
     * 3. Order 저장
     */
    @Transactional
    public OrderResponse buy(Long accountId, Member member, OrderRequest request) {
        Account account = accountService.findWithLock(accountId);
        validateOwner(account, member);

        Stock stock = stockService.findStockOrThrow(request.ticker());
        BigDecimal unitPrice = stockService.getCurrentPrice(stock.getTicker(), stock.getBasePrice());
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));

        account.withdraw(totalAmount);

        Holding holding = holdingRepository
                .findByAccountIdAndStockIdWithLock(accountId, stock.getId())
                .orElseGet(() -> holdingRepository.save(
                        Holding.of(account, stock, 0L, unitPrice)));

        holding.addQuantity(request.quantity(), unitPrice);

        Order order = orderRepository.save(
                Order.execute(account, stock, OrderType.BUY, request.quantity(), unitPrice));

        return OrderResponse.from(order);
    }

    /**
     * 매도 주문
     * 1. Holding 락 → 보유 수량 차감
     * 2. Account 락 → 잔액 증가
     * 3. Order 저장
     *
     * 매도는 Holding 먼저 락 — Account 락은 withdraw 없이 deposit만 하므로
     * 매수와 데드락이 발생하지 않도록 순서 설계
     */
    @Transactional
    public OrderResponse sell(Long accountId, Member member, OrderRequest request) {
        Account account = accountService.findWithLock(accountId);
        validateOwner(account, member);

        Stock stock = stockService.findStockOrThrow(request.ticker());
        BigDecimal unitPrice = stockService.getCurrentPrice(stock.getTicker(), stock.getBasePrice());
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));

        Holding holding = holdingRepository
                .findByAccountIdAndStockIdWithLock(accountId, stock.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_HOLDINGS));

        holding.subtractQuantity(request.quantity());

        account.deposit(totalAmount);

        Order order = orderRepository.save(
                Order.execute(account, stock, OrderType.SELL, request.quantity(), unitPrice));

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrderHistory(Long accountId, Member member, Pageable pageable) {
        Account account = findAccountAndValidate(accountId, member);
        return orderRepository.findByAccountId(account.getId(), pageable)
                .map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> getHoldings(Long accountId, Member member) {
        Account account = findAccountAndValidate(accountId, member);
        return holdingRepository.findByAccountIdWithStock(account.getId()).stream()
                .map(holding -> {
                    BigDecimal price = stockService.getCurrentPrice(
                            holding.getStock().getTicker(), holding.getStock().getBasePrice());
                    return HoldingResponse.of(holding, price);
                })
                .toList();
    }

    private Account findAccountAndValidate(Long accountId, Member member) {
        Account account = accountService.findById(accountId);  // 읽기 전용 — 락 불필요
        validateOwner(account, member);
        return account;
    }

    private void validateOwner(Account account, Member member) {
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
