package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.user.dto.NotificationResponse;
import com.example.mijang.user.dto.NotificationSettingsForm;
import com.example.mijang.user.dto.NotificationSettingsResponse;
import com.example.mijang.user.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 알림 목록 조회·읽음 처리·알림 설정 API를 제공한다. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 내 최근 알림 목록을 돌려준다. */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> recent(@LoginUser SessionUser me) {
        return ApiResponse.ok(notificationService.recent(me.userId()));
    }

    /** 내 알림을 모두 읽음으로 표시한다. */
    @PatchMapping("/read")
    public ApiResponse<Void> markAllRead(@LoginUser SessionUser me) {
        notificationService.markAllRead(me.userId());
        return ApiResponse.ok(null);
    }

    /** 내 알림 설정을 돌려준다. */
    @GetMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> settings(@LoginUser SessionUser me) {
        return ApiResponse.ok(notificationService.settings(me.userId()));
    }

    /** 내 알림 설정을 갱신하고 갱신된 설정을 돌려준다. */
    @PutMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> updateSettings(
            @LoginUser SessionUser me,
            @Valid @RequestBody NotificationSettingsForm form) {
        return ApiResponse.ok(notificationService.updateSettings(me.userId(), form));
    }
}
