package com.nh.stockapi.infrastructure.supabase;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

/**
 * Supabase PostgreSQL JDBC 클라이언트
 * PostgREST REST API 대신 직접 JDBC로 profiles / ranking_snapshots 조작
 */
@Slf4j
@Component
public class SupabaseClient {

    private final JdbcTemplate jdbc;

    public SupabaseClient(@Qualifier("supabaseJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─────────────────────────────────────────────────────────
    // profiles
    // ─────────────────────────────────────────────────────────

    public void upsertProfile(Map<String, Object> data) {
        try {
            String memberId = String.valueOf(data.get("local_member_id"));
            String nickname  = (String) data.get("nickname");
            String avatarUrl = (String) data.getOrDefault("avatar_url", null);
            String bio       = (String) data.getOrDefault("bio", null);
            String uid       = (String) data.getOrDefault("supabase_uid", null);

            jdbc.update("""
                INSERT INTO profiles (local_member_id, nickname, avatar_url, bio, supabase_uid)
                VALUES (?, ?, ?, ?, ?::uuid)
                ON CONFLICT (local_member_id)
                DO UPDATE SET
                  nickname   = EXCLUDED.nickname,
                  avatar_url = COALESCE(EXCLUDED.avatar_url, profiles.avatar_url),
                  bio        = COALESCE(EXCLUDED.bio, profiles.bio),
                  supabase_uid = COALESCE(EXCLUDED.supabase_uid, profiles.supabase_uid),
                  updated_at = now()
                """,
                Long.parseLong(memberId), nickname, avatarUrl, bio, uid
            );
        } catch (Exception e) {
            log.error("profiles upsert 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SUPABASE_API_ERROR);
        }
    }

    public Map<String, Object> getProfileByMemberId(Long memberId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id::text, local_member_id, nickname, avatar_url, bio FROM profiles WHERE local_member_id = ?",
                memberId
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("profile 조회 실패 (memberId={}): {}", memberId, e.getMessage());
            return null;
        }
    }

    public void updateProfile(Long memberId, Map<String, Object> data) {
        try {
            List<String> setClauses = new ArrayList<>();
            List<Object> params     = new ArrayList<>();

            if (data.containsKey("nickname")) {
                setClauses.add("nickname = ?");
                params.add(data.get("nickname"));
            }
            if (data.containsKey("bio")) {
                setClauses.add("bio = ?");
                params.add(data.get("bio"));
            }
            if (data.containsKey("avatar_url")) {
                setClauses.add("avatar_url = ?");
                params.add(data.get("avatar_url"));
            }
            if (setClauses.isEmpty()) return;

            setClauses.add("updated_at = now()");
            params.add(memberId);

            String sql = "UPDATE profiles SET " + String.join(", ", setClauses) +
                         " WHERE local_member_id = ?";
            jdbc.update(sql, params.toArray());
        } catch (Exception e) {
            log.error("profile 업데이트 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SUPABASE_API_ERROR);
        }
    }

    // ─────────────────────────────────────────────────────────
    // ranking_snapshots
    // ─────────────────────────────────────────────────────────

    public void upsertRanking(Map<String, Object> data) {
        try {
            jdbc.update("""
                INSERT INTO ranking_snapshots (profile_id, period, pnl_rate, total_pnl, total_trades, snapshot_date)
                VALUES (?::uuid, ?, ?, ?, ?, ?)
                ON CONFLICT (profile_id, period, snapshot_date)
                DO UPDATE SET
                  pnl_rate     = EXCLUDED.pnl_rate,
                  total_pnl    = EXCLUDED.total_pnl,
                  total_trades = EXCLUDED.total_trades
                """,
                (String) data.get("profile_id"),
                (String) data.get("period"),
                ((Number) data.get("pnl_rate")).doubleValue(),
                new BigDecimal(data.get("total_pnl").toString()),
                ((Number) data.get("total_trades")).intValue(),
                Date.valueOf(data.get("snapshot_date").toString())
            );
        } catch (Exception e) {
            log.warn("ranking upsert 실패: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getLeaderboard(String period, String date, int offset, int limit) {
        try {
            return jdbc.queryForList("""
                SELECT r.id, r.pnl_rate, r.total_pnl, r.total_trades, r.rank,
                       p.nickname, p.avatar_url
                FROM ranking_snapshots r
                JOIN profiles p ON p.id = r.profile_id
                WHERE r.period = ? AND r.snapshot_date = ?
                ORDER BY r.pnl_rate DESC
                OFFSET ? LIMIT ?
                """,
                period, Date.valueOf(date), offset, limit
            );
        } catch (Exception e) {
            log.warn("leaderboard 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getMyRanking(String profileId, String period, String date) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.*, p.nickname, p.avatar_url
                FROM ranking_snapshots r
                JOIN profiles p ON p.id = r.profile_id
                WHERE r.profile_id = ?::uuid AND r.period = ? AND r.snapshot_date = ?
                """,
                profileId, period, Date.valueOf(date)
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("my ranking 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
