/*
 * BatchLogWriter — 배치를 감싸 기록을 남기는 곳
 *
 * 이 파일이 하는 일
 *   각 배치가 이걸 통해 돌면 시작·끝·처리 건수가 저절로 남는다.
 *   배치마다 기록 코드를 적으면 빠뜨리는 데가 생기므로 한 곳으로 모았다.
 *   기록을 남기다 실패해도 배치 자체는 되돌리지 않는다 — 기록 때문에
 *   정작 해야 할 일이 취소되면 본말이 뒤바뀐다.
 */
package com.example.mijang.admin.service;

import com.example.mijang.admin.domain.BatchStatus;
import com.example.mijang.admin.mapper.BatchLogMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 배치 실행을 감싸 기록을 남긴다. 개발명세서(API) ADMIN-02
 *
 * <p>각 스케줄러가 이걸 통해 돌면 관리자 화면이 "무엇이 언제 돌았는가"를 볼 수 있다(2.4).
 *
 * <p>기록 때문에 배치가 실패하면 안 되므로, <b>기록 실패는 삼킨다</b>(2.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchLogWriter {

    private final BatchLogMapper batchLogMapper;

    /**
     * 작업을 감싸 실행하고 결과를 남긴다.
     *
     * <p>시작 행을 {@code FAILED} 로 먼저 넣는 이유 — 서버가 중간에 죽어도
     * 실패로 남아 눈에 띈다. 성공하면 끝에서 고친다.
     *
     * <p>작업이 던진 예외는 <b>기록한 뒤 다시 던진다.</b> 삼키면 스케줄러가
     * 성공한 줄 안다.
     *
     * @param jobName 잡 이름. 화면에 그대로 보인다
     * @param work    처리 건수를 돌려주는 작업
     * @return 처리 건수
     */
    public int run(String jobName, IntSupplier work) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long logId = start(jobName, startedAt);

        try {
            int count = work.getAsInt();
            finish(logId, startedAt, count, BatchStatus.SUCCESS, null);
            return count;
        } catch (RuntimeException e) {
            finish(logId, startedAt, 0, BatchStatus.FAILED, message(e));
            throw e;
        }
    }

    /**
     * 돌 필요가 없어 건너뛴 경우를 남긴다.
     *
     * <p>휴장일에 안 돈 것과 실패해서 못 돈 것을 구분한다(2.4).
     */
    public void skip(String jobName, String reason) {
        LocalDateTime now = LocalDateTime.now();
        Long logId = start(jobName, now);
        finish(logId, now, 0, BatchStatus.SKIPPED, reason);
    }

    /** 시작 행을 남긴다. 실패해도 배치는 계속 간다(2.3). */
    private Long start(String jobName, LocalDateTime startedAt) {
        try {
            batchLogMapper.insertStart(jobName, startedAt);
            return batchLogMapper.findLastInsertedId();
        } catch (RuntimeException e) {
            log.warn("[배치로그] 시작 기록 실패 — {}", jobName, e);
            return null;
        }
    }

    /** 종료 행을 채운다. 시작 기록이 없었으면 할 일이 없다. */
    private void finish(Long logId, LocalDateTime startedAt,
                        int count, BatchStatus status, String message) {
        if (logId == null) {
            return;
        }
        try {
            LocalDateTime finishedAt = LocalDateTime.now();
            int durationMs = (int) Duration.between(startedAt, finishedAt).toMillis();
            batchLogMapper.finish(logId, finishedAt, durationMs, count, status.name(), message);
        } catch (RuntimeException e) {
            log.warn("[배치로그] 종료 기록 실패", e);
        }
    }

    /** 예외 문구를 컬럼 길이에 맞게 자른다. VARCHAR(500) 을 넘기면 저장이 실패한다. */
    private String message(Exception e) {
        String raw = e.getClass().getSimpleName() + ": " + e.getMessage();
        return raw.length() > 500 ? raw.substring(0, 500) : raw;
    }
}
