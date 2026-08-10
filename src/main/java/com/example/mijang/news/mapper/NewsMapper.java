package com.example.mijang.news.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * news(뉴스) 접근. 제목·요약·원문 링크만 저장하고 본문은 전재하지 않는다.
 *
 * <p>개발명세서(MVC) · 뉴스 · mapper — 확장(부록 C)
 */
@Mapper
public interface NewsMapper {

    long countBySymbol(@Param("symbol") String symbol);
}
