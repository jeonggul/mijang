package com.example.mijang.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 error code prefix 와 HTTP 상태 매핑.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.exception
 * <p>TODO: 명세서 API 시트의 Error / Rule 컬럼을 보고 항목을 채운다.
 */
public enum ErrorCode {

    // 인증/회원 — AUTH-001~003, USER-001
    EMAIL_DUPLICATED("AUTH-409-1", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    NICKNAME_DUPLICATED("AUTH-409-2", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
    LOGIN_FAILED("AUTH-401-1", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),

    // 종목 — STOCK-001~003
    STOCK_NOT_FOUND("STOCK-404-1", HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다"),

    // 포트폴리오 — PORT-001~005
    TRANSACTION_NOT_FOUND("PORT-404-1", HttpStatus.NOT_FOUND, "존재하지 않는 매매 기록입니다"),
    NOT_TRANSACTION_OWNER("PORT-403-1", HttpStatus.FORBIDDEN, "본인의 매매 기록만 다룰 수 있습니다"),

    // 환율 — FX-001
    FX_RATE_NOT_FOUND("FX-404-1", HttpStatus.NOT_FOUND, "해당 일자의 고시 환율이 없습니다"),

    // 커뮤니티 — COM-005
    REPORT_DUPLICATED("COM-409-1", HttpStatus.CONFLICT, "이미 신고한 대상입니다"),

    // 공통
    INVALID_REQUEST("COMMON-400-1", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
