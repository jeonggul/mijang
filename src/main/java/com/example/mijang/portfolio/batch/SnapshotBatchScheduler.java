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

    /**
     * 08:00 — 일봉 수집(07:00) 한 시간 뒤다. 요일 범위도 일봉과 맞춘다.
     *
     * <p>주기를 설정으로 뺀 이유 — 배치가 제대로 도는지 보려면 하루를 기다려야 하고,
     * 확인하려고 코드의 cron 을 고쳤다가 되돌리는 것을 잊으면 운영 주기가 바뀐다.
     * 기본값은 그대로이므로 아무 것도 주지 않으면 예전과 같이 돈다.
     */
    @Scheduled(cron = "${mijang.batch.snapshot-cron:0 0 8 * * TUE-SAT}", zone = "Asia/Seoul")
    public void run() {
        snapshotService.createDailySnapshot();
    }
}
