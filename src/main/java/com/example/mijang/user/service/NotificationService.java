package com.example.mijang.user.service;

import com.example.mijang.user.dto.NotificationResponse;
import com.example.mijang.user.dto.NotificationSettingsForm;
import com.example.mijang.user.dto.NotificationSettingsResponse;
import com.example.mijang.user.mapper.NotificationMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final NotificationSettingsResponse DEFAULT_SETTINGS =
            new NotificationSettingsResponse(true, true, new BigDecimal("0.05"), true, false);

    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public List<NotificationResponse> recent(Long userId) {
        return notificationMapper.findRecent(userId, 20);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse settings(Long userId) {
        NotificationSettingsResponse settings = notificationMapper.findSettings(userId);
        return settings == null ? DEFAULT_SETTINGS : settings;
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(Long userId, NotificationSettingsForm form) {
        notificationMapper.upsertSettings(userId, form);
        return notificationMapper.findSettings(userId);
    }
}
