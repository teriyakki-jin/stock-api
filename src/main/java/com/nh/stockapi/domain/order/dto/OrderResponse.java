package com.nh.stockapi.domain.order.dto;

import com.nh.stockapi.domain.order.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String ticker,
        String stockName,
        String orderType,
        String status,
        Long quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        LocalDateTime executedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStock().getTicker(),
                order.getStock().getName(),
                order.getOrderType().name(),
                order.getStatus().name(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
