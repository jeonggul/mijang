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

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> recent(@LoginUser SessionUser me) {
        return ApiResponse.ok(notificationService.recent(me.userId()));
    }

    @PatchMapping("/read")
    public ApiResponse<Void> markAllRead(@LoginUser SessionUser me) {
        notificationService.markAllRead(me.userId());
        return ApiResponse.ok(null);
    }

    @GetMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> settings(@LoginUser SessionUser me) {
        return ApiResponse.ok(notificationService.settings(me.userId()));
    }

    @PutMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> updateSettings(
            @LoginUser SessionUser me,
            @Valid @RequestBody NotificationSettingsForm form) {
        return ApiResponse.ok(notificationService.updateSettings(me.userId(), form));
    }
}
