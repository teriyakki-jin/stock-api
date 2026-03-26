package com.nh.stockapi.domain.alert.dto;

import com.nh.stockapi.domain.alert.entity.AlertCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceAlertRequest(
    @NotBlank String ticker,
    @NotNull @DecimalMin("1") BigDecimal targetPrice,
    @NotNull AlertCondition condition
) {}
