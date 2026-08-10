package com.example.mijang.fx.batch;

import com.example.mijang.fx.service.FxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 한국수출입은행 일별 원달러 환율 수집. 화면 SR-008 · 영업일 1일 1회.
 * 고시가 영업일 11시에 한 번뿐이고 한도가 일 1,000회라 하루 한 번이면 충분하다.
 *
 * <p>개발명세서 '실시간·배치 상세' 시트
 * <p>구현 전이라 mijang.batch.enabled=false 로 꺼 둔다. 켜면 아래 주기대로 실제로 돌기 시작한다.
 */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FxCollectScheduler {

    private final FxRateService fxRateService;

    @Scheduled(cron = "0 10 11 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        log.warn("[배치] FxCollectScheduler 아직 구현 전이다");
        // fxRateService.collectDaily();
    }
}
