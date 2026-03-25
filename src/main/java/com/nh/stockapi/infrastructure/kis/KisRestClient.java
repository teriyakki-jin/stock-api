package com.nh.stockapi.infrastructure.kis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * 한국투자증권 OpenAPI REST 클라이언트
 *
 * 주요 기능:
 * - 국내주식 현재가 시세 조회 (주식현재가 시세)
 * - app_key 미설정 시 Optional.empty() 반환 → 시뮬레이터 fallback
 *
 * KIS API 문서: https://apiportal.koreainvestment.com/
 * 종목코드: 6자리 숫자 (예: 005930)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisRestClient {

    private final KisProperties    props;
    private final KisTokenManager  tokenManager;

    /**
     * 국내주식 현재가 시세 조회
     *
     * @param ticker 종목코드 (6자리, 예: 005930)
     * @return 현재가, 없으면 Optional.empty()
     */
    public Optional<KisPriceData> getCurrentPrice(String ticker) {
        if (!tokenManager.isConfigured()) return Optional.empty();

        try {
            String token  = tokenManager.getAccessToken();
            String trId   = props.isMock() ? "FHKST01010100" : "FHKST01010100";
            // 모의/실투자 동일 TR ID (시세 조회는 동일)

            RestClient client = RestClient.create();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.get()
                .uri(props.getBaseUrl() + "/uapi/domestic-stock/v1/quotations/inquire-price"
                    + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + ticker)
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + token)
                .header("appkey",    props.getAppKey())
                .header("appsecret", props.getAppSecret())
                .header("tr_id",     trId)
                .header("custtype",  "P")
                .retrieve()
                .body(Map.class);

            if (resp == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) resp.get("output");
            if (output == null) return Optional.empty();

            return Optional.of(new KisPriceData(
                ticker,
                parseBD(output, "stck_prpr"),   // 주식 현재가
                parseBD(output, "stck_hgpr"),   // 당일 고가
                parseBD(output, "stck_lwpr"),   // 당일 저가
                parseLong(output, "acml_vol"),  // 누적 거래량
                parseDouble(output, "prdy_ctrt") // 전일 대비율
            ));
        } catch (Exception e) {
            log.debug("KIS 현재가 조회 실패 ({}): {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    // ── 파싱 헬퍼 ────────────────────────────────────────────────────────────

    private BigDecimal parseBD(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return BigDecimal.ZERO;
        try { return new BigDecimal(v.toString().replace(",", "")); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private long parseLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0L;
        try { return Long.parseLong(v.toString().replace(",", "")); }
        catch (Exception e) { return 0L; }
    }

    private double parseDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString().replace(",", "")); }
        catch (Exception e) { return 0.0; }
    }

    public record KisPriceData(
        String ticker,
        BigDecimal currentPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        long volume,
        double changeRate
    ) {}
}
