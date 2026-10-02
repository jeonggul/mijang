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

    /** 기간 내 서비스 활동 건수를 집계한다. */
    AdminStatsCounts countActivities(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /** 기간 내 신규 가입자 수를 센다. */
    int countNewUsers(@Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    /** 기간 내 거래 건수를 센다. */
    int countTransactions(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    /** 인기 종목을 조회한다. */
    List<AdminPopularStockResponse> findPopularStocks(@Param("limit") int limit);
}
