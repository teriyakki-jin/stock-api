package com.nh.stockapi.domain.stock.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 한국 주식시장(KRX) 거래시간 판별.
 * - 정규장: 09:00 ~ 15:30 KST (월~금)
 * - 공휴일은 단순화하여 주말만 제외
 */
@Service
public class MarketHoursService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN  = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    /** 현재 정규장 거래 시간인지 */
    public boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now(KST);
        DayOfWeek dow = now.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }

    /**
     * 현재 intraday 변동성 배율.
     * 시가·종가 근처에서 변동성이 높다.
     */
    public double intradayVolatilityMultiplier() {
        LocalTime now = LocalDateTime.now(KST).toLocalTime();
        if (now.isBefore(LocalTime.of(9, 30)))  return 1.8;  // 장 시작 직후 (갭업/다운)
        if (now.isAfter(LocalTime.of(15, 0)))   return 1.4;  // 마감 30분 전
        if (now.isAfter(LocalTime.of(10, 30)) && now.isBefore(LocalTime.of(14, 0))) return 0.8;  // 점심 시간대 소강
        return 1.0;
    }

    /** 장 외 시뮬레이션 변동성 배율 (야간/주말 — 조용하게) */
    public double afterHoursVolatilityMultiplier() {
        return 0.35;
    }
}
