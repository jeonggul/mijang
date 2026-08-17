/*
 * MarketCalendarSyncScheduler — 거래일 달력 동기화 배치
 *
 * 이 파일이 하는 일
 *   하루 한 번 거래일 달력을 받아 채운다.
 *
 *   거래소 일정은 자주 바뀌지 않는다. 다만 미리 넉넉히 받아 두므로 하루 걸러도
 *   세션 판정이 멈추지 않는다.
 */
package com.example.mijang.market.batch;

import com.example.mijang.market.service.MarketCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MarketCalendarSyncScheduler {

    private final MarketCalendarSyncService syncService;

    /** 매일 20:40 KST. 미국 장이 열리기 전이다 */
    @Scheduled(cron = "0 40 20 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            syncService.syncAll();
        } catch (RuntimeException e) {
            log.error("[거래일] 동기화 실패 — 기존 달력은 그대로 남는다", e);
        }
    }
}
