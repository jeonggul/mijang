/*
 * MarketDayMapper — 거래일 달력 테이블 접근
 *
 * 이 파일이 하는 일
 *   market_days 를 읽고 쓴다.
 *
 *   세션 판정이 요청마다 이 표를 보므로 조회가 가벼워야 한다. PK 가 거래일이라
 *   "그날" 과 "그 앞뒤 거래일" 조회가 인덱스 하나로 끝난다.
 */
package com.example.mijang.market.mapper;

import com.example.mijang.market.domain.MarketDay;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarketDayMapper {

    /** 그날이 거래일이면 돌려준다. 휴장일이면 null 이다. */
    MarketDay findByDate(@Param("tradeDate") LocalDate tradeDate);

    /**
     * 그날을 포함해 거슬러 올라가 가장 가까운 거래일.
     *
     * <p>휴장일·주말에 "마지막으로 열렸던 날" 을 찾는 데 쓴다. 화면이 그날 값에서 멈춰야 한다.
     */
    MarketDay findLatestOnOrBefore(@Param("tradeDate") LocalDate tradeDate);

    /** 그날보다 앞선 거래일 중 가장 가까운 날. 전일 종가를 찾는 데 쓴다. */
    MarketDay findPreviousBefore(@Param("tradeDate") LocalDate tradeDate);

    /** 넣기 겸 갱신. 달력이 정정돼도 한 줄만 남는다. */
    int upsert(MarketDay day);

    /** 담긴 거래일 수. 배치 로그와 검증에서 쓴다. */
    int count();

    /** 담긴 마지막 거래일. 달력이 얼마나 앞까지 채워졌는지 본다. */
    LocalDate findMaxDate();

    /** 기간 내 거래일 목록. 검증에서 쓴다. */
    List<MarketDay> findByRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
