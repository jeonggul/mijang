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

    /**
     * 신고를 넣는다. PENDING 으로 시작한다.
     *
     * <p>uk_reports_reporter_target 이 겹치면 DuplicateKeyException — 위 카운트 확인과
     * 저장 사이에 같은 신고가 먼저 들어온 경우다. 서비스가 409 로 바꾼다.
     */
    int insert(@Param("userId") Long userId,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId,
               @Param("reason") String reason,
               @Param("detail") String detail);

    /** 방금 넣은 신고의 id. 같은 커넥션 안에서만 유효하다 — @Transactional 이 그걸 보장한다. */
    Long findLastInsertedId();
}
