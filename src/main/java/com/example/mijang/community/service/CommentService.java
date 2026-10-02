package com.example.mijang.community.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.community.dto.CommentForm;
import com.example.mijang.community.mapper.CommentMapper;
import com.example.mijang.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글·대댓글을 저장한다. 깊이는 1단계까지다. */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;

    /** 댓글을 저장한다. 부모는 이 글의 살아 있는 원댓글이어야 한다. */
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
