package com.nh.stockapi.domain.ranking.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.ranking.dto.MyRankingResponse;
import com.nh.stockapi.domain.ranking.dto.RankingEntryResponse;
import com.nh.stockapi.domain.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "랭킹", description = "기간별 리더보드 조회")
@RestController
@RequestMapping("/api/v1/ranking")
@RequiredArgsConstructor
@Profile("!test")
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "리더보드 조회", description = "period: DAILY | WEEKLY | MONTHLY | ALL_TIME")
    @GetMapping
    public ApiResponse<List<RankingEntryResponse>> getLeaderboard(
            @RequestParam(defaultValue = "ALL_TIME") String period,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(rankingService.getLeaderboard(period, page, size));
    }

    @Operation(summary = "내 순위 조회")
    @GetMapping("/me")
    public ApiResponse<MyRankingResponse> getMyRanking(
            @AuthenticationPrincipal Member member,
            @RequestParam(defaultValue = "ALL_TIME") String period) {
        return ApiResponse.ok(rankingService.getMyRanking(member.getId(), period));
    }
}
