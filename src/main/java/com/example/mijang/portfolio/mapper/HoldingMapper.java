package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** holdings(보유 현황) 테이블에 접근하는 매퍼다. */
@Mapper
public interface HoldingMapper {

    /** 보유 현황을 시세·평가금액과 함께 조회한다. 수량 0 인 행은 뺀다. */
    List<HoldingResponse> findByUser(@Param("userId") Long userId,
                                     @Param("fxRate") BigDecimal fxRate);

    /** 재계산 결과를 저장한다. 이미 있으면 갱신한다. */
    int upsert(@Param("userId") Long userId,
               @Param("portfolioId") Long portfolioId,
               @Param("symbol") String symbol,
               @Param("quantity") BigDecimal quantity,
               @Param("avgPrice") BigDecimal avgPrice,
               @Param("avgFxRate") BigDecimal avgFxRate,
               @Param("totalFee") BigDecimal totalFee,
               @Param("realizedPnlKrw") BigDecimal realizedPnlKrw);

    /** 총 평가금액(원)을 계산한다. 보유가 없으면 null 이다. */
    BigDecimal sumMarketValueKrw(@Param("userId") Long userId, @Param("fxRate") BigDecimal fxRate);

    /** 한 종목의 보유 수량을 조회한다. 산 적이 없으면 null, 전량 매도했으면 0 이다. */
    BigDecimal findQuantity(@Param("userId") Long userId, @Param("symbol") String symbol);

    /** 손익 분해 입력값을 조회한다. 현재가가 없는 종목도 포함해 돌려준다. */
    List<SymbolPnl> findForPnl(@Param("userId") Long userId, @Param("symbol") String symbol);

    /** asOf 날짜 이하의 마지막 종가를 붙인 손익 분해 입력값을 조회한다. 과거 백필용이다. */
    List<SymbolPnl> findForPnlAsOf(@Param("userId") Long userId,
                                   @Param("symbol") String symbol,
                                   @Param("asOf") java.time.LocalDate asOf);
}
