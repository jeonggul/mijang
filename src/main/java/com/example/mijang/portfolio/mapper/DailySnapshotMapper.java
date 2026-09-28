package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** daily_snapshots 테이블에 접근하는 매퍼다. */
@Mapper
public interface DailySnapshotMapper {

    /** 스냅샷을 저장한다. 같은 날을 다시 찍으면 갱신한다. */
    int upsert(@Param("userId") Long userId,
               @Param("portfolioId") Long portfolioId,
               @Param("snapshotDate") LocalDate snapshotDate,
               @Param("marketValueUsd") BigDecimal marketValueUsd,
               @Param("marketValueKrw") BigDecimal marketValueKrw,
               @Param("costBasisKrw") BigDecimal costBasisKrw,
               @Param("pricePnlKrw") BigDecimal pricePnlKrw,
               @Param("fxPnlKrw") BigDecimal fxPnlKrw,
               @Param("totalPnlKrw") BigDecimal totalPnlKrw,
               @Param("returnRate") BigDecimal returnRate,
               @Param("appliedFxRate") BigDecimal appliedFxRate,
               @Param("fxSubstituted") boolean fxSubstituted);

    /** 기간의 스냅샷 목록을 조회한다. */
    List<SnapshotResponse> findByRange(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** 해당 날짜 이후 첫 스냅샷을 조회한다. */
    SnapshotResponse findFirstOnOrAfter(@Param("userId") Long userId,
                                        @Param("from") LocalDate from);

    /** 해당 날짜 이전 마지막 스냅샷을 조회한다. */
    SnapshotResponse findLastOnOrBefore(@Param("userId") Long userId,
                                        @Param("to") LocalDate to);

    /** 보유 종목이 있는 사용자 id 목록을 조회한다. */
    List<Long> findUserIdsWithHoldings();
}
