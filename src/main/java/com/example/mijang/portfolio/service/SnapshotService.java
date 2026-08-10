package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.mapper.DailySnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 일별 자산 스냅샷. 개발명세서(API) SNAP-001 · 장 마감 후 배치가 적재한다.
 */
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final DailySnapshotMapper dailySnapshotMapper;

    public void createDailySnapshot() {
        throw new UnsupportedOperationException("TODO: 미국 장 마감 후 사용자별 스냅샷 생성");
    }
}
