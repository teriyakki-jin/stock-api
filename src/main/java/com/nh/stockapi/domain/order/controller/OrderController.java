package com.nh.stockapi.domain.order.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.dto.HoldingResponse;
import com.nh.stockapi.domain.order.dto.OrderRequest;
import com.nh.stockapi.domain.order.dto.OrderResponse;
import com.nh.stockapi.domain.order.service.OrderService;
import com.nh.stockapi.domain.order.service.SseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "주문", description = "매수 / 매도 / 지정가 / 체결 내역 / 보유 종목")
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SseEmitterService sseEmitterService;

    @Operation(summary = "매수 주문 (시장가/지정가)")
    @PostMapping("/api/v1/accounts/{accountId}/orders/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> buy(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok("매수 주문이 접수되었습니다.", orderService.buy(accountId, member, request));
    }

    @Operation(summary = "매도 주문 (시장가/지정가)")
    @PostMapping("/api/v1/accounts/{accountId}/orders/sell")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> sell(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok("매도 주문이 접수되었습니다.", orderService.sell(accountId, member, request));
    }

    @Operation(summary = "미체결 주문 취소")
    @DeleteMapping("/api/v1/accounts/{accountId}/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(
            @PathVariable Long accountId,
            @PathVariable Long orderId,
            @AuthenticationPrincipal Member member) {
        orderService.cancelOrder(accountId, member, orderId);
    }

    @Operation(summary = "주문 체결 내역 조회 (페이징)")
    @GetMapping("/api/v1/accounts/{accountId}/orders")
    public ApiResponse<Page<OrderResponse>> getOrderHistory(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(orderService.getOrderHistory(accountId, member, pageable));
    }

    @Operation(summary = "미체결 주문 조회")
    @GetMapping("/api/v1/accounts/{accountId}/orders/pending")
    public ApiResponse<List<OrderResponse>> getPendingOrders(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(orderService.getPendingOrders(accountId, member));
    }

    @Operation(summary = "보유 종목 조회")
    @GetMapping("/api/v1/accounts/{accountId}/orders/holdings")
    public ApiResponse<List<HoldingResponse>> getHoldings(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(orderService.getHoldings(accountId, member));
    }

    @Operation(summary = "주문 체결 SSE 구독")
    @GetMapping(value = "/api/v1/orders/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeOrderEvents(@AuthenticationPrincipal Member member) {
        return sseEmitterService.subscribe(member.getEmail());
    }
}
