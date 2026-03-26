package com.nh.stockapi.domain.alert.dto;

import com.nh.stockapi.domain.alert.entity.AlertCondition;
import com.nh.stockapi.domain.alert.entity.PriceAlert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceAlertResponse(
    Long id,
    String ticker,
    BigDecimal targetPrice,
    AlertCondition condition,
    boolean active,
    boolean triggered,
    boolean acknowledged,
    LocalDateTime createdAt,
    LocalDateTime triggeredAt
) {
    public static PriceAlertResponse from(PriceAlert a) {
        return new PriceAlertResponse(
            a.getId(), a.getTicker(), a.getTargetPrice(), a.getCondition(),
            a.isActive(), a.isTriggered(), a.isAcknowledged(),
            a.getCreatedAt(), a.getTriggeredAt()
        );
    }
}
