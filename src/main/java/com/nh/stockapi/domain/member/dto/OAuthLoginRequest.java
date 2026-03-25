package com.nh.stockapi.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank(message = "Supabase access token은 필수입니다.")
        String supabaseAccessToken
) {}
