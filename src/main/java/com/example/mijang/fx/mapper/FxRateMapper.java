package com.example.mijang.fx.mapper;

import com.example.mijang.fx.domain.FxRate;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 확정 환율({@code fx_rates})을 넣고 읽는다. */
@Mapper
public interface FxRateMapper {

    /** 기준일 환율을 찾는다. 없으면 null 을 준다. */
    FxRate findByDate(@Param("rateDate") LocalDate rateDate);

    /** 기준일 이전 {@code lookbackDays} 안에서 가장 가까운 환율을 찾는다. */
    FxRate findLatestBefore(@Param("rateDate") LocalDate rateDate,
                            @Param("lookbackDays") int lookbackDays);

    /** 확정 환율을 넣는다. 이미 그날 행이 있으면 넘어간다. */
    int insertIgnore(FxRate rate);
}
