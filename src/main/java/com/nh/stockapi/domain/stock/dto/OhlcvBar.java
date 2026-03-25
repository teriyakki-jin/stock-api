package com.nh.stockapi.domain.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 일별 OHLCV 캔들 데이터 */
public record OhlcvBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {}
