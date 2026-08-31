package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.HighLow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * daily_prices(일봉) 접근. PK 는 (symbol, trade_date).
 *
 * <p>PK 순서 덕분에 "한 종목의 기간 조회"가 범위 스캔으로 끝난다.
 *
 * <p>개발명세서(MVC) · 종목/검색/일봉 · mapper
 */
@Mapper
public interface DailyPriceMapper {

    /** 기간 일봉 조회. {@code PRICE-05} */
    List<CandleResponse> findByRange(@Param("symbol") String symbol,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * 가장 최근 일봉 한 건.
     *
     * <p>실시간이 붙기 전까지 상세 화면의 현재가로 쓴다(2.4).
     */
    CandleResponse findLatest(@Param("symbol") String symbol);

    /**
     * 그 거래일의 정규장 종가.
     *
     * <p>"뒤에서 두 번째" 같은 상대 위치가 아니라 <b>날짜로 집는다.</b>
     * 일봉 수집은 장 마감 뒤에 돌아서, 장중에는 그날 일봉이 아직 없다.
     * 상대 위치로 잡으면 그때 기준가가 하루씩 밀린다.
     */
    java.math.BigDecimal findCloseOn(@Param("symbol") String symbol,
                                     @Param("tradeDate") java.time.LocalDate tradeDate);

    /**
     * 기간 내 최고가·최저가. {@code PRICE-04}
     *
     * <p>저장하지 않고 매번 집계한다. 이유는 2.5.
     */
    HighLow findHighLow(@Param("symbol") String symbol, @Param("from") LocalDate from);

    /**
     * 일봉 저장. 이미 있으면 갱신한다.
     *
     * <p>벤더가 나중에 값을 정정하는 경우가 있어 덮어쓸 수 있어야 한다.
     * 무시하면 틀린 값이 영영 남는다.
     */
    int upsert(@Param("symbol") String symbol,
               @Param("tradeDate") LocalDate tradeDate,
               @Param("open") java.math.BigDecimal open,
               @Param("high") java.math.BigDecimal high,
               @Param("low") java.math.BigDecimal low,
               @Param("close") java.math.BigDecimal close,
               @Param("volume") long volume);
}
