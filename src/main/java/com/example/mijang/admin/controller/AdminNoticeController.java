package com.example.mijang.admin.controller;

import com.example.mijang.common.dto.NoticeForm;
import com.example.mijang.common.dto.NoticeResponse;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.service.SupportService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final SupportService supportService;

    @GetMapping
    public ApiResponse<List<NoticeResponse>> notices() {
        return ApiResponse.ok(supportService.notices());
    }

    @PostMapping
    public ApiResponse<Long> create(@LoginUser SessionUser me, @Valid @RequestBody NoticeForm form) {
        return ApiResponse.ok(supportService.createNotice(me.userId(), form));
    }
}
