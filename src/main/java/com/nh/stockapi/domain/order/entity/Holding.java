package com.nh.stockapi.domain.order.entity;

import com.nh.stockapi.common.entity.BaseEntity;
import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "holdings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "stock_id"}),
        indexes = @Index(name = "idx_holding_account", columnList = "account_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private Long quantity;          // 보유 수량

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal avgPrice;    // 평균 매수 단가

    @Builder
    private Holding(Account account, Stock stock, Long quantity, BigDecimal avgPrice) {
        this.account = account;
        this.stock = stock;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }

    public static Holding of(Account account, Stock stock, Long quantity, BigDecimal price) {
        return Holding.builder()
                .account(account)
                .stock(stock)
                .quantity(quantity)
                .avgPrice(price)
                .build();
    }

    /**
     * 매수: 평균 단가 재계산 후 수량 증가
     */
    public void addQuantity(Long qty, BigDecimal price) {
        BigDecimal prevTotal = avgPrice.multiply(BigDecimal.valueOf(this.quantity));
        BigDecimal newTotal = price.multiply(BigDecimal.valueOf(qty));
        this.quantity += qty;
        this.avgPrice = prevTotal.add(newTotal)
                .divide(BigDecimal.valueOf(this.quantity), 2, RoundingMode.HALF_UP);
    }

    /**
     * 매도: 수량 감소 (부족하면 예외)
     */
    public void subtractQuantity(Long qty) {
        if (this.quantity < qty) {
            throw new CustomException(ErrorCode.INSUFFICIENT_HOLDINGS);
        }
        this.quantity -= qty;
    }

    public boolean isEmpty() {
        return this.quantity == 0;
    }
}
