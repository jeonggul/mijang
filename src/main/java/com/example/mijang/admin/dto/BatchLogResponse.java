/*
 * BatchLogResponse — 배치 실행 기록 한 건
 *
 * 이 파일이 하는 일
 *   관리자 화면의 배치 상태 표에 한 줄로 뜨는 값이다.
 *   무엇이 언제 시작해 얼마나 걸렸고 몇 건을 처리했는지, 그리고 어떻게 끝났는지를 담는다.
 */
package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/**
 * 배치 실행 기록 한 건. 개발명세서(API) ADMIN-02
 */
public record BatchLogResponse(
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer durationMs,
        Integer processedCount,
        String status,
        String message) {
}
