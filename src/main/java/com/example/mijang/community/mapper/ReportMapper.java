package com.example.mijang.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** reports(신고) 테이블에 접근한다. */
@Mapper
public interface ReportMapper {

    /** 같은 사람이 같은 대상을 이미 신고했는지 센다. */
    int countByReporterAndTarget(@Param("userId") Long userId,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") Long targetId);

    /** 신고를 PENDING 상태로 넣는다. 유니크 키가 겹치면 DuplicateKeyException 이 난다. */
    int insert(@Param("userId") Long userId,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId,
               @Param("reason") String reason,
               @Param("detail") String detail);

    /** 방금 넣은 신고의 id 를 반환한다. 같은 커넥션 안에서만 유효하다. */
    Long findLastInsertedId();

    /** 이 대상에 걸린 미처리(PENDING) 신고 수를 센다. 자동 숨김 판정에 쓴다. */
    int countPendingByTarget(@Param("targetType") String targetType,
                             @Param("targetId") Long targetId);
}
