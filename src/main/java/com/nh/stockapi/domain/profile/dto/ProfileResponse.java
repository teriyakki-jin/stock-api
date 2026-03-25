package com.nh.stockapi.domain.profile.dto;

public record ProfileResponse(
        String id,
        Long   localMemberId,
        String nickname,
        String avatarUrl,
        String bio
) {}
