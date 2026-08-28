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

    /**
     * vendor_id 로 바로 잇는다. 기사 id 를 따로 조회하지 않는다.
     *
     * <p>수집은 종목마다 기사 수십 건을 도는데, 건마다 id 를 물으면 왕복이 그만큼 늘어난다.
     */
    int linkByVendorId(@Param("vendorId") String vendorId, @Param("symbol") String symbol);
}
