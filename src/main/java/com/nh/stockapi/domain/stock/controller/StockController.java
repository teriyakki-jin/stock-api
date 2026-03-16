package com.nh.stockapi.domain.stock.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.stock.dto.StockResponse;
import com.nh.stockapi.domain.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "종목", description = "종목 검색 및 현재가 조회 (인증 불필요)")
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

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
}
