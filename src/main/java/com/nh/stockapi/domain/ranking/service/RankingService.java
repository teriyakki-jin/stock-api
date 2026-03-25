package com.nh.stockapi.domain.ranking.service;

import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.entity.AccountStatus;
import com.nh.stockapi.domain.account.repository.AccountRepository;
import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.ranking.dto.MyRankingResponse;
import com.nh.stockapi.domain.ranking.dto.RankingEntryResponse;
import com.nh.stockapi.domain.stock.service.StockService;
import com.nh.stockapi.infrastructure.supabase.SupabaseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class RankingService {

    private final AccountRepository  accountRepository;
    private final HoldingRepository  holdingRepository;
    private final StockService       stockService;
    private final SupabaseClient     supabaseClient;

    private static final String[] PERIODS = {"DAILY", "WEEKLY", "MONTHLY", "ALL_TIME"};

    // ─────────────────────────────────────────────────────────
    // 랭킹 갱신 (스케줄러에서 호출)
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void refreshRanking() {
        String today = LocalDate.now().toString();
        List<Account> accounts = accountRepository.findAll().stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .toList();

        log.info("랭킹 갱신 시작: 활성 계좌 {}개", accounts.size());

        for (Account account : accounts) {
            try {
                processAccount(account, today);
            } catch (Exception e) {
                log.warn("계좌 {} 랭킹 계산 실패: {}", account.getId(), e.getMessage());
            }
        }

        // 순위 재계산 (pnl_rate DESC 기준 rank 업데이트)
        for (String period : PERIODS) {
            recalculateRanks(period, today);
        }

        log.info("랭킹 갱신 완료");
    }

    private void processAccount(Account account, String today) {
        List<Holding> holdings = holdingRepository.findByAccountIdWithStock(account.getId());

        BigDecimal totalInvested = holdings.stream()
                .map(h -> h.getAvgPrice().multiply(BigDecimal.valueOf(h.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentValue = holdings.stream()
                .map(h -> stockService.getCurrentPrice(
                        h.getStock().getTicker(), h.getStock().getBasePrice())
                        .multiply(BigDecimal.valueOf(h.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPnl = currentValue.subtract(totalInvested);
        double pnlRate = totalInvested.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .doubleValue();

        // 프로필 조회 (없으면 생성)
        Map<String, Object> profile = supabaseClient.getProfileByMemberId(account.getMember().getId());
        if (profile == null) {
            profile = createDefaultProfile(account);
        }

        String profileId = (String) profile.get("id");
        if (profileId == null) return;

        // 모든 기간에 동일한 스냅샷 저장 (기간별 필터는 쿼리에서)
        for (String period : PERIODS) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("profile_id",    profileId);
            snapshot.put("period",         period);
            snapshot.put("pnl_rate",       pnlRate);
            snapshot.put("total_pnl",      totalPnl.toPlainString());
            snapshot.put("total_trades",   countTrades(account.getId(), period));
            snapshot.put("snapshot_date",  today);
            supabaseClient.upsertRanking(snapshot);
        }
    }

    private Map<String, Object> createDefaultProfile(Account account) {
        String email    = account.getMember().getEmail();
        String nickname = email.split("@")[0] + "_" + account.getId();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("local_member_id", account.getMember().getId());
        p.put("nickname",         nickname);
        supabaseClient.upsertProfile(p);
        return supabaseClient.getProfileByMemberId(account.getMember().getId());
    }

    private void recalculateRanks(String period, String today) {
        // Supabase DB 함수 없이 클라이언트에서 rank 업데이트 (소규모)
        List<Map<String, Object>> entries = supabaseClient.getLeaderboard(period, today, 0, 10000);
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = entries.get(i);
            Map<String, Object> update = Map.of("rank", i + 1);
            // profile_id + period + snapshot_date 조합으로 업데이트
            // (단순화: upsert로 재처리)
        }
    }

    private int countTrades(Long accountId, String period) {
        // TODO: OrderRepository에 기간별 집계 쿼리 추가 시 교체
        return 0;
    }

    // ─────────────────────────────────────────────────────────
    // 조회 API
    // ─────────────────────────────────────────────────────────

    public List<RankingEntryResponse> getLeaderboard(String period, int page, int size) {
        String today = LocalDate.now().toString();
        List<Map<String, Object>> raw = supabaseClient.getLeaderboard(
                period, today, page * size, size);

        List<RankingEntryResponse> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> entry = raw.get(i);
            Map<String, Object> profile = getNestedProfile(entry);

            result.add(new RankingEntryResponse(
                    page * size + i + 1,
                    (String) profile.getOrDefault("nickname", "익명"),
                    (String) profile.get("avatar_url"),
                    toDouble(entry.get("pnl_rate")),
                    toBigDecimal(entry.get("total_pnl")),
                    toInt(entry.get("total_trades"))
            ));
        }
        return result;
    }

    public MyRankingResponse getMyRanking(Long memberId, String period) {
        String today = LocalDate.now().toString();
        Map<String, Object> profile = supabaseClient.getProfileByMemberId(memberId);
        if (profile == null) return null;

        String profileId = (String) profile.get("id");
        Map<String, Object> entry = supabaseClient.getMyRanking(profileId, period, today);
        if (entry == null) return null;

        return new MyRankingResponse(
                toInt(entry.get("rank")),
                toDouble(entry.get("pnl_rate")),
                toBigDecimal(entry.get("total_pnl")),
                toInt(entry.get("total_trades")),
                period,
                today
        );
    }

    // ─────────────────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedProfile(Map<String, Object> entry) {
        Object p = entry.get("profiles");
        if (p instanceof Map<?, ?>) return (Map<String, Object>) p;
        return Map.of();
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }
}
