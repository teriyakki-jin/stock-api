package com.nh.stockapi.domain.stock.entity;

import com.nh.stockapi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;        // 종목코드 (예: 005930)

    @Column(nullable = false, length = 100)
    private String name;          // 종목명

    @Column(nullable = false, length = 20)
    private String market;        // KOSPI / KOSDAQ

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal basePrice; // 기준가 (당일 시초가)

    @Column(nullable = false)
    private Long totalShares;     // 상장 주식 수

    @Builder
    public Stock(String ticker, String name, String market, BigDecimal basePrice, Long totalShares) {
        this.ticker = ticker;
        this.name = name;
        this.market = market;
        this.basePrice = basePrice;
        this.totalShares = totalShares;
    }

    public void updateBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }
}
