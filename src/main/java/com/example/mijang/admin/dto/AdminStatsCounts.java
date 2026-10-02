package com.example.mijang.admin.dto;

/** 선택한 기간에 발생한 실제 서비스 활동 건수. */
public record AdminStatsCounts(
        int newUserCount,
        int activeUserCount,
        int transactionCount,
        int judgmentCount,
        int postCount,
        int commentCount,
        int watchCount) {
}
