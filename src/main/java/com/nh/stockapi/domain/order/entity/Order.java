package com.nh.stockapi.domain.order.entity;

import com.nh.stockapi.common.entity.BaseEntity;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_account", columnList = "account_id"),
        @Index(name = "idx_order_stock", columnList = "stock_id")
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
    @Column(nullable = false, length = 10)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(nullable = false)
    private Long quantity;           // 주문 수량

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;    // 체결 단가

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;  // 체결 금액 (unitPrice × quantity)

    @Builder
    private Order(Account account, Stock stock, OrderType orderType, Long quantity,
                  BigDecimal unitPrice) {
        this.account = account;
        this.stock = stock;
        this.orderType = orderType;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.status = OrderStatus.EXECUTED; // 단순 즉시 체결 모델
    }

    public static Order execute(Account account, Stock stock, OrderType orderType,
                                Long quantity, BigDecimal unitPrice) {
        return Order.builder()
                .account(account)
                .stock(stock)
                .orderType(orderType)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }
}
