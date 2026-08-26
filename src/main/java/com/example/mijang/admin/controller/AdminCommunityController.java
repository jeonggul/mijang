/*
 * AdminCommunityController — 게시글·댓글·신고 관리 API
 *
 * 이 파일이 하는 일
 *   관리자 화면의 커뮤니티 세 탭이 부르는 것들을 내준다.
 *   권한은 SecurityConfig 의 /api/admin/** 규칙이 막는다(2.1).
 */
package com.example.mijang.admin.controller;

import com.example.mijang.admin.dto.AdminCommentResponse;
import com.example.mijang.admin.dto.AdminPostResponse;
import com.example.mijang.admin.dto.AdminReportResponse;
import com.example.mijang.admin.service.AdminCommunityService;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커뮤니티 운영 API. 화면 SR-013 — 4.5 점검 3.1 의 게시글·댓글·신고 탭.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCommunityController {

    private final AdminCommunityService service;

    /** 게시글 목록. 숨김·삭제 상태까지 본다. */
    @GetMapping("/posts")
    public ApiResponse<List<AdminPostResponse>> posts(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.posts(status, q, Math.min(limit, 200)));
    }

    /** 게시글 숨김·복원. */
    @PatchMapping("/posts/{postId}/status")
    public ApiResponse<Void> togglePost(@LoginUser SessionUser me,
                                        @PathVariable Long postId,
                                        @RequestBody @jakarta.validation.Valid HideRequest request) {
        service.togglePost(me.userId(), postId, request.hidden());
        return ApiResponse.ok(null);
    }

    @GetMapping("/comments")
    public ApiResponse<List<AdminCommentResponse>> comments(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.comments(status, q, Math.min(limit, 200)));
    }

    @PatchMapping("/comments/{commentId}/status")
    public ApiResponse<Void> toggleComment(@LoginUser SessionUser me,
                                           @PathVariable Long commentId,
                                           @RequestBody @jakarta.validation.Valid HideRequest request) {
        service.toggleComment(me.userId(), commentId, request.hidden());
        return ApiResponse.ok(null);
    }

    /** 신고 목록. 기본은 미처리만 — 처리한 것은 물어야 보인다. */
    @GetMapping("/reports")
    public ApiResponse<List<AdminReportResponse>> reports(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.reports(status, Math.min(limit, 200)));
    }

    /** 신고 처리. RESOLVE 는 대상을 숨기고 닫고, REJECT 는 그냥 닫는다. */
    @PatchMapping("/reports/{reportId}")
    public ApiResponse<Void> handleReport(@LoginUser SessionUser me,
                                          @PathVariable Long reportId,
                                          @RequestBody @jakarta.validation.Valid HandleRequest request) {
        service.handleReport(me.userId(), reportId,
                "RESOLVE".equalsIgnoreCase(request.action()));
        return ApiResponse.ok(null);
    }

    /** 숨김 요청 본문. true 면 숨기고 false 면 되살린다. */
    public record HideRequest(boolean hidden) {
    }

    /** 신고 처리 본문. 받아들이면 RESOLVE, 기각이면 REJECT. */
    public record HandleRequest(
            @NotBlank @Pattern(regexp = "(?i)RESOLVE|REJECT", message = "허용되지 않는 값입니다")
            String action) {
    }
}
