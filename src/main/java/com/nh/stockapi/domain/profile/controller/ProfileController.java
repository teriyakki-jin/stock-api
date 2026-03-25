package com.nh.stockapi.domain.profile.controller;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.profile.dto.ProfileResponse;
import com.nh.stockapi.domain.profile.dto.ProfileUpdateRequest;
import com.nh.stockapi.infrastructure.supabase.SupabaseClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "프로필", description = "사용자 프로필 조회 및 수정")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Profile("!test")
public class ProfileController {

    private final SupabaseClient supabaseClient;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getMyProfile(@AuthenticationPrincipal Member member) {
        Map<String, Object> profile = supabaseClient.getProfileByMemberId(member.getId());
        if (profile == null) {
            // 프로필 없으면 기본값 생성
            profile = createDefaultProfile(member);
        }
        return ApiResponse.ok(toResponse(profile));
    }

    @Operation(summary = "프로필 수정 (닉네임 / bio)")
    @PatchMapping("/me")
    public ApiResponse<Void> updateProfile(
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody ProfileUpdateRequest request) {

        Map<String, Object> data = new LinkedHashMap<>();
        if (request.nickname() != null) data.put("nickname", request.nickname());
        if (request.bio()      != null) data.put("bio",      request.bio());

        if (!data.isEmpty()) {
            supabaseClient.updateProfile(member.getId(), data);
        }
        return ApiResponse.ok("프로필이 수정되었습니다.", null);
    }

    @Operation(summary = "공개 프로필 조회 (닉네임 기반)")
    @GetMapping("/public/{memberId}")
    public ApiResponse<ProfileResponse> getPublicProfile(@PathVariable Long memberId) {
        Map<String, Object> profile = supabaseClient.getProfileByMemberId(memberId);
        if (profile == null) throw new CustomException(ErrorCode.PROFILE_NOT_FOUND);
        return ApiResponse.ok(toResponse(profile));
    }

    private Map<String, Object> createDefaultProfile(Member member) {
        String email    = member.getEmail();
        String nickname = email.split("@")[0];
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("local_member_id", member.getId());
        p.put("nickname",         nickname);
        supabaseClient.upsertProfile(p);
        return supabaseClient.getProfileByMemberId(member.getId());
    }

    private ProfileResponse toResponse(Map<String, Object> p) {
        if (p == null) return null;
        Long memberId = null;
        Object mid = p.get("local_member_id");
        if (mid instanceof Number n) memberId = n.longValue();
        return new ProfileResponse(
                (String) p.get("id"),
                memberId,
                (String) p.get("nickname"),
                (String) p.get("avatar_url"),
                (String) p.get("bio")
        );
    }
}
