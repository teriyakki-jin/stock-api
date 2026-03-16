package com.nh.stockapi.domain.member.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.member.dto.LoginRequest;
import com.nh.stockapi.domain.member.dto.RefreshTokenRequest;
import com.nh.stockapi.domain.member.dto.SignUpRequest;
import com.nh.stockapi.domain.member.dto.TokenResponse;
import com.nh.stockapi.domain.member.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입 / 로그인 / 로그아웃 / 토큰 갱신")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.ok("회원가입이 완료되었습니다.", null);
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "로그아웃 (Refresh 삭제 + Access Token 블랙리스트)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        String accessToken = resolveToken(request);
        authService.logout(userDetails.getUsername(), accessToken);
        return ApiResponse.ok("로그아웃 되었습니다.", null);
    }

    @Operation(summary = "Access Token 갱신")
    @PostMapping("/refresh")
    public ApiResponse<String> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String newAccessToken = authService.refresh(request.refreshToken());
        return ApiResponse.ok("토큰이 갱신되었습니다.", newAccessToken);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return "";
    }
}
