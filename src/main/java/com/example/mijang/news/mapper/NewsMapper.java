/*
 * NewsMapper — news(뉴스) 테이블 접근
 *
 * 이 파일이 하는 일
 *   수집한 기사를 넣고, 이미 있는지 확인한다.
 *   본문은 저장하지 않는다 — 제목·요약·원문 링크만이다(저작권).
 *
 *   중복은 vendor_id 로 막는다. 같은 기사가 매 수집마다 새 행으로 쌓이면
 *   목록이 같은 제목으로 도배되고, "새 기사" 판단도 못 하게 된다.
 */
package com.example.mijang.news.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * news(뉴스) 접근. 제목·요약·원문 링크만 저장하고 본문은 전재하지 않는다.
 *
 * <p>개발명세서(MVC) · 뉴스 · mapper — 확장(부록 C)
 */
@Mapper
public interface NewsMapper {

    /** 이 종목에 연결된 기사 수. */
    long countBySymbol(@Param("symbol") String symbol);

    /**
     * 기사를 넣는다. 이미 있는 vendor_id 면 아무 일도 하지 않는다.
     *
     * @return 실제로 들어갔으면 1, 이미 있었으면 0
     */
    int insertIgnore(@Param("vendorId") String vendorId,
                     @Param("headline") String headline,
                     @Param("summary") String summary,
                     @Param("url") String url,
                     @Param("source") String source,
                     @Param("publishedAt") LocalDateTime publishedAt);

    /** vendor_id 로 id 를 찾는다. 방금 넣었든 원래 있었든 연결에는 id 가 필요하다. */
    Long findIdByVendorId(@Param("vendorId") String vendorId);

    /**
     * 수집 대상 종목.
     *
     * <p>누군가 보유하거나 관심에 담아 둔 종목만 모은다. 전 종목(13,364개)을 돌면
     * 벤더 한도를 한 시간 만에 태우고, 아무도 안 보는 기사를 쌓는다.
     */
    List<String> findSymbolsOfInterest();
}
