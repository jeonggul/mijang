/*
 * SnapshotBatchScheduler — 스냅샷 배치
 *
 * 이 파일이 하는 일
 *   매일 정해진 시각에 사용자마다 스냅샷을 한 줄씩 찍는다.
 *   일봉 수집보다 뒤에 돌아야 한다 — 그날 종가가 아직 없으면 전날 값으로
 *   찍혀 추이가 하루씩 밀린다.
 */
package com.example.mijang.portfolio.batch;

import com.example.mijang.portfolio.service.SnapshotService;
import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.common.time.TradingClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일별 자산 스냅샷. 미국 장 마감 후 하루 1회.
 *
 * <p>일봉 수집(07:00)보다 <b>뒤에</b> 돌아야 한다. 그날 종가가 없으면 평가액이
 * 전날 값으로 찍혀 추이가 하루씩 밀린다.
 *
 * <p>개발명세서 '실시간·배치 상세' 시트
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SnapshotBatchScheduler {

    private final SnapshotService snapshotService;
    private final BatchLogWriter batchLogWriter;
    private final MarketCalendar marketCalendar;
    private final TradingClock tradingClock;

    /**
     * 08:00 — 일봉 수집(07:00) 한 시간 뒤다. 요일 범위도 일봉과 맞춘다.
     *
     * <p>주기를 설정으로 뺀 이유 — 배치가 제대로 도는지 보려면 하루를 기다려야 하고,
     * 확인하려고 코드의 cron 을 고쳤다가 되돌리는 것을 잊으면 운영 주기가 바뀐다.
     * 기본값은 그대로이므로 아무 것도 주지 않으면 예전과 같이 돈다.
     */
    @Scheduled(cron = "${mijang.batch.snapshot-cron:0 0 8 * * TUE-SAT}", zone = "Asia/Seoul")
    public void run() {
        /* 휴장일에 안 돈 것과 실패해서 못 돈 것은 다르다(admin 2.4).
           서비스도 같은 판정을 하지만 0 건으로만 돌려주므로 여기서 한 번 더 본다 —
           그래야 관리자 화면이 "건너뜀" 과 "0건 처리" 를 구분해 보여준다 */
        if (!marketCalendar.isTradingDay(tradingClock.today())) {
            batchLogWriter.skip("일별 스냅샷", "거래일이 아니다");
            return;
        }
        batchLogWriter.run("일별 스냅샷", snapshotService::createDailySnapshot);
    }
}
