package com.example.mijang.dividend.mapper;

import com.example.mijang.dividend.domain.StockEarnings;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** stock_earnings(실적 발표 일정) 테이블에 접근한다. */
@Mapper
public interface StockEarningsMapper {

    /** 저장하되, 같은 (symbol, report_date) 는 나머지 값을 갱신한다. */
    int upsert(StockEarnings earnings);

    /** 기간 내 발표 일정을 조회한다. */
    List<StockEarnings> findByReportDateBetween(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /** 종목의 기준일 이후 첫 발표를 조회한다. 없으면 null 이다. */
    StockEarnings findNextBySymbol(@Param("symbol") String symbol,
                                   @Param("onOrAfter") LocalDate onOrAfter);
}
