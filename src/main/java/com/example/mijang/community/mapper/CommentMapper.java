package com.example.mijang.community.mapper;

import com.example.mijang.community.dto.CommentResponse;
import com.example.mijang.community.dto.MyCommentResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** comments(댓글·대댓글) 테이블에 접근한다. */
@Mapper
public interface CommentMapper {

    /** 댓글을 저장한다. parentId 가 있으면 대댓글이다. */
    int insert(@Param("postId") Long postId,
               @Param("userId") Long userId,
               @Param("parentId") Long parentId,
               @Param("content") String content);

    /** 방금 저장한 댓글의 id 를 반환한다. insert 직후에만 유효하다. */
    Long findLastInsertedId();

    /** 한 글의 댓글 전체를 원댓글-대댓글 순으로 조회한다. */
    List<CommentResponse> findByPost(@Param("postId") Long postId);

    /** 답글 가능한 원댓글이면 그 댓글이 달린 글의 id, 아니면 null 을 반환한다. */
    Long findRepliableParentPostId(@Param("commentId") Long commentId);

    /** 내가 쓴 댓글을 조회한다. 숨김·삭제된 것도 함께 돌려준다. */
    List<MyCommentResponse> findByUser(@Param("userId") Long userId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    /** 내가 쓴 댓글 수를 센다. */
    long countByUser(@Param("userId") Long userId);
}
