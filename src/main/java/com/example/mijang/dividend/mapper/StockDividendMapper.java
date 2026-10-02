package com.example.mijang.dividend.mapper;

import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.dto.HolderAtExDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** stock_dividends(종목 배당 마스터) 테이블에 접근한다. PROFIT-12 · INFO-06. */
@Mapper
public interface StockDividendMapper {

    /** 저장하되, 같은 (종목·배당락일·유형)이 있으면 새 값으로 덮는다. */
    int upsert(StockDividend dividend);

    /** 한 종목의 배당 전부를 배당락일 내림차순으로 조회한다. */
    List<StockDividend> findBySymbol(@Param("symbol") String symbol);

    /** 마지막 수집 시각을 조회한다. 없으면 null 이다. */
    LocalDateTime findLastSyncedAt(@Param("symbol") String symbol);

    /** 기간 안에 배당락일이 있는 이벤트를 조회한다. */
    List<StockDividend> findByExDateBetween(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** 배당락일 시점의 보유자와 수량을 조회한다. transactions 를 읽기 전용으로만 본다. */
    List<HolderAtExDate> findHoldersAtExDate(@Param("symbol") String symbol,
                                             @Param("exDate") LocalDate exDate);

    /** 지금 보유 중인 종목 티커 전부(사용자 무관)를 조회한다. */
    List<String> findHeldSymbols();

    /** 종목의 기준일 이후 첫 배당락을 조회한다. 없으면 null 이다. */
    StockDividend findNextExDateBySymbol(@Param("symbol") String symbol,
                                         @Param("onOrAfter") LocalDate onOrAfter);
}
