/*
 * AdminLogResponse — 운영 로그 한 건
 *
 * 이 파일이 하는 일
 *   관리자가 무엇을 했는지 한 줄로 남긴 기록이다.
 *   대상 이름을 그때의 값으로 함께 적어 둔다 — 나중에 원본이 사라져도
 *   "무엇에 대해 한 일인지"를 알 수 있어야 하기 때문이다.
 */
package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/**
 * 운영 로그 한 건. 개발명세서(API) ADMIN-07
 *
 * <p>{@code targetLabel} 은 작업 당시의 표시용 스냅샷이다. 원본이 사라져도 남는다(2.2).
 */
public record AdminLogResponse(
        Long id,
        String adminNickname,
        String action,
        String targetType,
        String targetId,
        String targetLabel,
        String detail,
        String result,
        LocalDateTime createdAt) {
}
