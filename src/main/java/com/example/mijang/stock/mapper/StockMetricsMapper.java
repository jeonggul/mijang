package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.StockMetricsResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** stock_metrics(투자 지표) 테이블 접근 매퍼다. */
@Mapper
public interface StockMetricsMapper {

    /** 저장된 지표 한 건을 조회한다. 없으면 null 이다. */
    StockMetricsResponse findBySymbol(@Param("symbol") String symbol);

    /** 지표를 넣거나 갱신한다. 같은 종목은 한 줄만 남는다. */
    int upsert(StockMetricsResponse metrics);

    /** DB 의 지금 시각을 받는다. 신선도 비교를 JVM 시각으로 하면 표준시가 어긋나 요청마다 벤더를 부르게 된다. */
    java.time.LocalDateTime now();

    /** 지표가 채워진 종목 수를 센다. */
    int count();
}
