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

/** 헤더 알림 센터 조회·읽음 처리와 알림 설정 저장을 담당한다. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final NotificationSettingsResponse DEFAULT_SETTINGS =
            new NotificationSettingsResponse(true, true, new BigDecimal("0.05"), true, false);

    private final NotificationMapper notificationMapper;

    /** 최근 알림 20건을 돌려준다. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> recent(Long userId) {
        return notificationMapper.findRecent(userId, 20);
    }

    /** 사용자의 모든 알림을 읽음 처리한다. */
    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }

    /** 알림 설정을 돌려준다. 저장된 것이 없으면 기본값을 쓴다. */
    @Transactional(readOnly = true)
    public NotificationSettingsResponse settings(Long userId) {
        NotificationSettingsResponse settings = notificationMapper.findSettings(userId);
        return settings == null ? DEFAULT_SETTINGS : settings;
    }

    /** 알림 설정을 저장하고 저장된 값을 돌려준다. */
    @Transactional
    public NotificationSettingsResponse updateSettings(Long userId, NotificationSettingsForm form) {
        notificationMapper.upsertSettings(userId, form);
        return notificationMapper.findSettings(userId);
    }
}
