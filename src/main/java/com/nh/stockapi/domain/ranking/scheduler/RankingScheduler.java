package com.nh.stockapi.domain.ranking.scheduler;

import com.nh.stockapi.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 5분마다 전체 유저 수익률을 집계하여 Supabase ranking_snapshots에 UPSERT합니다.
 * Supabase Realtime이 변경을 감지하여 프론트엔드에 자동 Push합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final RankingService rankingService;

    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void refreshRanking() {
        log.info("랭킹 갱신 스케줄러 실행");
        try {
            rankingService.refreshRanking();
        } catch (Exception e) {
            log.error("랭킹 갱신 실패: {}", e.getMessage());
        }
    }
}
