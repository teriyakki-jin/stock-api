package com.nh.stockapi.domain.alert.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.alert.dto.PriceAlertRequest;
import com.nh.stockapi.domain.alert.dto.PriceAlertResponse;
import com.nh.stockapi.domain.alert.service.PriceAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "가격 알림", description = "목표주가 도달 시 알림")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/alerts")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService alertService;

    @Operation(summary = "알림 등록")
    @PostMapping
    public ApiResponse<PriceAlertResponse> create(
            @PathVariable Long accountId,
            @RequestBody @Valid PriceAlertRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.ok(alertService.create(accountId, req));
    }

    @Operation(summary = "알림 목록")
    @GetMapping
    public ApiResponse<List<PriceAlertResponse>> list(@PathVariable Long accountId) {
        return ApiResponse.ok(alertService.list(accountId));
    }

    @Operation(summary = "미확인 발동 알림 조회")
    @GetMapping("/pending")
    public ApiResponse<List<PriceAlertResponse>> pending(@PathVariable Long accountId) {
        return ApiResponse.ok(alertService.listUnacknowledged(accountId));
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{alertId}")
    public ApiResponse<Void> delete(
            @PathVariable Long accountId,
            @PathVariable Long alertId) {
        alertService.delete(accountId, alertId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "알림 확인 처리")
    @PatchMapping("/{alertId}/acknowledge")
    public ApiResponse<Void> acknowledge(
            @PathVariable Long accountId,
            @PathVariable Long alertId) {
        alertService.acknowledge(accountId, alertId);
        return ApiResponse.ok(null);
    }
}
