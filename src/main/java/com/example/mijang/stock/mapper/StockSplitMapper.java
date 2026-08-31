/*
 * StockSplitMapper — 주식 분할 표
 *
 * 이 파일이 하는 일
 *   stock_splits 를 읽고 쓴다. 보유 재계산이 읽고, 동기화 배치가 쓴다.
 */
package com.example.mijang.stock.mapper;

import com.example.mijang.stock.domain.StockSplit;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockSplitMapper {

    /** 한 종목의 분할 전부. 기준일 오름차순이다. */
    List<StockSplit> findBySymbol(@Param("symbol") String symbol);

    /**
     * 있으면 넘어간다.
     *
     * <p>같은 사건을 두 번 넣으면 보정 배수가 제곱이 되어 수량이 터무니없이 늘어난다.
     * (symbol, ex_date, split_type) 이 기본키라 DB 가 막아 주지만, 배치가 매일
     * 같은 구간을 다시 훑으므로 IGNORE 로 조용히 넘긴다.
     */
    int insertIgnore(@Param("symbol") String symbol,
                     @Param("exDate") LocalDate exDate,
                     @Param("splitType") String splitType,
                     @Param("oldRate") java.math.BigDecimal oldRate,
                     @Param("newRate") java.math.BigDecimal newRate,
                     @Param("vendorId") String vendorId);

    /** 지금 누군가 들고 있는 종목. 분할을 받아 올 대상이다. */
    List<String> findHeldSymbols();

    /** 분할이 기록된 종목 수. 배치 로그가 쓴다. */
    long count();
}
