package com.example.mijang.admin.domain;

/** 배치 실행 결과. 스키마의 ENUM 과 값이 같아야 한다. */
public enum BatchStatus {
    /** 정상 완료. */
    SUCCESS,
    /** 일부만 처리. 묶음 중 몇 개가 실패한 경우다. */
    PARTIAL,
    /** 실패. */
    FAILED,
    /** 돌 필요가 없어 건너뜀. 휴장일·주말 등. */
    SKIPPED
}
