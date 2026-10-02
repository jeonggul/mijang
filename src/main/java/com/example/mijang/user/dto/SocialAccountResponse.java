package com.example.mijang.user.dto;

import java.time.LocalDateTime;

/** 연동된 소셜 계정 한 건이다 — provider는 GOOGLE 또는 KAKAO이고 제공자 쪽 식별자는 담지 않는다. */
public record SocialAccountResponse(String provider, LocalDateTime linkedAt) {
}
