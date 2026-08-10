package com.example.mijang.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * reports(신고) 접근.
 *
 * <p>개발명세서(MVC) · 커뮤니티 · mapper — 확장(부록 C)
 */
@Mapper
public interface ReportMapper {

    /** 같은 사람이 같은 대상을 이미 신고했는지. COM-005 의 409 판정에 쓴다. */
    int countByReporterAndTarget(@Param("userId") Long userId,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") Long targetId);
}
