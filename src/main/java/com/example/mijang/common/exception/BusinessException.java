package com.example.mijang.common.exception;

/** 업무 규칙 위반을 {@link ErrorCode}와 함께 실어 나른다. field 는 짚어 줄 입력이 있을 때만 채운다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.field = field;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String field() {
        return field;
    }
}
