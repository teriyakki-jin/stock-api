package com.nh.stockapi.domain.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull(message = "금액은 필수입니다.")
        @DecimalMin(value = "1000", message = "최소 입금액은 1,000원입니다.")
        BigDecimal amount
) {}
