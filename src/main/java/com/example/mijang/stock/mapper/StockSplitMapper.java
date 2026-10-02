package com.example.mijang.stock.mapper;

import com.example.mijang.stock.domain.StockSplit;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** stock_splits(주식 분할) 테이블 접근 매퍼다. */
@Mapper
public interface StockSplitMapper {

    /** 한 종목의 분할 전부를 기준일 오름차순으로 조회한다. */
    List<StockSplit> findBySymbol(@Param("symbol") String symbol);

    /** 분할을 넣되 이미 있으면 조용히 넘어간다. 같은 사건이 두 번 들어가면 보정 배수가 제곱이 된다. */
    int insertIgnore(@Param("symbol") String symbol,
                     @Param("exDate") LocalDate exDate,
                     @Param("splitType") String splitType,
                     @Param("oldRate") java.math.BigDecimal oldRate,
                     @Param("newRate") java.math.BigDecimal newRate,
                     @Param("vendorId") String vendorId);

    /** 지금 누군가 보유 중인 종목을 조회한다. */
    List<String> findHeldSymbols();

    /** 분할이 기록된 종목 수를 센다. */
    long count();
}
