package com.example.mijang.community.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.community.dto.CommentForm;
import com.example.mijang.community.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 API. 개발명세서(API) COM-004 — 확장(부록 C)
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** COM-004 댓글 작성 */
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<Long> create(@PathVariable Long postId, @Valid @RequestBody CommentForm form) {
        return ApiResponse.ok(commentService.create(null, postId, form));
    }
}
