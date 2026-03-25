package com.nh.stockapi.domain.profile.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(min = 2, max = 30, message = "닉네임은 2~30자여야 합니다.")
        String nickname,

        @Size(max = 200, message = "소개는 200자 이하여야 합니다.")
        String bio
) {}
