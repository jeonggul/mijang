/*
 * FxCollectScheduler — 환율을 주기적으로 받아 오는 곳
 *
 * 이 파일이 하는 일
 *   매시 02분에 벤더를 한 번 부른다.
 *
 *   왜 1시간인가 — 무료 플랜이 월 1,000회다. 5분마다면 월 8,640회로 한도를 여덟 배 넘고,
 *   30분이어도 1,440회로 넘는다. 1시간이면 720회라 재시도 여유까지 들어간다.
 *   게다가 벤더 갱신 자체가 1시간 주기라 더 자주 불러도 같은 값이다.
 *
 *   왜 정각이 아닌가 — 벤더의 timestamp 가 정확히 정시로 온다(실측). 정각에 부르면
 *   아직 갱신 전 값을 받을 수 있다.
 */
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

    /** 매시 02분. 서머타임과 무관한 값이라 표준시를 지정하지 않는다. */
    @Scheduled(cron = "0 2 * * * *")
    public void run() {
        collectService.collect().ifPresentOrElse(
                q -> log.debug("[배치] 환율 수집 — {}", q.quotedAt()),
                () -> log.warn("[배치] 환율을 받지 못했다"));
    }
}
