/*
 * CommentMapper — comments(댓글·대댓글) 테이블 접근
 *
 * 이 파일이 하는 일
 *   한 글의 댓글을 통째로 꺼내고, 새 댓글을 넣는다.
 *   깊이가 1단계뿐이라 트리로 꺼내지 않는다 — 원댓글 뒤에 그 대댓글이 붙도록
 *   정렬만 맞춰 주면 화면이 한 번 훑으면서 그린다.
 */
package com.example.mijang.community.mapper;

import com.example.mijang.community.dto.CommentResponse;
import com.example.mijang.community.dto.MyCommentResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * comments(댓글·대댓글) 접근.
 *
 * <p>개발명세서(MVC) · 커뮤니티 · mapper — 확장(부록 C)
 */
@Mapper
public interface CommentMapper {

    /** 저장. {@code parentId} 가 있으면 대댓글이다. */
    int insert(@Param("postId") Long postId,
               @Param("userId") Long userId,
               @Param("parentId") Long parentId,
               @Param("content") String content);

    /** 방금 저장한 댓글의 id. insert 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /**
     * 한 글의 댓글 전체. {@code COM-003}
     *
     * <p>원댓글 아래에 그 대댓글이 오도록 정렬한다. 화면이 다시 묶지 않아도 된다.
     */
    List<CommentResponse> findByPost(@Param("postId") Long postId);

    /**
     * 답글을 달아도 되는 원댓글이면 그 댓글이 달린 글의 id, 아니면 null.
     *
     * <p>한 번에 세 가지를 본다 — 그 댓글이 있는가, 살아 있는가, <b>원댓글인가</b>.
     * 서비스는 돌려받은 값이 지금 글의 id 와 같은지만 보면 된다. 나눠서 물으면
     * 대댓글에 답글이 달리는 경우나 남의 글 댓글을 부모로 지목하는 경우가 새어 나간다.
     */
    Long findRepliableParentPostId(@Param("commentId") Long commentId);

    /** 게시글 댓글 수. COM-003 */
    long countByPost(@Param("postId") Long postId);

    /**
     * 내가 쓴 댓글. 어느 글에 달았는지까지 함께 온다.
     *
     * <p>숨김·삭제된 것도 함께 돌려준다 — 목록에서 사라진 이유를 쓴 사람은 알아야 한다.
     */
    List<MyCommentResponse> findByUser(@Param("userId") Long userId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    long countByUser(@Param("userId") Long userId);
}
