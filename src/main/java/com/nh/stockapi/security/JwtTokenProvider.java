package com.nh.stockapi.security;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpireMs;
    private final long refreshTokenExpireMs;
    private final CustomUserDetailsService userDetailsService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX  = "RT:";
    private static final String BLACKLIST_TOKEN_PREFIX = "BL:";

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expire-ms}") long accessTokenExpireMs,
            @Value("${jwt.refresh-token-expire-ms}") long refreshTokenExpireMs,
            CustomUserDetailsService userDetailsService,
            RedisTemplate<String, String> redisTemplate) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpireMs = accessTokenExpireMs;
        this.refreshTokenExpireMs = refreshTokenExpireMs;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
    }

    public String createAccessToken(String email) {
        return buildToken(email, accessTokenExpireMs);
    }

    public String createRefreshToken(String email) {
        String token = buildToken(email, refreshTokenExpireMs);
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + email,
                token,
                refreshTokenExpireMs,
                TimeUnit.MILLISECONDS
        );
        return token;
    }

    /** Refresh Token으로 새 Access Token 발급 */
    public String refreshAccessToken(String refreshToken) {
        validateToken(refreshToken);
        String email = extractEmail(refreshToken);

        String stored = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + email);
        if (!refreshToken.equals(stored)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return createAccessToken(email);
    }

    public Authentication getAuthentication(String token) {
        String email = extractEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            // 블랙리스트 확인
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_TOKEN_PREFIX + token))) {
                throw new CustomException(ErrorCode.INVALID_TOKEN);
            }
            return true;
        } catch (CustomException e) {
            throw e;
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** 로그아웃: Refresh Token 삭제 + Access Token 블랙리스트 등록 */
    public void invalidateTokens(String email, String accessToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + email);

        // Access Token 남은 유효시간만큼 블랙리스트에 등록
        try {
            Date expiration = parseClaims(accessToken).getExpiration();
            long remainMs = expiration.getTime() - System.currentTimeMillis();
            if (remainMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_TOKEN_PREFIX + accessToken,
                        "logout",
                        remainMs,
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (JwtException e) {
            log.warn("로그아웃 토큰 파싱 실패 (이미 만료된 토큰): {}", e.getMessage());
        }
    }

    private String buildToken(String subject, long expireMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
