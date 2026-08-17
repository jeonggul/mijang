/*
 * StockTypeSyncScheduler — 종목 종류 동기화 배치
 *
 * 이 파일이 하는 일
 *   하루 한 번 Finnhub 에서 종목 종류를 받아 채운다.
 *
 *   호출 한 번에 3만 건이 오므로 부담이 없다. 종목 마스터 동기화(21:00) 뒤에 돌아
 *   그날 새로 상장된 종목에도 종류가 붙게 한다.
 */
package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.StockTypeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockTypeSyncScheduler {

    private final StockTypeSyncService syncService;

    /**
     * 평일 21:30. 마스터 동기화(21:00)·한글명(21:20) 다음이다.
     *
     * <p>실패해도 다음 날 다시 돈다. 이미 정해진 종류는 그대로 남으므로 하루 걸러도
     * 검색 필터가 망가지지 않는다.
     */
    @Scheduled(cron = "0 30 21 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        try {
            syncService.syncAll();
        } catch (RuntimeException e) {
            log.error("[종목 종류] 동기화 실패 — 기존 값은 그대로 남는다", e);
        }
    }
}
