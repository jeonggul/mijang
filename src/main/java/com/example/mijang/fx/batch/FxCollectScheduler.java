package com.example.mijang.fx.batch;

import com.example.mijang.fx.service.FxCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 환율 수집 배치. 개발명세서 '실시간·배치 상세' 시트 · {@code GLOBAL-01} */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FxCollectScheduler {

    private final FxCollectService collectService;

    /** 매시 02분에 환율을 수집한다. */
    @Scheduled(cron = "0 2 * * * *")
    public void run() {
        collectService.collect().ifPresentOrElse(
                q -> log.debug("[배치] 환율 수집 — {}", q.quotedAt()),
                () -> log.warn("[배치] 환율을 받지 못했다"));
    }
}
