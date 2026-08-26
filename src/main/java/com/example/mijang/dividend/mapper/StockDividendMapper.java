/*
 * StockDividendMapper — stock_dividends 테이블 접근
 *
 * 이 파일이 하는 일
 *   종목 배당 마스터를 넣고 읽는다. 넣기는 upsert 다 — 벤더가 지급일을
 *   정정해 보내는 일이 있어, 같은 (종목·배당락일·유형)이면 새 값으로 덮는다.
 *   예상 배당 생성이 쓰는 두 조회(기간 내 이벤트 · 배당락일 시점 보유자)도
 *   여기에 있다.
 */
package com.example.mijang.dividend.mapper;

import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.dto.HolderAtExDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * stock_dividends(종목 배당 마스터) 접근. 개발명세서(API) PROFIT-12 · INFO-06
 */
@Mapper
public interface StockDividendMapper {

    /** 저장. 이미 있으면(종목·배당락일·유형) 새 값으로 덮는다 — 벤더 정정 대응. */
    int upsert(StockDividend dividend);

    /** 한 종목의 배당 전부. 배당락일 내림차순. */
    List<StockDividend> findBySymbol(@Param("symbol") String symbol);

    /** 마지막 수집 시각. 없으면 null — 신선도 판단에 쓴다. */
    LocalDateTime findLastSyncedAt(@Param("symbol") String symbol);

    /** 기간 안에 배당락일이 있는 이벤트. 예상 배당 생성의 입력이다. */
    List<StockDividend> findByExDateBetween(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /**
     * 배당락일 시점의 보유자와 수량.
     *
     * <p>transactions 를 읽는다 — 이 매퍼의 표가 아니지만, "배당락일 전일까지
     * 보유" 판정은 예상 배당 생성만 쓰는 질의라 여기에 둔다. 원장의 쓰기 규칙은
     * 건드리지 않고 읽기만 한다.
     */
    List<HolderAtExDate> findHoldersAtExDate(@Param("symbol") String symbol,
                                             @Param("exDate") LocalDate exDate);

    /** 지금 보유 중인 종목 티커 전부(사용자 무관). 수집 배치의 대상이다. */
    List<String> findHeldSymbols();
}
