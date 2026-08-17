/*
 * StockMetricsMapper — 투자 지표 테이블 접근
 *
 * 이 파일이 하는 일
 *   stock_metrics 를 읽고 쓴다.
 *
 *   이 표가 stocks 와 따로 있는 이유 — 값이 매일 바뀌고(시가총액·PER), 전 종목이 아니라
 *   사용자가 열어 본 종목만 채우기 때문이다. stocks 에 붙이면 13,000행 대부분이 빈 채로 남는다.
 */
package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.StockMetricsResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockMetricsMapper {

    /** 저장된 지표 한 건. 없으면 null 이다. */
    StockMetricsResponse findBySymbol(@Param("symbol") String symbol);

    /** 넣기 겸 갱신. 같은 종목을 다시 받아도 한 줄만 남는다. */
    int upsert(StockMetricsResponse metrics);

    /**
     * DB 의 지금 시각.
     *
     * <p>{@code synced_at} 은 DB 가 {@code CURRENT_TIMESTAMP(3)} 으로 찍는다. 신선도를
     * 자바 시각과 견주면 서로 다른 시계를 비교하게 되고, 표준시가 어긋나면 저장된 값이
     * 언제나 만료로 읽혀 <b>요청마다 벤더를 부른다</b> — Finnhub 는 분당 60회다.
     */
    java.time.LocalDateTime now();

    /** 지표가 채워진 종목 수. */
    int count();
}
