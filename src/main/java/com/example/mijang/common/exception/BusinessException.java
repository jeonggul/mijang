package com.example.mijang.common.exception;

/**
 * 업무 규칙 위반. {@link ErrorCode} 를 그대로 들고 다닌다.
 *
 * <p>명세서에는 없지만 ErrorCode 를 실어 나를 통로가 필요해 함께 둔다.
 * <p>{@code field} 는 [[미장-API명세서]] 1.1 의 {@code error.field} 로 나간다.
 * 어떤 입력 때문에 막혔는지 화면이 짚어 줄 수 있을 때만 채우고, 그 외에는 null 로 둔다.
 */
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
