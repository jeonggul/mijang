package com.example.mijang.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 알림 설정 전체를 한 번에 저장한다. */
public record NotificationSettingsForm(
        boolean targetPriceEnabled,
        boolean volatilityEnabled,
        @NotNull @DecimalMin("0.01") @DecimalMax("1.0") BigDecimal volatilityThreshold,
        boolean dividendEnabled,
        boolean newsEnabled) {
}
