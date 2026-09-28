package com.example.mijang.stock.mapper;

import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.StockSearchResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** stocks(종목 마스터) 테이블 접근 매퍼다. */
@Mapper
public interface StockMapper {

    /** 전방 일치로 검색한다. 티커 완전 일치가 맨 앞에 온다. */
    List<StockSearchResponse> searchByPrefix(@Param("q") String q, @Param("limit") int limit);

    /** 티커로 한 건을 조회한다. 비활성 종목도 돌려준다. */
    Stock findBySymbol(@Param("symbol") String symbol);

    /** 시장·자산군별 목록을 조회한다. 조건이 null 이면 전체다. */
    List<StockSearchResponse> findByFilter(@Param("exchange") String exchange,
                                           @Param("assetClass") String assetClass,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 같은 조건의 전체 건수를 센다. */
    int countByFilter(@Param("exchange") String exchange,
                      @Param("assetClass") String assetClass);

    /** 마스터 동기화용 upsert 다. 이미 있으면 이름·거래소·활성 여부를 갱신한다. */
    int upsert(@Param("symbol") String symbol,
               @Param("name") String name,
               @Param("exchange") String exchange,
               @Param("assetClass") String assetClass,
               @Param("fractionable") boolean fractionable);

    /** DB 의 지금 시각을 받는다. synced_at 과 JVM 시각을 견주면 표준시가 어긋나 전 종목이 비활성으로 내려갈 수 있다. */
    java.time.LocalDateTime now();

    /** 관리자 화면용 종목 목록을 조회한다. 비활성 종목까지 보며 조건이 null 이면 전부다. */
    List<StockSearchResponse> findForAdmin(@Param("active") Boolean active,
                                           @Param("assetClass") String assetClass,
                                           @Param("q") String q,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    /** 관리자 목록 조건에 걸리는 전체 건수를 센다. */
    int countForAdmin(@Param("active") Boolean active,
                      @Param("assetClass") String assetClass,
                      @Param("q") String q);

    /** 활성·비활성을 전환한다. 과거 기록이 참조하므로 지우지는 않는다. */
    int setActive(@Param("symbol") String symbol,
                  @Param("active") boolean active,
                  @Param("reason") String reason);

    /** 이번 동기화에서 보이지 않은 종목을 비활성으로 내린다. threshold 는 반드시 now() 로 받은 DB 시각이어야 한다. */
    int deactivateNotSyncedSince(@Param("threshold") java.time.LocalDateTime threshold);

    /** 활성 종목의 티커만 조회한다. */
    List<String> findActiveSymbols();

    /** 한글 종목명을 채운다. 없는 티커면 아무 일도 하지 않는다. */
    int updateNameKo(@Param("symbol") String symbol, @Param("nameKo") String nameKo);

    /** 한글명이 채워진 종목 수를 센다. */
    int countWithNameKo();

    /** 종목 종류(security_type)와 자산군을 채운다. 값이 그대로면 건드리지 않는다. */
    int updateSecurityType(@Param("symbol") String symbol,
                           @Param("securityType") String securityType,
                           @Param("isin") String isin,
                           @Param("assetClass") String assetClass);
}
