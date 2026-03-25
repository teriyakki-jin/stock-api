package com.nh.stockapi.domain.portfolio.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.portfolio.dto.PortfolioAnalysisResponse;
import com.nh.stockapi.domain.portfolio.dto.PortfolioSummaryResponse;
import com.nh.stockapi.domain.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "포트폴리오", description = "포트폴리오 요약 / 배분 / 수익률")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(summary = "포트폴리오 요약", description = "총 투자금, 평가금액, 손익, 보유 종목별 비중 반환")
    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(portfolioService.getSummary(accountId, member));
    }

    @Operation(summary = "포트폴리오 심화 분석",
               description = "수익률 곡선, 섹터 비중, 승률, MDD 반환. days 파라미터로 분석 기간 조정 (기본 30일)")
    @GetMapping("/analysis")
    public ApiResponse<PortfolioAnalysisResponse> getAnalysis(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(portfolioService.getAnalysis(accountId, member, days));
    }
}
