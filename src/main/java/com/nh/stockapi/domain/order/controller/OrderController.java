package com.nh.stockapi.domain.order.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.dto.HoldingResponse;
import com.nh.stockapi.domain.order.dto.OrderRequest;
import com.nh.stockapi.domain.order.dto.OrderResponse;
import com.nh.stockapi.domain.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "주문", description = "매수 / 매도 / 체결 내역 / 보유 종목")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "매수 주문")
    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> buy(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok("매수 주문이 체결되었습니다.", orderService.buy(accountId, member, request));
    }

    @Operation(summary = "매도 주문")
    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> sell(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok("매도 주문이 체결되었습니다.", orderService.sell(accountId, member, request));
    }

    @Operation(summary = "주문 체결 내역 조회 (페이징)")
    @GetMapping
    public ApiResponse<Page<OrderResponse>> getOrderHistory(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(orderService.getOrderHistory(accountId, member, pageable));
    }

    @Operation(summary = "보유 종목 조회")
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> getHoldings(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(orderService.getHoldings(accountId, member));
    }
}
