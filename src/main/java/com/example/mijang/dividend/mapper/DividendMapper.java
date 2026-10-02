package com.example.mijang.dividend.mapper;

import com.example.mijang.dividend.domain.Dividend;
import com.example.mijang.dividend.dto.DividendResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** dividends(배당 기록) 테이블에 접근한다. 모든 조회·변경은 user_id 를 함께 건다. PROFIT-11·12. */
@Mapper
public interface DividendMapper {

    /** 배당을 저장한다. uk(portfolio_id, symbol, pay_date) 위반은 부르는 쪽이 409 로 바꾼다. */
    int insert(Dividend dividend);

    /** 같은 (포트폴리오·종목·지급일)이 이미 있으면 넘어가는 저장을 한다. 반환 0 이면 이미 있던 것이다. */
    int insertIgnore(Dividend dividend);

    /** 방금 저장한 기록의 id 를 반환한다. insert 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /** 사용자의 배당 목록을 최근 지급일 순으로 조회한다. 삭제 표시된 행은 뺀다. */
    List<DividendResponse> findByUser(@Param("userId") Long userId);

    /** 한 건을 조회한다. 소유 확인을 겸해 남의 것이면 null 이다. */
    Dividend findById(@Param("id") Long id, @Param("userId") Long userId);

    /** 예상을 확정으로 바꾼다. status='ESTIMATED' 인 행만 갱신하며, 반환 0 이면 이미 확정됐거나 없는 행이다. */
    int confirm(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("netAmountUsd") BigDecimal netAmountUsd,
                @Param("fxRate") BigDecimal fxRate,
                @Param("netAmountKrw") BigDecimal netAmountKrw,
                @Param("payDate") LocalDate payDate);

    /** 세후 금액·환율·원화 환산을 함께 수정한다. */
    int update(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("netAmountUsd") BigDecimal netAmountUsd,
               @Param("fxRate") BigDecimal fxRate,
               @Param("netAmountKrw") BigDecimal netAmountKrw,
               @Param("payDate") LocalDate payDate);

    /** 삭제 표시만 하고 실제로 지우지 않는다. */
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 기간 안 확정 배당 합(원)을 구한다. 없으면 null 이다. */
    BigDecimal sumConfirmedKrwBetween(@Param("userId") Long userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** 확정 대기 건수를 센다. */
    long countEstimated(@Param("userId") Long userId);

    /** 확정 대기 예상 금액 합(원)을 구한다. 없으면 null 이다. */
    BigDecimal sumEstimatedKrw(@Param("userId") Long userId);

    /** 오늘 이후 가장 가까운 배당 한 건을 조회한다. 없으면 null 이다. */
    DividendResponse findNextUpcoming(@Param("userId") Long userId,
                                      @Param("today") LocalDate today);
}
