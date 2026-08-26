/*
 * TransactionMapper — 매매 기록 테이블 접근
 *
 * 이 파일이 하는 일
 *   transactions 를 읽고 쓰는 통로다. 이 표가 원장이라 여기가 틀리면 전부 틀린다.
 *   저장·조회 말고 "재계산용으로 한 종목의 거래를 시간순으로 전부 꺼내기"가
 *   따로 있다. 보유 현황을 다시 계산할 때 쓴다.
 */
package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * transactions(매매 기록) 접근. <b>이 표가 원장이다</b>(2.1).
 *
 * <p>개발명세서(MVC) · 포트폴리오 · mapper
 */
@Mapper
public interface TransactionMapper {

    /** 저장. 생성된 id 는 쓰지 않으므로 되돌려 받지 않는다. */
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

    /** 방금 저장한 기록의 id. insert 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /**
     * 재계산용. 한 종목의 거래를 <b>거래일 오름차순</b>으로 전부 가져온다.
     *
     * <p>정렬이 계산 결과를 좌우한다. 순서가 뒤바뀌면 평단가가 달라진다(2.2).
     * 같은 날 여러 건이면 id 순으로 이어 붙여 입력한 순서를 유지한다.
     */
    List<Transaction> findForRecalc(@Param("userId") Long userId,
                                    @Param("symbol") String symbol);

    /** 목록 조회. 최근 거래가 위로 온다. */
    List<TransactionResponse> findByUser(@Param("userId") Long userId,
                                         @Param("symbol") String symbol,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /** CSV 내보내기용 전체 기록. 화면의 종목·매수/매도·연도 필터를 서버에서 적용한다. */
    List<TransactionResponse> findForExport(@Param("userId") Long userId,
                                            @Param("symbol") String symbol,
                                            @Param("side") String side,
                                            @Param("from") LocalDate from,
                                            @Param("toExclusive") LocalDate toExclusive);

    /** 사용자의 매매 기록 수. 페이징에 쓴다. */
    long countByUser(@Param("userId") Long userId, @Param("symbol") String symbol);

    /** 한 건 조회. 삭제 전에 종목을 알아내야 재계산 대상을 정할 수 있다. */
    Transaction findById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 삭제 표시. 지우지 않는다(2.9).
     *
     * @return 바뀐 행 수. 0 이면 없거나 남의 기록이다
     */
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 보유 종목 티커 목록. 전체 재계산이 필요할 때 대상이 된다. */
    List<String> findSymbolsByUser(@Param("userId") Long userId);
}
