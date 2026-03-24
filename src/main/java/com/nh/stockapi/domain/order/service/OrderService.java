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
import com.nh.stockapi.domain.order.entity.OrderStatus;
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

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final AccountService accountService;
    private final StockService stockService;

    @Transactional
    public OrderResponse buy(Long accountId, Member member, OrderRequest request) {
        if (request.orderType() == OrderType.BUY_LIMIT) {
            return buyLimit(accountId, member, request);
        }
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

    private OrderResponse buyLimit(Long accountId, Member member, OrderRequest request) {
        if (request.limitPrice() == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        Account account = accountService.findWithLock(accountId);
        validateOwner(account, member);

        Stock stock = stockService.findStockOrThrow(request.ticker());
        account.withdraw(request.limitPrice().multiply(BigDecimal.valueOf(request.quantity())));

        Order order = orderRepository.save(
                Order.pendingLimit(account, stock, OrderType.BUY_LIMIT,
                        request.quantity(), request.limitPrice()));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse sell(Long accountId, Member member, OrderRequest request) {
        if (request.orderType() == OrderType.SELL_LIMIT) {
            return sellLimit(accountId, member, request);
        }
        Account account = accountService.findWithLock(accountId);
        validateOwner(account, member);

        Stock stock = stockService.findStockOrThrow(request.ticker());
        BigDecimal unitPrice = stockService.getCurrentPrice(stock.getTicker(), stock.getBasePrice());

        Holding holding = holdingRepository
                .findByAccountIdAndStockIdWithLock(accountId, stock.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_HOLDINGS));
        holding.subtractQuantity(request.quantity());

        account.deposit(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));

        Order order = orderRepository.save(
                Order.execute(account, stock, OrderType.SELL, request.quantity(), unitPrice));
        return OrderResponse.from(order);
    }

    private OrderResponse sellLimit(Long accountId, Member member, OrderRequest request) {
        if (request.limitPrice() == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        Account account = accountService.findWithLock(accountId);
        validateOwner(account, member);

        Stock stock = stockService.findStockOrThrow(request.ticker());
        Holding holding = holdingRepository
                .findByAccountIdAndStockIdWithLock(accountId, stock.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_HOLDINGS));
        if (holding.getQuantity() < request.quantity()) {
            throw new CustomException(ErrorCode.INSUFFICIENT_HOLDINGS);
        }

        Order order = orderRepository.save(
                Order.pendingLimit(account, stock, OrderType.SELL_LIMIT,
                        request.quantity(), request.limitPrice()));
        return OrderResponse.from(order);
    }

    @Transactional
    public void cancelOrder(Long accountId, Member member, Long orderId) {
        Account account = accountService.findWithLock(accountId);
        validateOwner(account, member);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CustomException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        if (order.getOrderType() == OrderType.BUY_LIMIT) {
            account.deposit(order.getLimitPrice().multiply(BigDecimal.valueOf(order.getRemainingQty())));
        }
        order.cancel();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrderHistory(Long accountId, Member member, Pageable pageable) {
        Account account = findAccountAndValidate(accountId, member);
        return orderRepository.findByAccountId(account.getId(), pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getPendingOrders(Long accountId, Member member) {
        Account account = findAccountAndValidate(accountId, member);
        return orderRepository.findPendingByAccountId(account.getId()).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> getHoldings(Long accountId, Member member) {
        Account account = findAccountAndValidate(accountId, member);
        return holdingRepository.findByAccountIdWithStock(account.getId()).stream()
                .map(h -> {
                    BigDecimal price = stockService.getCurrentPrice(h.getStock().getTicker(), h.getStock().getBasePrice());
                    return HoldingResponse.of(h, price);
                }).toList();
    }

    private Account findAccountAndValidate(Long accountId, Member member) {
        Account account = accountService.findById(accountId);
        validateOwner(account, member);
        return account;
    }

    private void validateOwner(Account account, Member member) {
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
