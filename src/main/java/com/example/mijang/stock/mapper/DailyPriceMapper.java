package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.HighLow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** daily_prices(일봉) 테이블 접근 매퍼다. */
@Mapper
public interface DailyPriceMapper {

    /** 기간의 일봉을 조회한다. */
    List<CandleResponse> findByRange(@Param("symbol") String symbol,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** 가장 최근 일봉 한 건을 조회한다. */
    CandleResponse findLatest(@Param("symbol") String symbol);

    /** 그 거래일의 정규장 종가를 날짜로 집어 온다. 상대 위치로 잡으면 장중에 기준가가 하루씩 밀린다. */
    java.math.BigDecimal findCloseOn(@Param("symbol") String symbol,
                                     @Param("tradeDate") java.time.LocalDate tradeDate);

    /** 기간 내 최고가·최저가를 매번 집계해 돌려준다. */
    HighLow findHighLow(@Param("symbol") String symbol, @Param("from") LocalDate from);

    /** 일봉을 저장한다. 벤더 정정에 대비해 이미 있으면 덮어쓴다. */
    int upsert(@Param("symbol") String symbol,
               @Param("tradeDate") LocalDate tradeDate,
               @Param("open") java.math.BigDecimal open,
               @Param("high") java.math.BigDecimal high,
               @Param("low") java.math.BigDecimal low,
               @Param("close") java.math.BigDecimal close,
               @Param("volume") long volume);
}
