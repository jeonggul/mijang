package com.example.mijang.market.mapper;

import com.example.mijang.market.domain.MarketDay;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** market_days 거래일 달력 테이블을 읽고 쓴다. */
@Mapper
public interface MarketDayMapper {

    /** 그날이 거래일이면 돌려주고 휴장일이면 null 을 반환한다. */
    MarketDay findByDate(@Param("tradeDate") LocalDate tradeDate);

    /** 그날을 포함해 거슬러 올라가 가장 가까운 거래일을 반환한다. */
    MarketDay findLatestOnOrBefore(@Param("tradeDate") LocalDate tradeDate);

    /** 그날보다 앞선 거래일 중 가장 가까운 날을 반환한다. 전일 종가 조회에 쓴다. */
    MarketDay findPreviousBefore(@Param("tradeDate") LocalDate tradeDate);

    /** 거래일 한 건을 넣거나 갱신한다. */
    int upsert(MarketDay day);

    /** 담긴 거래일 수를 반환한다. */
    int count();

    /** 담긴 마지막 거래일을 반환한다. */
    LocalDate findMaxDate();

    /** 기간 내 거래일 목록을 반환한다. */
    List<MarketDay> findByRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
