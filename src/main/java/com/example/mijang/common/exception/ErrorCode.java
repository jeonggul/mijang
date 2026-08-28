package com.example.mijang.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드와 HTTP 상태 매핑.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.exception
 * <p>코드 문자열은 [[미장-API명세서]] 1.6 의 {@code UPPER_SNAKE_CASE} 표기를 따른다.
 * 명세서에 이미 있는 항목은 문자열을 글자 그대로 옮겼고, 명세서에 없지만 구현상 필요한 것은
 * 같은 접두사 규칙({@code AUTH_} · {@code STOCK_} · {@code TX_} · {@code FX_} · {@code COMMUNITY_}
 * · {@code VENDOR_} · {@code COMMON_})으로 덧붙였다.
 *
 * <p>400 과 422 를 구분한다. 형식이 틀린 것이 400, 형식은 맞지만 업무 규칙에 어긋나는 것이 422다.
 * 화면 처리가 다르기 때문이다 — 400 은 필드 하이라이트, 422 는 안내 모달.
 *
 * <p>명세서 1.6 의 {@code FX_NOT_AVAILABLE} 은 여기에 없다. 환율 미수집은 오류가 아니라
 * 대체 환율로 응답하고 {@code substituted} 플래그를 세우는 정상 응답이기 때문이다.
 */
public enum ErrorCode {

    // ===== 인증·회원 (AUTH-001~003, USER-001) =====
    /** 명세서 1.6. 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다 — 계정 존재 여부가 새 나간다. */
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다"),
    /** 명세서 1.6. */
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED,
            "로그인이 만료되었습니다. 다시 로그인해 주세요"),
    AUTH_REQUIRED("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다"),
    AUTH_EMAIL_DUPLICATED("AUTH_EMAIL_DUPLICATED", HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다"),
    AUTH_NICKNAME_DUPLICATED("AUTH_NICKNAME_DUPLICATED", HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다"),
    /** 사칭·비속어 등 쓸 수 없는 단어. 형식 오류(400)와 구분해 422 로 둔다. */
    AUTH_NICKNAME_FORBIDDEN("AUTH_NICKNAME_FORBIDDEN", HttpStatus.UNPROCESSABLE_CONTENT,
            "사용할 수 없는 단어가 포함되어 있습니다"),
    /** 로그인된 사용자가 현재 비밀번호를 틀린 경우. 이미 인증된 상태라 401 이 아니라 400 이다. */
    AUTH_PASSWORD_MISMATCH("AUTH_PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST,
            "현재 비밀번호가 올바르지 않습니다"),
    /**
     * 형식은 맞지만 닉네임이나 이메일 아이디가 들어 있다.
     *
     * <p>형식 오류가 아니라 <b>추측하기 쉬운 값</b>이라 막는 것이라 422다.
     */
    AUTH_PASSWORD_TOO_GUESSABLE("AUTH_PASSWORD_TOO_GUESSABLE", HttpStatus.UNPROCESSABLE_CONTENT,
            "닉네임이나 이메일 아이디가 들어간 비밀번호는 사용할 수 없습니다"),
    /** 형식은 맞지만 이전과 같은 값이다. 형식 오류가 아니므로 422다. */
    AUTH_PASSWORD_UNCHANGED("AUTH_PASSWORD_UNCHANGED", HttpStatus.UNPROCESSABLE_CONTENT,
            "이전과 다른 비밀번호를 입력해주세요"),
    /** 만료·위조·이미 사용됨을 구분하지 않는다. 화면이 할 일은 어느 쪽이든 같다. */
    AUTH_RESET_TOKEN_INVALID("AUTH_RESET_TOKEN_INVALID", HttpStatus.BAD_REQUEST,
            "재설정 링크가 만료되었거나 이미 사용되었습니다"),
    /**
     * 소셜 전용 계정. 바꾸거나 확인할 비밀번호가 없다.
     *
     * <p>전에는 USER_NOT_FOUND 를 던져 로그인해 있는 사람에게 "존재하지 않는 사용자"가 나갔다.
     * 형식이 아니라 계정 상태의 문제라 422다.
     */
    AUTH_PASSWORD_NOT_SET("AUTH_PASSWORD_NOT_SET", HttpStatus.UNPROCESSABLE_CONTENT,
            "비밀번호가 설정되지 않은 계정입니다. 소셜 로그인으로 이용해주세요"),
    /** 재설정 링크 요청이 너무 잦다. 가입 여부와 무관하게 같은 기준으로 센다. */
    AUTH_TOO_MANY_REQUESTS("AUTH_TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS,
            "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요"),
    USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
            "존재하지 않는 사용자입니다"),

    // ===== 관리자 사용자 관리 (ADMIN-03) =====
    ADMIN_SELF_STATUS_CHANGE("ADMIN_SELF_STATUS_CHANGE", HttpStatus.UNPROCESSABLE_CONTENT,
            "본인 계정 상태는 변경할 수 없습니다"),
    ADMIN_LAST_ACTIVE("ADMIN_LAST_ACTIVE", HttpStatus.UNPROCESSABLE_CONTENT,
            "마지막 활성 관리자는 정지할 수 없습니다"),
    ADMIN_WITHDRAWN_USER("ADMIN_WITHDRAWN_USER", HttpStatus.UNPROCESSABLE_CONTENT,
            "탈퇴 계정의 상태는 변경할 수 없습니다"),
    ADMIN_USER_STATUS_CONFLICT("ADMIN_USER_STATUS_CONFLICT", HttpStatus.CONFLICT,
            "사용자 상태가 이미 변경되었습니다. 목록을 새로고침해 주세요"),

    // ===== 종목 (STOCK-001~003) =====
    STOCK_NOT_FOUND("STOCK_NOT_FOUND", HttpStatus.NOT_FOUND,
            "존재하지 않는 종목입니다"),
    STOCK_CIK_NOT_FOUND("STOCK_CIK_NOT_FOUND", HttpStatus.NOT_FOUND,
            "SEC에 등록되지 않은 종목입니다"),
    STOCK_DISCLOSURE_NOT_FOUND("STOCK_DISCLOSURE_NOT_FOUND", HttpStatus.NOT_FOUND,
            "공시 정보를 찾을 수 없습니다"),

    // ===== 매매 기록·보유 (PORT-001~005) =====
    /** 명세서 1.6. 형식은 맞고 규칙에 어긋나므로 422다. */
    TX_QUANTITY_EXCEEDS_HOLDING("TX_QUANTITY_EXCEEDS_HOLDING", HttpStatus.UNPROCESSABLE_CONTENT,
            "보유 수량보다 많이 매도할 수 없습니다"),
    /** 명세서 1.6. 날짜 형식 자체가 틀린 것이 아니라 값이 미래이므로 400이다. */
    TX_TRADE_DATE_FUTURE("TX_TRADE_DATE_FUTURE", HttpStatus.BAD_REQUEST,
            "거래일은 미래일 수 없습니다"),
    /** 명세서 1.6. */
    TX_STOCK_INACTIVE("TX_STOCK_INACTIVE", HttpStatus.UNPROCESSABLE_CONTENT,
            "상장 폐지되었거나 거래할 수 없는 종목입니다"),
    TX_NOT_FOUND("TX_NOT_FOUND", HttpStatus.NOT_FOUND,
            "존재하지 않는 매매 기록입니다"),
    TX_FORBIDDEN("TX_FORBIDDEN", HttpStatus.FORBIDDEN,
            "본인의 매매 기록만 다룰 수 있습니다"),

    // ===== 환율 (FX-001) =====
    FX_RATE_NOT_FOUND("FX_RATE_NOT_FOUND", HttpStatus.NOT_FOUND,
            "해당 일자의 고시 환율이 없습니다"),

    // ===== 배당 =====
    /** 명세서 1.6. */
    DIVIDEND_ALREADY_CONFIRMED("DIVIDEND_ALREADY_CONFIRMED", HttpStatus.CONFLICT,
            "이미 확정된 배당입니다"),
    DIVIDEND_NOT_FOUND("DIVIDEND_NOT_FOUND", HttpStatus.NOT_FOUND,
            "존재하지 않는 배당 기록입니다"),
    /** uk(portfolio_id, symbol, pay_date). 같은 지급일 배당은 한 건으로 합쳐 적는다. */
    DIVIDEND_DUPLICATED("DIVIDEND_DUPLICATED", HttpStatus.CONFLICT,
            "같은 종목·지급일의 배당이 이미 있습니다"),

    // ===== 커뮤니티 (COM-001~005) =====
    COMMUNITY_POST_NOT_FOUND("COMMUNITY_POST_NOT_FOUND", HttpStatus.NOT_FOUND,
            "삭제되었거나 존재하지 않는 글입니다"),
    COMMUNITY_REPORT_DUPLICATED("COMMUNITY_REPORT_DUPLICATED", HttpStatus.CONFLICT,
            "이미 신고한 대상입니다"),
    /** 두 관리자가 같은 신고를 동시에 처리한 경우. 진 쪽이 받는다. */
    COMMUNITY_REPORT_ALREADY_HANDLED("COMMUNITY_REPORT_ALREADY_HANDLED", HttpStatus.CONFLICT,
            "이미 처리된 신고입니다"),
    COMMUNITY_FORBIDDEN("COMMUNITY_FORBIDDEN", HttpStatus.FORBIDDEN,
            "본인이 작성한 글만 다룰 수 있습니다"),
    /** 운영 설정의 글쓰기 제한. 며칠 남았는지는 field 가 아니라 메시지로 알린다. */
    COMMUNITY_WRITE_TOO_EARLY("COMMUNITY_WRITE_TOO_EARLY", HttpStatus.FORBIDDEN,
            "가입 직후에는 글을 쓸 수 없습니다"),
    COMMUNITY_BADWORD("COMMUNITY_BADWORD", HttpStatus.BAD_REQUEST,
            "허용되지 않는 표현이 포함되어 있습니다"),

    // ===== 운영 설정 (SR-013) =====
    /** 관리자가 신규 가입을 닫아 둔 상태. 자격 문제가 아니라 서비스 상태다. */
    SIGNUP_DISABLED("SIGNUP_DISABLED", HttpStatus.FORBIDDEN,
            "현재 신규 가입을 받지 않습니다"),
    /** 점검 모드. 503 이라 검색 엔진과 클라이언트가 나중에 다시 온다. */
    MAINTENANCE_MODE("MAINTENANCE_MODE", HttpStatus.SERVICE_UNAVAILABLE,
            "서비스 점검 중입니다. 잠시 후 다시 시도해 주세요"),

    // ===== 외부 벤더 =====
    // 벤더 장애와 우리 쪽 설정 누락을 구분한다. 전자는 재시도, 후자는 사람이 고쳐야 한다.
    /** 명세서 1.6. */
    VENDOR_RATE_LIMIT("VENDOR_RATE_LIMIT", HttpStatus.TOO_MANY_REQUESTS,
            "데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요"),
    /** 명세서 1.6. */
    VENDOR_UNAVAILABLE("VENDOR_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
            "외부 데이터 조회에 실패했습니다"),
    VENDOR_NOT_CONFIGURED("VENDOR_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
            "외부 API 설정이 완료되지 않았습니다"),

    // ===== 공통 =====
    COMMON_INVALID_REQUEST("COMMON_INVALID_REQUEST", HttpStatus.BAD_REQUEST,
            "요청 값이 올바르지 않습니다"),
    COMMON_FORBIDDEN("COMMON_FORBIDDEN", HttpStatus.FORBIDDEN,
            "권한이 없습니다"),
    COMMON_NOT_FOUND("COMMON_NOT_FOUND", HttpStatus.NOT_FOUND,
            "요청한 경로를 찾을 수 없습니다"),
    COMMON_METHOD_NOT_ALLOWED("COMMON_METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED,
            "허용되지 않은 요청 방식입니다"),
    COMMON_INTERNAL_ERROR("COMMON_INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
            "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요");

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
