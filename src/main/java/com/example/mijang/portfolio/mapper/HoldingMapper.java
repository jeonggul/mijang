/*
 * HoldingMapper — 보유 현황 테이블 접근
 *
 * 이 파일이 하는 일
 *   holdings 를 읽고 쓰는 통로다.
 *   이 표는 원장이 아니라 매매 기록에서 다시 계산되어 채워지는 파생 표다.
 *   그래서 갱신은 늘 "넣기 겸 덮어쓰기" 한 가지뿐이다.
 */
package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.dto.HoldingResponse;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * holdings(보유 현황) 접근. 매매 기록에서 재계산되는 파생 표다(2.1).
 *
 * <p>개발명세서(MVC) · 포트폴리오 · mapper
 */
@Mapper
public interface HoldingMapper {

    /**
     * 보유 현황 조회. 시세와 평가금액을 함께 붙인다.
     *
     * <p>수량 0 인 행은 <b>목록에서 뺀다.</b> 표에는 남겨 두지만(2.4) 화면에 보일 이유는 없다.
     *
     * @param fxRate 평가에 쓸 현재 환율. null 이면 원화 금액이 전부 null 로 나온다
     */
    List<HoldingResponse> findByUser(@Param("userId") Long userId,
                                     @Param("fxRate") BigDecimal fxRate);

    /**
     * 재계산 결과 저장. 이미 있으면 갱신한다.
     *
     * <p>{@code (portfolio_id, symbol)} 이 UNIQUE 라 종목마다 한 행이다.
     */
    int upsert(@Param("userId") Long userId,
               @Param("portfolioId") Long portfolioId,
               @Param("symbol") String symbol,
               @Param("quantity") BigDecimal quantity,
               @Param("avgPrice") BigDecimal avgPrice,
               @Param("avgFxRate") BigDecimal avgFxRate,
               @Param("totalFee") BigDecimal totalFee,
               @Param("realizedPnlKrw") BigDecimal realizedPnlKrw);

    /** 총 평가금액(원). ACCOUNT-07. 보유가 없으면 null. */
    BigDecimal sumMarketValueKrw(@Param("userId") Long userId, @Param("fxRate") BigDecimal fxRate);
}
