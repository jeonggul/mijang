/*
 * FxRateMapper — fx_rates 접근
 *
 * 이 파일이 하는 일
 *   그날의 확정 환율을 넣고 읽는다.
 *
 *   이 표의 값은 환차손익과 일별 스냅샷이 집어간다. 한 번 확정되면 바뀌지 않아야 하므로
 *   넣기는 "없으면 넣는다"(INSERT IGNORE)로 둔다. 같은 날짜를 두 번 확정하려 해도
 *   먼저 들어간 값이 이긴다 — 나중 값으로 덮으면 이미 계산해 둔 손익과 어긋난다.
 */
package com.example.mijang.fx.mapper;

import com.example.mijang.fx.domain.FxRate;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FxRateMapper {

    /** 기준일 환율 조회. {@code GLOBAL-01}. 없으면 null */
    FxRate findByDate(@Param("rateDate") LocalDate rateDate);

    /**
     * 기준일 이전의 가장 가까운 환율. 대체 값을 찾을 때 쓴다.
     *
     * <p>{@code lookbackDays} 보다 더 거슬러 오르지 않는다. 무한정 찾으면 몇 년 전 환율로
     * 오늘 손익을 계산하게 된다 — 그럴 바에는 없다고 답하는 편이 낫다.
     */
    FxRate findLatestBefore(@Param("rateDate") LocalDate rateDate,
                            @Param("lookbackDays") int lookbackDays);

    /**
     * 확정 환율을 넣는다. 이미 그날 행이 있으면 넘어간다.
     *
     * @return 실제로 들어간 행 수
     */
    int insertIgnore(FxRate rate);
}
