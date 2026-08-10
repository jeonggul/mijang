package com.example.mijang.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * comments(댓글·대댓글) 접근.
 *
 * <p>개발명세서(MVC) · 커뮤니티 · mapper — 확장(부록 C)
 */
@Mapper
public interface CommentMapper {

    /** 게시글 댓글 수. COM-003 */
    long countByPost(@Param("postId") Long postId);
}
