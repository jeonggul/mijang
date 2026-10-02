package com.example.mijang.fx.batch;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.service.FxConfirmService;
import java.time.LocalDate;
import com.example.mijang.admin.service.BatchLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 하루의 마지막 시세를 확정 환율로 옮기는 배치다. {@code GLOBAL-01} */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FxConfirmScheduler {

    private final FxConfirmService confirmService;
    private final BatchLogWriter batchLogWriter;

    /** 23:50 KST 에 그날 환율을 확정한다. */
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(TradingClock.SERVICE_ZONE);
        // 확정 성공이면 건수 1, 실패면 0 을 배치 로그에 남긴다.
        batchLogWriter.run("환율 확정", () ->
                confirmService.confirm(today).map(r -> {
                    log.info("[배치] 환율 확정 — {} {}{}", r.rateDate(), r.usdKrw(),
                            r.substituted() ? " (대체)" : "");
                    return 1;
                }).orElseGet(() -> {
                    log.warn("[배치] {} 환율을 확정하지 못했다", today);
                    return 0;
                }));
    }
}
