package com.nh.stockapi.domain.order.entity;

import com.nh.stockapi.common.entity.BaseEntity;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_account", columnList = "account_id"),
        @Index(name = "idx_order_stock", columnList = "stock_id"),
        @Index(name = "idx_order_status_stock", columnList = "status, stock_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderStatus status;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal limitPrice;   // null = 시장가

    @Column
    private Long remainingQty;       // 미체결 수량 (지정가 전용)

    // ─── 팩토리: 시장가 즉시 체결 ────────────────────────────────────────────
    public static Order execute(Account account, Stock stock, OrderType orderType,
                                Long quantity, BigDecimal unitPrice) {
        Order o = new Order();
        o.account = account;
        o.stock = stock;
        o.orderType = orderType;
        o.quantity = quantity;
        o.unitPrice = unitPrice;
        o.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        o.status = OrderStatus.EXECUTED;
        return o;
    }

    // ─── 팩토리: 지정가 대기 주문 ────────────────────────────────────────────
    public static Order pendingLimit(Account account, Stock stock, OrderType orderType,
                                     Long quantity, BigDecimal limitPrice) {
        Order o = new Order();
        o.account = account;
        o.stock = stock;
        o.orderType = orderType;
        o.quantity = quantity;
        o.unitPrice = limitPrice;
        o.totalAmount = limitPrice.multiply(BigDecimal.valueOf(quantity));
        o.status = OrderStatus.PENDING;
        o.limitPrice = limitPrice;
        o.remainingQty = quantity;
        return o;
    }

    // ─── 상태 전이 ────────────────────────────────────────────────────────────
    public void executeLimit(BigDecimal fillPrice) {
        this.unitPrice = fillPrice;
        this.totalAmount = fillPrice.multiply(BigDecimal.valueOf(this.quantity));
        this.status = OrderStatus.EXECUTED;
        this.remainingQty = 0L;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.remainingQty = 0L;
    }
}
