package com.example.mijang.community.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.community.dto.CommentForm;
import com.example.mijang.community.service.CommentService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 API. 개발명세서(API) COM-004 — 확장(부록 C)
 *
 * <p>누가 썼는지는 요청에서 받지 않고 토큰에서 꺼낸다 — 받으면 남의 이름으로 댓글을 달 수 있다.
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** COM-004 댓글 작성 */
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @PathVariable Long postId,
                                    @Valid @RequestBody CommentForm form) {
        return ApiResponse.ok(commentService.create(me.userId(), postId, form));
    }
}
