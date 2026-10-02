/*
 * DailySnapshotMapper — 스냅샷 테이블 접근
 *
 * 이 파일이 하는 일
 *   daily_snapshots 를 읽고 쓰는 통로다.
 *   기간으로 뽑기(그래프), 기간의 처음·끝 한 건씩 찾기(수익률),
 *   그리고 배치가 쓸 "보유 종목이 있는 사용자 목록"을 낸다.
 */
package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * daily_snapshots 접근. PK 는 (user_id, portfolio_id, snapshot_date).
 *
 * <p>개발명세서(MVC) · 포트폴리오 · mapper
 */
@Mapper
public interface DailySnapshotMapper {

    /**
     * 스냅샷 저장. 같은 날을 다시 찍으면 갱신한다(2.5).
     *
     * <p>일봉이 정정되거나 놓친 날을 메울 때 덮어쓸 수 있어야 한다.
     */
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

    /** 기간 추이. 차트가 그대로 쓴다. */
    List<SnapshotResponse> findByRange(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * 기간 시작점.
     *
     * <p>정확히 그 날짜가 없을 수 있다(주말·휴장일). <b>그 날짜 이후 첫 스냅샷</b>을 집는다.
     */
    SnapshotResponse findFirstOnOrAfter(@Param("userId") Long userId,
                                        @Param("from") LocalDate from);

    /** 기간 끝점. 그 날짜 이전 마지막 스냅샷. */
    SnapshotResponse findLastOnOrBefore(@Param("userId") Long userId,
                                        @Param("to") LocalDate to);

    /** 스냅샷 대상 사용자. 보유가 있는 사람만 나온다(2.6). */
    List<Long> findUserIdsWithHoldings();
}
