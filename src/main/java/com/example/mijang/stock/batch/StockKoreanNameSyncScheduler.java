/*
 * StockKoreanNameSyncScheduler — 한글 종목명 동기화 배치
 *
 * 이 파일이 하는 일
 *   하루 한 번 Wikidata 에서 한글 종목명을 받아 채운다.
 *
 *   자주 돌 이유가 없다. 회사 이름이 바뀌는 일은 드물고, 새로 상장된 종목이
 *   Wikidata 에 실리기까지도 시간이 걸린다.
 *
 *   종목 마스터 동기화(21:00) 뒤에 돈다. 새 종목이 stocks 에 들어와 있어야 이름을 붙일 수 있다.
 */
package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.StockKoreanNameSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockKoreanNameSyncScheduler {

    private final StockKoreanNameSyncService syncService;

    /**
     * 평일 21:20. 종목 마스터 동기화 20분 뒤다.
     *
     * <p>실패해도 다음 날 다시 돈다. 이미 채워진 이름은 그대로 남으므로
     * 하루 걸러도 검색이 망가지지 않는다.
     */
    @Scheduled(cron = "0 20 21 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        try {
            syncService.syncAll();
        } catch (RuntimeException e) {
            log.error("[한글명] 동기화 실패 — 기존 이름은 그대로 남는다", e);
        }
    }
}
