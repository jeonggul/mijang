package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.BatchLogResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** batch_logs 접근 — 시작 때 한 줄 넣고 끝날 때 그 줄을 채우는 두 단계로 쓴다. */
@Mapper
public interface BatchLogMapper {

    /** 배치 시작 기록을 남긴다. */
    int insertStart(@Param("jobName") String jobName,
                    @Param("startedAt") LocalDateTime startedAt);

    /** 방금 만든 행 id 를 돌려준다. insertStart 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /** 시작 행을 종료 정보로 채운다. */
    int finish(@Param("id") Long id,
               @Param("finishedAt") LocalDateTime finishedAt,
               @Param("durationMs") int durationMs,
               @Param("processedCount") int processedCount,
               @Param("status") String status,
               @Param("message") String message);

    /** 잡별 최근 실행 한 건씩을 조회한다. */
    List<BatchLogResponse> findLatestPerJob();
}
