package com.example.mijang.market.batch;

import com.example.mijang.market.service.MarketCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 하루 한 번 거래일 달력을 받아 채운다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MarketCalendarSyncScheduler {

    private final MarketCalendarSyncService syncService;

    /** 매일 20:40 KST(미국 장 열리기 전)에 달력을 동기화한다. */
    @Scheduled(cron = "0 40 20 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            syncService.syncAll();
        } catch (RuntimeException e) {
            log.error("[거래일] 동기화 실패 — 기존 달력은 그대로 남는다", e);
        }
    }
}
