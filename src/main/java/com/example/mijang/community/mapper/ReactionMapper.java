package com.example.mijang.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** post_reactions(좋아요·스크랩) 테이블에 접근한다. 토글은 "지워 보고 없으면 넣는다"로 푼다. */
@Mapper
public interface ReactionMapper {

    /** 반응을 지우고 지운 행 수를 반환한다. 0 이면 반응이 없었다는 뜻이다. */
    int delete(@Param("postId") Long postId,
               @Param("userId") Long userId,
               @Param("type") String type);

    /** 반응을 넣는다. PK(post·user·type)가 겹치면 DuplicateKeyException. */
    int insert(@Param("postId") Long postId,
               @Param("userId") Long userId,
               @Param("type") String type);

    /** posts.like_count 를 실제 반응 수로 재집계한다. */
    int syncLikeCount(@Param("postId") Long postId);

    /** 이 사용자가 이 글에 남긴 반응 종류들을 조회한다. */
    java.util.List<String> findTypes(@Param("postId") Long postId,
                                     @Param("userId") Long userId);
}
