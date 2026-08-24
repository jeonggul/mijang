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

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping("/notices")
    public ApiResponse<List<NoticeResponse>> notices() {
        return ApiResponse.ok(supportService.notices());
    }

    @GetMapping("/notices/{id}")
    public ApiResponse<NoticeResponse> notice(@PathVariable Long id) {
        return ApiResponse.ok(supportService.notice(id));
    }

    @GetMapping("/faqs")
    public ApiResponse<List<FaqResponse>> faqs() {
        return ApiResponse.ok(supportService.faqs());
    }
}
