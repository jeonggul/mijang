/*
 * ReactionMapper — post_reactions(좋아요·스크랩) 접근
 *
 * 이 파일이 하는 일
 *   반응 한 건을 넣고 빼고, 글의 좋아요 수를 실제 반응 수로 다시 센다.
 */
package com.example.mijang.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * post_reactions 접근. COM-005 이웃의 좋아요·스크랩(4.5 점검 4.2).
 *
 * <p>토글은 "지워 보고, 지워진 게 없으면 넣는다" 로 푼다. 상태를 먼저 읽고 갈리면
 * 읽기와 쓰기 사이에 다른 요청이 끼어들 수 있다 — 지우기·넣기는 각각 원자적이다.
 */
@Mapper
public interface ReactionMapper {

    /**
     * 반응을 지운다.
     *
     * @return 지운 행 수. 0 이면 반응이 없었다는 뜻이라 이어서 넣으면 토글이 된다
     */
    int delete(@Param("postId") Long postId,
               @Param("userId") Long userId,
               @Param("type") String type);

    /** 반응을 넣는다. PK(post·user·type)가 겹치면 DuplicateKeyException. */
    int insert(@Param("postId") Long postId,
               @Param("userId") Long userId,
               @Param("type") String type);

    /**
     * posts.like_count 를 실제 반응 수로 다시 센다.
     *
     * <p>±1 증감이 아니라 재집계다. 증감은 어긋난 뒤에 스스로 못 돌아오지만,
     * 재집계는 언제 틀렸든 다음 토글에서 맞는 값으로 돌아온다.
     */
    int syncLikeCount(@Param("postId") Long postId);

    /** 이 사용자가 이 글에 남긴 반응 종류들. 상세 화면의 버튼 상태에 쓴다. */
    java.util.List<String> findTypes(@Param("postId") Long postId,
                                     @Param("userId") Long userId);
}
