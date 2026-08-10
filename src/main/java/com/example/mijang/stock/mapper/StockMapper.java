package com.example.mijang.stock.mapper;

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

    /** 전방 일치 검색. STOCK-001 */
    List<StockSearchResponse> searchByPrefix(@Param("q") String q, @Param("limit") int limit);
}
