package com.example.mijang.fx.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * fx_rates(원달러 환율) 접근.
 *
 * <p>개발명세서(MVC) · 환율 · mapper
 */
@Mapper
public interface FxRateMapper {

    /** 기준일 환율 조회. FX-001 */
    java.math.BigDecimal findRateByDate(@Param("baseDate") LocalDate baseDate);
}
