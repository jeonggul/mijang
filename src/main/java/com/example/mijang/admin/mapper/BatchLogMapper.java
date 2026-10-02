/*
 * BatchLogMapper — 배치 기록 테이블 접근
 *
 * 이 파일이 하는 일
 *   batch_logs 를 읽고 쓰는 통로다.
 *   시작할 때 한 줄 넣고 끝날 때 그 줄을 채우는 두 단계로 쓴다 —
 *   도중에 서버가 죽어도 "시작만 하고 안 끝난" 흔적이 남아야 하기 때문이다.
 */
package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.BatchLogResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * batch_logs 접근. 배치가 도는지 확인하는 근거다(2.4).
 */
@Mapper
public interface BatchLogMapper {

    /** 시작 기록. 배치가 시작할 때 남긴다. */
    int insertStart(@Param("jobName") String jobName,
                    @Param("startedAt") LocalDateTime startedAt);

    /** 방금 만든 행 id. insertStart 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /** 종료 기록. 시작 행을 채워 넣는다. */
    int finish(@Param("id") Long id,
               @Param("finishedAt") LocalDateTime finishedAt,
               @Param("durationMs") int durationMs,
               @Param("processedCount") int processedCount,
               @Param("status") String status,
               @Param("message") String message);

    /**
     * 잡별 최근 실행 한 건씩.
     *
     * <p>화면이 "무엇이 언제 돌았고 성공했는가"를 한눈에 보는 용도다.
     */
    List<BatchLogResponse> findLatestPerJob();
}
