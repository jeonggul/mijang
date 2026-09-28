package com.example.mijang.news.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** news(뉴스) 테이블에 접근한다. 본문은 전재하지 않고 중복은 vendor_id 로 막는다. */
@Mapper
public interface NewsMapper {

    /** 이 종목에 연결된 기사 수를 센다. */
    long countBySymbol(@Param("symbol") String symbol);

    /** 기사를 넣는다. 이미 있는 vendor_id 면 아무 일도 하지 않고 0 을 반환한다. */
    int insertIgnore(@Param("vendorId") String vendorId,
                     @Param("headline") String headline,
                     @Param("summary") String summary,
                     @Param("url") String url,
                     @Param("source") String source,
                     @Param("publishedAt") LocalDateTime publishedAt);

    /** 수집 대상 종목(누군가 보유하거나 관심에 담은 것)을 조회한다. */
    List<String> findSymbolsOfInterest();
}
