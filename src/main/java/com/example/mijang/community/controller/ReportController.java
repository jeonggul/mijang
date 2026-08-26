package com.example.mijang.community.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.community.dto.ReportForm;
import com.example.mijang.community.service.ReportService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 API. 개발명세서(API) COM-005 · 화면 SR-009 — 확장(부록 C)
 *
 * <p>MVC 시트에는 신고 컨트롤러 행이 없어 API 시트를 근거로 추가했다.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** COM-005 게시글·댓글 신고. 같은 대상을 두 번 신고하면 409. */
    @PostMapping
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @Valid @RequestBody ReportForm form) {
        /* 전에는 null 을 넘겼다 — 신고자가 전부 null 이면 중복 신고 판정이 무력해진다 */
        return ApiResponse.ok(reportService.create(me.userId(), form));
    }
}
