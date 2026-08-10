package com.example.mijang.portfolio.batch;

import com.example.mijang.portfolio.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미국 장 마감 후 사용자별 일별 자산 스냅샷 생성. 화면 SR-007.
 * 주가손익·환차손익을 분리해 저장해 두어야 기간별 수익률 조회가 O(1) 이 된다.
 *
 * <p>개발명세서 '실시간·배치 상세' 시트
 * <p>구현 전이라 mijang.batch.enabled=false 로 꺼 둔다. 켜면 아래 주기대로 실제로 돌기 시작한다.
 */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SnapshotBatchScheduler {

    private final SnapshotService snapshotService;

    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "America/New_York")
    public void run() {
        log.warn("[배치] SnapshotBatchScheduler 아직 구현 전이다");
        // snapshotService.createDailySnapshot();
    }
}
