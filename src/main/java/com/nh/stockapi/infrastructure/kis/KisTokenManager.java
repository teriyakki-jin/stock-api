package com.nh.stockapi.infrastructure.kis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * KIS OAuth2 Access Token 관리
 * - 토큰 만료(24h) 전 자동 갱신
 * - app_key / app_secret 없으면 빈 문자열 반환 (기능 비활성화)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisTokenManager {

    private final KisProperties props;

    private String cachedToken   = null;
    private LocalDateTime expiry = LocalDateTime.MIN;

    public synchronized String getAccessToken() {
        if (!isConfigured()) return "";
        if (cachedToken != null && LocalDateTime.now().isBefore(expiry.minusMinutes(10))) {
            return cachedToken;
        }
        return fetchToken();
    }

    public boolean isConfigured() {
        return props.getAppKey() != null && !props.getAppKey().isBlank()
            && props.getAppSecret() != null && !props.getAppSecret().isBlank();
    }

    private String fetchToken() {
        try {
            RestClient client = RestClient.create();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.post()
                .uri(props.getBaseUrl() + "/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "grant_type", "client_credentials",
                    "appkey",     props.getAppKey(),
                    "appsecret",  props.getAppSecret()
                ))
                .retrieve()
                .body(Map.class);

            if (body == null || !body.containsKey("access_token")) {
                log.warn("KIS 토큰 발급 실패: 응답 없음");
                return "";
            }
            cachedToken = (String) body.get("access_token");
            // 만료 시간 계산 (KIS는 access_token_token_expired 필드 반환)
            String expiredAt = (String) body.getOrDefault("access_token_token_expired", "");
            expiry = expiredAt.isBlank()
                ? LocalDateTime.now().plusHours(23)
                : LocalDateTime.now().plusHours(23); // 간단화: 23h로 고정
            log.info("KIS 액세스 토큰 발급 성공 ({})", props.isMock() ? "모의투자" : "실투자");
            return cachedToken;
        } catch (Exception e) {
            log.warn("KIS 토큰 발급 실패: {}", e.getMessage());
            return "";
        }
    }
}
