package com.nh.stockapi.domain.order.dto;

import com.nh.stockapi.domain.order.entity.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank(message = "종목코드는 필수입니다.")
        String ticker,

        @NotNull(message = "주문 유형은 필수입니다.")
        OrderType orderType,

        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "최소 1주 이상 주문해야 합니다.")
        Long quantity,

        // 지정가 주문(BUY_LIMIT / SELL_LIMIT) 시 필수, 시장가 시 null
        @DecimalMin(value = "0.01", message = "지정가는 0보다 커야 합니다.")
        BigDecimal limitPrice
) {}
