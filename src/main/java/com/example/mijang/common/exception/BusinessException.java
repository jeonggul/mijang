package com.example.mijang.common.exception;

/**
 * 업무 규칙 위반. {@link ErrorCode} 를 그대로 들고 다닌다.
 *
 * <p>명세서에는 없지만 ErrorCode 를 실어 나를 통로가 필요해 함께 둔다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
