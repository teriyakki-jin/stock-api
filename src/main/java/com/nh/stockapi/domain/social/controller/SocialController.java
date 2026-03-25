package com.nh.stockapi.domain.social.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.infrastructure.supabase.SupabaseClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "소셜", description = "팔로우 / 팔로워 / 팔로잉")
@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SupabaseClient supabaseClient;

    @Operation(summary = "팔로우")
    @PostMapping("/follow/{targetMemberId}")
    public ApiResponse<Void> follow(
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal Member member) {
        supabaseClient.follow(member.getId(), targetMemberId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "언팔로우")
    @DeleteMapping("/follow/{targetMemberId}")
    public ApiResponse<Void> unfollow(
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal Member member) {
        supabaseClient.unfollow(member.getId(), targetMemberId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "팔로우 여부 확인")
    @GetMapping("/follow/{targetMemberId}")
    public ApiResponse<Map<String, Boolean>> isFollowing(
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal Member member) {
        boolean following = supabaseClient.isFollowing(member.getId(), targetMemberId);
        return ApiResponse.ok(Map.of("following", following));
    }

    @Operation(summary = "나를 팔로우하는 사람 목록")
    @GetMapping("/followers")
    public ApiResponse<List<Map<String, Object>>> getFollowers(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(supabaseClient.getFollowers(member.getId(), offset, limit));
    }

    @Operation(summary = "내가 팔로우하는 사람 목록")
    @GetMapping("/followings")
    public ApiResponse<List<Map<String, Object>>> getFollowings(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(supabaseClient.getFollowings(member.getId(), offset, limit));
    }

    @Operation(summary = "특정 유저 팔로워/팔로잉 수")
    @GetMapping("/counts/{memberId}")
    public ApiResponse<Map<String, Object>> getFollowCounts(@PathVariable Long memberId) {
        return ApiResponse.ok(supabaseClient.getFollowCounts(memberId));
    }

    @Operation(summary = "특정 유저 팔로워 목록 (공개)")
    @GetMapping("/followers/{memberId}")
    public ApiResponse<List<Map<String, Object>>> getFollowersOf(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(supabaseClient.getFollowers(memberId, offset, limit));
    }
}
