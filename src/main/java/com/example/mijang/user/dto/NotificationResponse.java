package com.example.mijang.user.dto;

import java.time.LocalDateTime;

/** 헤더 알림 센터 한 건. */
public record NotificationResponse(
        Long id,
        String type,
        String symbol,
        String title,
        String body,
        String linkUrl,
        boolean read,
        LocalDateTime createdAt) {
}
