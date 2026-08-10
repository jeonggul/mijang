package com.example.mijang.community.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.community.dto.PostForm;
import com.example.mijang.community.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 API. 개발명세서(API) COM-001~003 · 화면 SR-009 — 확장(부록 C)
 */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** COM-001 종목별 게시글 목록 */
    @GetMapping("/api/stocks/{symbol}/posts")
    public ApiResponse<Void> list(@PathVariable String symbol) {
        throw new UnsupportedOperationException("TODO COM-001: 종목별 게시글 목록");
    }

    /** COM-002 게시글 작성 */
    @PostMapping("/api/stocks/{symbol}/posts")
    public ApiResponse<Long> create(@PathVariable String symbol, @Valid @RequestBody PostForm form) {
        return ApiResponse.ok(postService.create(null, symbol, form));
    }

    /** COM-003 게시글 상세·댓글 */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<Void> detail(@PathVariable Long postId) {
        throw new UnsupportedOperationException("TODO COM-003: 게시글 상세와 댓글");
    }
}
