/*
 * FxConfirmScheduler — 그날 환율을 확정하는 배치
 *
 * 이 파일이 하는 일
 *   하루가 끝나면 그날 마지막 시세를 확정값으로 옮긴다.
 *
 *   23시 50분(KST)에 돈다. 자정을 넘기면 "그날" 이 바뀌어 버리고,
 *   너무 이르면 저녁 시세를 놓친다.
 *
 *   일별 자산 스냅샷 배치가 이 값을 집어간다. 그쪽은 미국 장 마감 후에 도므로
 *   한국 시각으로는 다음 날 새벽이다 — 그때 전날 확정값이 이미 있어야 한다.
 */
package com.example.mijang.fx.batch;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.service.FxConfirmService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 확정 환율 배치. {@code GLOBAL-01} */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FxConfirmScheduler {

    private final FxConfirmService confirmService;

    /** 23:50 KST. 한국의 하루를 기준으로 자른다. */
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(TradingClock.SERVICE_ZONE);
        confirmService.confirm(today).ifPresentOrElse(
                r -> log.info("[배치] 환율 확정 — {} {}{}", r.rateDate(), r.usdKrw(),
                        r.substituted() ? " (대체)" : ""),
                () -> log.warn("[배치] {} 환율을 확정하지 못했다", today));
    }
}
