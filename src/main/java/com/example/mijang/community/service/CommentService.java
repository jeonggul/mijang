/*
 * CommentService — 댓글
 *
 * 이 파일이 하는 일
 *   댓글과 대댓글을 저장한다. 깊이는 1단계까지다 —
 *   대댓글에 다시 답글이 달리면 화면이 무한히 들여쓰기해야 하고, 읽는 사람도
 *   어디에 달린 말인지 못 따라간다.
 *   저장하면서 글의 댓글 수를 같이 올린다. 목록에서 매번 세면 글 수만큼 COUNT 가 나간다.
 */
package com.example.mijang.community.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.community.dto.CommentForm;
import com.example.mijang.community.mapper.CommentMapper;
import com.example.mijang.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글. 개발명세서(API) COM-004 — 확장(부록 C)
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;

    /**
     * 댓글 저장.
     *
     * <p>부모를 지목했으면 그것이 <b>이 글의 원댓글</b>인지 본다. 한 번의 조회로 셋을 다 본다 —
     * 그 댓글이 있는가, 원댓글인가(대댓글이면 답글을 못 단다), 이 글의 것인가.
     * 마지막 조건이 없으면 남의 글 댓글을 부모로 지목해 엉뚱한 자리에 댓글을 심을 수 있다.
     *
     * @throws BusinessException 글이 없을 때(404), 부모 댓글이 답글을 달 수 없는 것일 때(400)
     */
    @Transactional
    public Long create(Long userId, Long postId, CommentForm form) {
        if (postMapper.findById(postId) == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        Long parentId = form.getParentId();
        if (parentId != null && !postId.equals(commentMapper.findRepliableParentPostId(parentId))) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "parentId");
        }

        commentMapper.insert(postId, userId, parentId, form.getContent());
        postMapper.increaseCommentCount(postId);
        return commentMapper.findLastInsertedId();
    }
}
