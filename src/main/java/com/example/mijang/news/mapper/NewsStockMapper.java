package com.example.mijang.news.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * news_stocks(뉴스-종목 매핑) 접근.
 *
 * <p>개발명세서(MVC) · 뉴스 · mapper — 확장(부록 C)
 */
@Mapper
public interface NewsStockMapper {

    int link(@Param("newsId") Long newsId, @Param("symbol") String symbol);
}
