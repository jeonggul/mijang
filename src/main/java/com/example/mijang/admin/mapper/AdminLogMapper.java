package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.AdminLogResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** admin_logs 접근 — 관리자가 한 일을 남기고 최근 것부터 꺼낸다. */
@Mapper
public interface AdminLogMapper {

    /** 로그 한 건을 남긴다. 실패해도 본 작업을 되돌리지 않는다 — 부르는 쪽이 예외를 삼킨다. */
    int insert(@Param("adminId") Long adminId,
               @Param("action") String action,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("targetLabel") String targetLabel,
               @Param("detail") String detail,
               @Param("result") String result);

    /** 최근 로그를 조회한다. q·targetTypes·since 는 null 이면 조건을 걸지 않는다. */
    List<AdminLogResponse> findRecent(@Param("limit") int limit,
                                      @Param("q") String q,
                                      @Param("targetTypes") List<String> targetTypes,
                                      @Param("since") LocalDateTime since);
}
