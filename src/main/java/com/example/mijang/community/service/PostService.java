package com.example.mijang.community.service;

import com.example.mijang.community.dto.PostForm;
import com.example.mijang.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시글. 개발명세서(API) COM-001~003 · 화면 SR-009 — 확장(부록 C)
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;

    public Long create(Long userId, String symbol, PostForm form) {
        throw new UnsupportedOperationException("TODO COM-002: 작성 시점 주가와 함께 저장");
    }
}
