package com.example.mijang.stock.mapper;

import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.StockSearchResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * stocks(종목 마스터) 접근. 약 10,000 종목.
 *
 * <p>개발명세서(MVC) · 종목/검색/일봉 · mapper
 */
@Mapper
public interface StockMapper {

    /**
     * 전방 일치 검색. {@code SEARCH-01}·{@code SEARCH-02}
     *
     * <p>정렬 순서에 이유가 있다 — 티커 완전 일치가 맨 앞이다.
     * "A" 를 쳤을 때 `A`(Agilent)가 `AAPL` 뒤에 나오면 찾는 사람 입장에서 이상하다.
     */
    List<StockSearchResponse> searchByPrefix(@Param("q") String q, @Param("limit") int limit);

    /**
     * 티커로 한 건. 상세 화면과 매매 기록 검증이 쓴다.
     *
     * <p>비활성 종목도 돌려준다. 과거 기록이 참조하고 있어 화면에는 보여야 한다(2.10).
     */
    Stock findBySymbol(@Param("symbol") String symbol);

    /**
     * 시장·자산군별 목록. {@code SEARCH-03}·{@code SEARCH-04}
     *
     * @param exchange   거래소. null 이면 전체
     * @param assetClass STOCK 또는 ETF. null 이면 전체
     */
    List<StockSearchResponse> findByFilter(@Param("exchange") String exchange,
                                           @Param("assetClass") String assetClass,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /**
     * 같은 조건의 전체 건수.
     *
     * <p>"더보기" 가 더 있는지 알아야 버튼을 감출 수 있다. 건수를 모르면 빈 응답이 올 때까지
     * 눌러 보게 된다.
     */
    int countByFilter(@Param("exchange") String exchange,
                      @Param("assetClass") String assetClass);

    /**
     * 마스터 동기화용 upsert.
     *
     * <p>벤더가 준 종목을 한 건씩 넣는다. 이미 있으면 이름·거래소·활성 여부를 갱신한다.
     * 지우지 않는 이유는 2.2.
     */
    int upsert(@Param("symbol") String symbol,
               @Param("name") String name,
               @Param("exchange") String exchange,
               @Param("assetClass") String assetClass,
               @Param("fractionable") boolean fractionable);

    /**
     * DB 의 지금 시각.
     *
     * <p>{@code synced_at} 은 DB 가 {@code CURRENT_TIMESTAMP(3)} 으로 찍는다. 그 값과 비교할
     * 기준을 자바(JVM)에서 만들면 <b>서로 다른 시계를 견주게 된다</b>. 두 시계의 표준시가
     * 다르면 방금 넣은 행까지 "오래된 행" 으로 잡혀 전 종목이 비활성으로 내려간다.
     * 그래서 기준도 DB 에서 받아 온다.
     */
    java.time.LocalDateTime now();

    /**
     * 관리자 화면의 종목 목록. {@code ADMIN-01}
     *
     * <p>검색 화면({@code search})과 달리 <b>비활성 종목까지 본다.</b> 관리자가 내린 종목을
     * 다시 올리려면 목록에 보여야 한다. 비활성이 아래로 가도록 정렬한다.
     *
     * @param active     true 면 활성만, false 면 비활성만, null 이면 전부
     * @param assetClass 자산 구분. null 이면 전부
     * @param q          티커·종목명 앞부분. null 이면 전부
     */
    List<StockSearchResponse> findForAdmin(@Param("active") Boolean active,
                                           @Param("assetClass") String assetClass,
                                           @Param("q") String q,
                                           @Param("limit") int limit);

    /** 위 조건에 걸리는 전체 건수. 목록은 limit 로 잘려 나가므로 총계는 따로 센다. */
    int countForAdmin(@Param("active") Boolean active,
                      @Param("assetClass") String assetClass,
                      @Param("q") String q);

    /**
     * 활성·비활성 전환. {@code ADMIN-01}
     *
     * <p><b>지우지 않는다</b>(2.6). 과거 매매 기록이 이 종목을 참조하고 있다.
     * 다시 올릴 때는 사유를 비운다 — 내려간 이유가 남아 있으면 왜 비활성인지 헷갈린다.
     */
    int setActive(@Param("symbol") String symbol,
                  @Param("active") boolean active,
                  @Param("reason") String reason);

    /**
     * 이번 동기화에서 보이지 않은 종목을 비활성으로 내린다.
     *
     * <p>{@code synced_at} 이 이번 회차보다 오래된 행이 대상이다. 벤더 목록에서 빠졌다는 것은
     * 상장폐지되었거나 거래 불가 상태가 되었다는 뜻이다.
     *
     * @param threshold {@link #now()} 로 받은 DB 시각이어야 한다. JVM 시각을 넣으면 안 된다
     * @return 내려간 행 수. 배치 로그에 남긴다
     */
    int deactivateNotSyncedSince(@Param("threshold") java.time.LocalDateTime threshold);

    /** 활성 종목의 티커만. 일봉 수집 배치가 대상 목록으로 쓴다. */
    List<String> findActiveSymbols();

    /**
     * 한글 종목명을 채운다.
     *
     * <p>없는 티커면 아무 일도 하지 않는다 — Wikidata 에는 우리가 안 다루는 종목도 들어 있다.
     *
     * @return 바뀐 행 수. 0 이면 그 티커가 우리 목록에 없다는 뜻이다
     */
    int updateNameKo(@Param("symbol") String symbol, @Param("nameKo") String nameKo);

    /** 한글명이 채워진 종목 수. 배치 로그와 관리자 화면이 쓴다. */
    int countWithNameKo();

    /**
     * 종목 종류를 채운다. 벤더 원문(security_type)과 거기서 접은 자산군을 함께 넣는다.
     *
     * <p>값이 그대로면 건드리지 않는다 — updated_at 이 매번 밀려 "언제 바뀌었나" 를 잃는다.
     *
     * @return 바뀐 행 수. 0 이면 값이 같거나 우리 목록에 없는 티커다
     */
    int updateSecurityType(@Param("symbol") String symbol,
                           @Param("securityType") String securityType,
                           @Param("isin") String isin,
                           @Param("assetClass") String assetClass);
}
