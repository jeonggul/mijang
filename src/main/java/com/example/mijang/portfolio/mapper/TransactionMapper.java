package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 매매 원장인 transactions 테이블에 접근하는 매퍼다. */
@Mapper
public interface TransactionMapper {

    /** 매매 기록 한 건을 저장한다. */
    int insert(@Param("userId") Long userId,
               @Param("portfolioId") Long portfolioId,
               @Param("symbol") String symbol,
               @Param("side") String side,
               @Param("quantity") BigDecimal quantity,
               @Param("price") BigDecimal price,
               @Param("fxRate") BigDecimal fxRate,
               @Param("fee") BigDecimal fee,
               @Param("tradedAt") LocalDateTime tradedAt,
               @Param("tradeDate") LocalDate tradeDate,
               @Param("buyReason") String buyReason,
               @Param("targetPrice") BigDecimal targetPrice,
               @Param("sentiment") String sentiment);

    /** 방금 저장한 기록의 id 를 조회한다. insert 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /** 본인 기록 한 건을 통째로 고친다. 0 을 돌려주면 없거나 남의 것이다. */
    int update(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("symbol") String symbol,
               @Param("side") String side,
               @Param("quantity") BigDecimal quantity,
               @Param("price") BigDecimal price,
               @Param("fxRate") BigDecimal fxRate,
               @Param("fee") BigDecimal fee,
               @Param("tradedAt") LocalDateTime tradedAt,
               @Param("tradeDate") LocalDate tradeDate,
               @Param("buyReason") String buyReason,
               @Param("targetPrice") BigDecimal targetPrice,
               @Param("sentiment") String sentiment);

    /** 재계산용으로 한 종목의 거래를 거래일 오름차순으로 전부 가져온다. 순서가 바뀌면 평단가가 달라진다. */
    List<Transaction> findForRecalc(@Param("userId") Long userId,
                                    @Param("symbol") String symbol);

    /** 목록을 최근 거래 순으로 조회한다. */
    List<TransactionResponse> findByUser(@Param("userId") Long userId,
                                         @Param("symbol") String symbol,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /** CSV 내보내기용 전체 기록을 필터 적용해 조회한다. */
    List<TransactionResponse> findForExport(@Param("userId") Long userId,
                                            @Param("symbol") String symbol,
                                            @Param("side") String side,
                                            @Param("from") LocalDate from,
                                            @Param("toExclusive") LocalDate toExclusive);

    /** 사용자의 매매 기록 수를 센다. */
    long countByUser(@Param("userId") Long userId, @Param("symbol") String symbol);

    /** 매매 기록 한 건을 조회한다. */
    Transaction findById(@Param("id") Long id, @Param("userId") Long userId);

    /** 삭제 표시만 한다. 0 을 돌려주면 없거나 남의 기록이다. */
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 사용자가 거래한 종목 티커 목록을 조회한다. */
    List<String> findSymbolsByUser(@Param("userId") Long userId);
}
