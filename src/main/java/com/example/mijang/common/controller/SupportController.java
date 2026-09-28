package com.example.mijang.common.controller;

import com.example.mijang.common.dto.FaqResponse;
import com.example.mijang.common.dto.NoticeResponse;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.service.SupportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공지·FAQ 조회 API를 제공한다. */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    /** 공지 목록을 반환한다. */
    @GetMapping("/notices")
    public ApiResponse<List<NoticeResponse>> notices() {
        return ApiResponse.ok(supportService.notices());
    }

    /** 공지 상세를 반환한다. */
    @GetMapping("/notices/{id}")
    public ApiResponse<NoticeResponse> notice(@PathVariable Long id) {
        return ApiResponse.ok(supportService.notice(id));
    }

    /** FAQ 목록을 반환한다. */
    @GetMapping("/faqs")
    public ApiResponse<List<FaqResponse>> faqs() {
        return ApiResponse.ok(supportService.faqs());
    }
}
