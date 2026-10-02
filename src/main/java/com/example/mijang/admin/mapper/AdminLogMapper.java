/*
 * AdminLogMapper — 운영 로그 테이블 접근
 *
 * 이 파일이 하는 일
 *   admin_logs 를 읽고 쓰는 통로다. 관리자가 한 일을 남기고, 최근 것부터 꺼낸다.
 */
package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.AdminLogResponse;
import java.time.LocalDateTime;
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

    /**
     * 최근 기록. 관리자 화면이 그대로 쓴다.
     *
     * <p>세 조건은 전부 선택이고 <b>null 이면 걸지 않는다.</b> 화면의 검색·종류·기간이
     * 각각 대응한다 — 안 걸린 조건까지 SQL 에 넣으면 인덱스를 못 탄다.
     *
     * @param q          관리자 닉네임 또는 대상 라벨의 일부. null·빈 문자열이면 전체
     * @param targetTypes 종류 필터. 화면의 "콘텐츠" 처럼 한 버튼이 여러 값을 뜻할 수 있어 목록으로 받는다
     * @param since      이 시각 이후만. null 이면 전 기간
     */
    List<AdminLogResponse> findRecent(@Param("limit") int limit,
                                      @Param("q") String q,
                                      @Param("targetTypes") List<String> targetTypes,
                                      @Param("since") LocalDateTime since);
}
