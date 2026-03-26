package com.nh.stockapi.domain.alert.entity;

import com.nh.stockapi.domain.account.entity.Account;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts", indexes = {
    @Index(name = "idx_price_alerts_ticker_active", columnList = "ticker, active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceAlert {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private AlertCondition condition;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean triggered = false;

    @Column(nullable = false)
    private boolean acknowledged = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime triggeredAt;

    public static PriceAlert create(Account account, String ticker, BigDecimal targetPrice, AlertCondition condition) {
        PriceAlert alert = new PriceAlert();
        alert.account = account;
        alert.ticker = ticker;
        alert.targetPrice = targetPrice;
        alert.condition = condition;
        return alert;
    }

    public boolean checkAndTrigger(BigDecimal currentPrice) {
        if (!active || triggered) return false;
        boolean hit = (condition == AlertCondition.GTE && currentPrice.compareTo(targetPrice) >= 0)
                   || (condition == AlertCondition.LTE && currentPrice.compareTo(targetPrice) <= 0);
        if (hit) {
            this.triggered = true;
            this.active = false;
            this.triggeredAt = LocalDateTime.now();
        }
        return hit;
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
