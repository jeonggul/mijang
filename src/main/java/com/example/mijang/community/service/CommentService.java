package com.example.mijang.community.service;

import com.example.mijang.community.dto.CommentForm;
import com.example.mijang.community.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 댓글·대댓글. 개발명세서(API) COM-004 — 확장(부록 C)
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;

    public Long create(Long userId, Long postId, CommentForm form) {
        throw new UnsupportedOperationException("TODO COM-004: 댓글 저장");
    }
}
