package com.example.mijang.dividend.mapper;

import com.example.mijang.dividend.domain.StockEarnings;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** stock_earnings 접근. 배당 마스터(StockDividendMapper)와 같은 결. */
@Mapper
public interface StockEarningsMapper {

    /** 수집 upsert. 같은 (symbol, report_date) 는 나머지를 갱신한다. */
    int upsert(StockEarnings earnings);

    /** 기간 내 발표 일정. 캘린더가 쓴다. */
    List<StockEarnings> findByReportDateBetween(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /** 이 종목의 기준일 이후 첫 발표. 종목 상세가 쓴다. 없으면 null. */
    StockEarnings findNextBySymbol(@Param("symbol") String symbol,
                                   @Param("onOrAfter") LocalDate onOrAfter);
}
