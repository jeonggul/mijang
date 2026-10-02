package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.AdminPopularStockResponse;
import com.example.mijang.admin.dto.AdminStatsCounts;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 통계 집계 SQL의 통로. */
@Mapper
public interface AdminStatsMapper {

    AdminStatsCounts countActivities(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    int countNewUsers(@Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    int countTransactions(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    List<AdminPopularStockResponse> findPopularStocks(@Param("limit") int limit);
}
