/*
 * DividendMapper — dividends 테이블 접근
 *
 * 이 파일이 하는 일
 *   배당 기록을 넣고 읽는 통로다. 모든 조회·변경이 user_id 를 함께 물어
 *   남의 배당은 애초에 닿지 않는다.
 *   확정은 조건부 갱신(status='ESTIMATED')이라 이미 확정된 행에는 손대지
 *   않는다 — 두 번 눌러도 한 번만 성공한다.
 */
package com.example.mijang.dividend.mapper;

import com.example.mijang.dividend.domain.Dividend;
import com.example.mijang.dividend.dto.DividendResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * dividends(배당 기록) 접근. 개발명세서(API) PROFIT-11·12
 */
@Mapper
public interface DividendMapper {

    /** 저장. uk(portfolio_id, symbol, pay_date) 위반은 부르는 쪽이 409 로 바꾼다. */
    int insert(Dividend dividend);

    /**
     * 있으면 넘어가는 저장. 예상 배당 생성(PROFIT-12)이 쓴다.
     *
     * <p>같은 (포트폴리오·종목·지급일)이 이미 있으면 — 직접 입력했든 지난 배치가
     * 만들었든 — 건드리지 않는다. 배치를 다시 돌려도 안전한 이유다.
     *
     * @return 실제로 들어간 행 수. 0 이면 이미 있던 것
     */
    int insertIgnore(Dividend dividend);

    /** 방금 저장한 기록의 id. insert 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /** 목록. 최근 지급일이 위로 온다. 삭제 표시된 행은 빠진다. */
    List<DividendResponse> findByUser(@Param("userId") Long userId);

    /** 한 건 조회. 소유 확인을 겸한다 — 남의 것이면 null. */
    Dividend findById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 예상 → 확정. <b>status='ESTIMATED' 인 행만</b> 바꾼다.
     *
     * @return 바뀐 행 수. 0 이면 이미 확정됐거나 없는 행이다
     */
    int confirm(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("netAmountUsd") BigDecimal netAmountUsd,
                @Param("fxRate") BigDecimal fxRate,
                @Param("netAmountKrw") BigDecimal netAmountKrw,
                @Param("payDate") LocalDate payDate);

    /** 수정. 세후 금액·환율·원화 환산을 함께 갱신한다. */
    int update(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("netAmountUsd") BigDecimal netAmountUsd,
               @Param("fxRate") BigDecimal fxRate,
               @Param("netAmountKrw") BigDecimal netAmountKrw,
               @Param("payDate") LocalDate payDate);

    /** 삭제 표시. 지우지 않는다 — 매매 기록과 같은 규칙이다. */
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 기간 안 확정 배당 합(원). 요약 띠의 "올해 누적" 이다. 없으면 null. */
    BigDecimal sumConfirmedKrwBetween(@Param("userId") Long userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** 확정 대기 건수. */
    long countEstimated(@Param("userId") Long userId);

    /** 확정 대기 예상 금액 합(원). 없으면 null. */
    BigDecimal sumEstimatedKrw(@Param("userId") Long userId);

    /** 오늘 이후 가장 가까운 배당 한 건. 없으면 null. */
    DividendResponse findNextUpcoming(@Param("userId") Long userId,
                                      @Param("today") LocalDate today);
}
