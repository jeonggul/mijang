/*
 * AdminLogMapper — 운영 로그 테이블 접근
 *
 * 이 파일이 하는 일
 *   admin_logs 를 읽고 쓰는 통로다. 관리자가 한 일을 남기고, 최근 것부터 꺼낸다.
 */
package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.AdminLogResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * admin_logs 접근. 관리자가 한 일을 남긴다(2.2).
 */
@Mapper
public interface AdminLogMapper {

    /**
     * 기록. 실패해도 본 작업을 되돌리지 않는다(2.3) — 부르는 쪽이 예외를 삼킨다.
     */
    int insert(@Param("adminId") Long adminId,
               @Param("action") String action,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("targetLabel") String targetLabel,
               @Param("detail") String detail,
               @Param("result") String result);

    /** 최근 기록. 관리자 화면이 그대로 쓴다. */
    List<AdminLogResponse> findRecent(@Param("limit") int limit);
}
