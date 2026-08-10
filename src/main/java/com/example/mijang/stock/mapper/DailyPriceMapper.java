package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.CandleResponse;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * daily_prices(일봉) 접근. PK 는 (symbol, trade_date).
 *
 * <p>개발명세서(MVC) · 종목/검색/일봉 · mapper
 */
@Mapper
public interface DailyPriceMapper {

    /** 기간 일봉 조회. STOCK-003 */
    List<CandleResponse> findByRange(@Param("symbol") String symbol,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
}
