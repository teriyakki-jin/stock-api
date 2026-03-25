package com.nh.stockapi.security;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Supabase JWT 검증기
 * Supabase Auth가 발급한 access_token의 서명/만료를 검증하고
 * 이메일, sub(UUID), provider 등 클레임을 추출합니다.
 */
@Slf4j
@Component
public class SupabaseJwtVerifier {

    private final SecretKey key;

    public SupabaseJwtVerifier(
            @Value("${supabase.jwt-secret}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Supabase JWT 파싱 결과
     */
    public record SupabaseClaims(
            String sub,      // Supabase user UUID
            String email,
            String provider  // google, github, email, ...
    ) {}

    public SupabaseClaims verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String sub      = claims.getSubject();
            String email    = claims.get("email", String.class);
            String provider = extractProvider(claims);

            if (sub == null || email == null) {
                throw new CustomException(ErrorCode.OAUTH_VERIFICATION_FAILED);
            }

            return new SupabaseClaims(sub, email, provider);

        } catch (CustomException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Supabase JWT 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.OAUTH_VERIFICATION_FAILED);
        }
    }

    private String extractProvider(Claims claims) {
        try {
            // Supabase JWT: app_metadata.provider
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> appMeta =
                    (java.util.Map<String, Object>) claims.get("app_metadata");
            if (appMeta != null && appMeta.get("provider") instanceof String p) {
                return p;
            }
        } catch (Exception ignored) {}
        return "email";
    }
}
