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

/** 댓글 API를 제공한다. 작성자는 요청이 아닌 토큰에서 꺼낸다. */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 댓글을 작성한다. */
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @PathVariable Long postId,
                                    @Valid @RequestBody CommentForm form) {
        return ApiResponse.ok(commentService.create(me.userId(), postId, form));
    }
}
