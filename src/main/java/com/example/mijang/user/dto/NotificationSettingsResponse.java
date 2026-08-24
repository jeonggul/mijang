package com.example.mijang.user.dto;

import java.math.BigDecimal;

/** 설정 화면에서 바로 읽고 저장하는 알림 설정. */
public record NotificationSettingsResponse(
        boolean targetPriceEnabled,
        boolean volatilityEnabled,
        BigDecimal volatilityThreshold,
        boolean dividendEnabled,
        boolean newsEnabled) {
}
