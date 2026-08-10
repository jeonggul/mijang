package com.example.mijang.portfolio.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * daily_snapshots(일별 자산 스냅샷) 접근.
 *
 * <p>기간별 수익률 조회를 O(1) 로 만들기 위해 장 마감 후 배치로 적재한다.
 * <p>개발명세서(MVC) · 이력/스냅샷 · mapper
 */
@Mapper
public interface DailySnapshotMapper {

    /** 기간 스냅샷 건수. SNAP-001 */
    long countByRange(@Param("userId") Long userId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to);
}
