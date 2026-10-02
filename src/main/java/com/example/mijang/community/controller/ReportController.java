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

/** 게시글·댓글 신고 API를 제공한다. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 게시글·댓글을 신고한다. 같은 대상 중복 신고는 409. */
    @PostMapping
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @Valid @RequestBody ReportForm form) {
        return ApiResponse.ok(reportService.create(me.userId(), form));
    }
}
