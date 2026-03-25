package com.nh.stockapi.domain.stock.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.stock.dto.StockResponse;
import com.nh.stockapi.domain.stock.dto.StockTechnicalResponse;
import com.nh.stockapi.domain.stock.service.MarketHoursService;
import com.nh.stockapi.domain.stock.service.StockService;
import com.nh.stockapi.domain.stock.service.VolatilityCalibrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "종목", description = "종목 검색 및 현재가 조회 (인증 불필요)")
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final Environment environment;
    private final MarketHoursService marketHoursService;
    private final VolatilityCalibrationService calibrationService;

    @Operation(summary = "종목 검색 (이름/코드)")
    @GetMapping
    public ApiResponse<List<StockResponse>> searchStocks(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(stockService.searchStocks(keyword));
    }

    @Operation(summary = "종목 단건 조회 (현재가 포함)")
    @GetMapping("/{ticker}")
    public ApiResponse<StockResponse> getStock(@PathVariable String ticker) {
        return ApiResponse.ok(stockService.getStock(ticker));
    }

    @Operation(summary = "기술적 분석 + 1년 히스토리 (RSI, MACD, 볼린저밴드)")
    @GetMapping("/{ticker}/technicals")
    public ApiResponse<StockTechnicalResponse> getTechnicals(@PathVariable String ticker) {
        return ApiResponse.ok(stockService.getTechnicals(ticker));
    }

    @Operation(summary = "시뮬레이션 모드 / 장 상태 조회")
    @GetMapping("/sim/status")
    public Map<String, Object> simStatus() {
        boolean sim = Arrays.asList(environment.getActiveProfiles()).contains("local");
        boolean marketOpen = marketHoursService.isMarketOpen();
        boolean calibrated = calibrationService.isCalibrated();
        String mode = !sim ? "LIVE" : marketOpen ? "REALTIME" : "SIM";
        return Map.of(
                "simulation", sim,
                "marketOpen", marketOpen,
                "calibrated", calibrated,
                "mode", mode,
                "interval", sim && !marketOpen ? 3 : 60
        );
    }
}
