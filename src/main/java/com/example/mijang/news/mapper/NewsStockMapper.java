package com.example.mijang.news.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** news_stocks(뉴스-종목 매핑) 테이블에 접근한다. */
@Mapper
public interface NewsStockMapper {

    /** 기사 id 조회 없이 vendor_id 로 기사와 종목을 바로 잇는다. */
    int linkByVendorId(@Param("vendorId") String vendorId, @Param("symbol") String symbol);
}
