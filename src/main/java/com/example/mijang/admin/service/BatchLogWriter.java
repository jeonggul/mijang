package com.example.mijang.admin.service;

import com.example.mijang.admin.domain.BatchStatus;
import com.example.mijang.admin.mapper.BatchLogMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 배치 실행을 감싸 시작·끝·처리 건수를 batch_logs 에 남긴다. 기록 실패는 삼킨다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchLogWriter {

    private final BatchLogMapper batchLogMapper;

    /** 작업을 감싸 실행하고 결과를 남긴다 — 작업이 던진 예외는 기록한 뒤 다시 던진다. */
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

    /** 돌 필요가 없어 건너뛴 경우를 SKIPPED 로 남긴다. */
    public void skip(String jobName, String reason) {
        LocalDateTime now = LocalDateTime.now();
        Long logId = start(jobName, now);
        finish(logId, now, 0, BatchStatus.SKIPPED, reason);
    }

    /** 시작 행을 남긴다. 실패해도 배치는 계속 간다. */
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
