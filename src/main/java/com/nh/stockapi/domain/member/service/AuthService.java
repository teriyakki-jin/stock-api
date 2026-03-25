package com.nh.stockapi.domain.member.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.member.dto.LoginRequest;
import com.nh.stockapi.domain.member.dto.OAuthLoginRequest;
import com.nh.stockapi.domain.member.dto.SignUpRequest;
import com.nh.stockapi.domain.member.dto.TokenResponse;
import com.nh.stockapi.domain.member.entity.AuthProvider;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.member.repository.MemberRepository;
import com.nh.stockapi.security.JwtTokenProvider;
import com.nh.stockapi.security.SupabaseJwtVerifier;
import com.nh.stockapi.security.SupabaseJwtVerifier.SupabaseClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SupabaseJwtVerifier supabaseJwtVerifier;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phone()
        );
        memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken  = jwtTokenProvider.createAccessToken(member.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getEmail());

        return TokenResponse.of(accessToken, refreshToken);
    }

    public void logout(String email, String accessToken) {
        jwtTokenProvider.invalidateTokens(email, accessToken);
    }

    /** Refresh Token으로 새 Access Token 발급 */
    public String refresh(String refreshToken) {
        return jwtTokenProvider.refreshAccessToken(refreshToken);
    }

    /**
     * OAuth 로그인 (Google / GitHub)
     * Supabase JWT 검증 → Member 조회/생성 → 자체 JWT 발급
     */
    @Transactional
    public TokenResponse oauthLogin(OAuthLoginRequest request) {
        SupabaseClaims claims = supabaseJwtVerifier.verify(request.supabaseAccessToken());

        AuthProvider provider = parseProvider(claims.provider());
        String email = claims.email();

        Member member = memberRepository.findByEmail(email)
                .map(existing -> {
                    // 기존 계정에 Supabase UID 연결 (처음 OAuth 로그인인 경우)
                    if (existing.getSupabaseUid() == null) {
                        existing.linkSupabase(claims.sub(), provider);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    // 신규 OAuth 회원 자동 가입
                    String name = email.split("@")[0]; // 이메일 앞부분을 기본 이름으로
                    return memberRepository.save(
                            Member.createOAuth(email, name, claims.sub(), provider));
                });

        String accessToken  = jwtTokenProvider.createAccessToken(member.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getEmail());
        return TokenResponse.of(accessToken, refreshToken);
    }

    private AuthProvider parseProvider(String raw) {
        return switch (raw.toLowerCase()) {
            case "google" -> AuthProvider.GOOGLE;
            case "github" -> AuthProvider.GITHUB;
            default       -> AuthProvider.LOCAL;
        };
    }
}
