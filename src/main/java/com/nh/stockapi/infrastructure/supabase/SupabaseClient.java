package com.nh.stockapi.infrastructure.supabase;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Supabase PostgREST API 클라이언트
 * service_role 키를 사용하여 RLS를 우회하고 서버에서만 호출합니다.
 */
@Slf4j
@Component
public class SupabaseClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String serviceRoleKey;

    public SupabaseClient(
            RestTemplate restTemplate,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey) {
        this.restTemplate = restTemplate;
        this.baseUrl      = supabaseUrl + "/rest/v1";
        this.serviceRoleKey = serviceRoleKey;
    }

    // ─────────────────────────────────────────────────────────
    // profiles
    // ─────────────────────────────────────────────────────────

    public void upsertProfile(Map<String, Object> profileData) {
        String url = baseUrl + "/profiles";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(profileData, serviceHeaders());
        try {
            restTemplate.exchange(url + "?on_conflict=local_member_id",
                    HttpMethod.POST, entity, Void.class);
        } catch (Exception e) {
            log.error("Supabase profiles upsert 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SUPABASE_API_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProfileByMemberId(Long memberId) {
        String url = baseUrl + "/profiles?local_member_id=eq." + memberId + "&select=*";
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(readHeaders()), List.class);
            List<Map<String, Object>> list = resp.getBody();
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        } catch (Exception e) {
            log.warn("Supabase profile 조회 실패 (memberId={}): {}", memberId, e.getMessage());
            return null;
        }
    }

    public void updateProfile(Long memberId, Map<String, Object> data) {
        String url = baseUrl + "/profiles?local_member_id=eq." + memberId;
        try {
            restTemplate.exchange(url, HttpMethod.PATCH,
                    new HttpEntity<>(data, serviceHeaders()), Void.class);
        } catch (Exception e) {
            log.error("Supabase profile 업데이트 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SUPABASE_API_ERROR);
        }
    }

    // ─────────────────────────────────────────────────────────
    // ranking_snapshots
    // ─────────────────────────────────────────────────────────

    public void upsertRanking(Map<String, Object> rankingData) {
        String url = baseUrl + "/ranking_snapshots?on_conflict=profile_id,period,snapshot_date";
        try {
            restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(rankingData, upsertHeaders()), Void.class);
        } catch (Exception e) {
            log.error("Supabase ranking upsert 실패: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getLeaderboard(String period, String date,
                                                     int offset, int limit) {
        String url = baseUrl + "/ranking_snapshots" +
                "?period=eq." + period +
                "&snapshot_date=eq." + date +
                "&order=pnl_rate.desc" +
                "&offset=" + offset +
                "&limit=" + limit +
                "&select=*,profiles(nickname,avatar_url)";
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(readHeaders()), List.class);
            return resp.getBody() != null ? resp.getBody() : List.of();
        } catch (Exception e) {
            log.warn("Supabase leaderboard 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyRanking(String profileId, String period, String date) {
        String url = baseUrl + "/ranking_snapshots" +
                "?profile_id=eq." + profileId +
                "&period=eq." + period +
                "&snapshot_date=eq." + date +
                "&select=*";
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(readHeaders()), List.class);
            List<Map<String, Object>> list = resp.getBody();
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        } catch (Exception e) {
            log.warn("Supabase my ranking 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────
    // 헤더 유틸
    // ─────────────────────────────────────────────────────────

    private HttpHeaders serviceHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("apikey", serviceRoleKey);
        h.set("Authorization", "Bearer " + serviceRoleKey);
        h.set("Prefer", "return=minimal");
        return h;
    }

    private HttpHeaders upsertHeaders() {
        HttpHeaders h = serviceHeaders();
        h.set("Prefer", "resolution=merge-duplicates,return=minimal");
        return h;
    }

    private HttpHeaders readHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("apikey", serviceRoleKey);
        h.set("Authorization", "Bearer " + serviceRoleKey);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }
}
