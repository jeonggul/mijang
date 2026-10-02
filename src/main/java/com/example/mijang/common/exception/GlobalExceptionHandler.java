package com.example.mijang.common.exception;

import com.example.mijang.common.response.ApiError;
import com.example.mijang.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 컨트롤러 전용 예외 처리.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.exception
 * <p>화면(Thymeleaf) 쪽 오류는 templates/error.html 이 담당하므로 건드리지 않도록
 * {@code annotations = RestController.class} 로 적용 범위를 좁혔다.
 * <p>응답은 전부 [[미장-API명세서]] 1.1 봉투를 쓴다.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.errorCode();
        return ResponseEntity.status(ec.status())
                .body(ApiResponse.fail(ApiError.of(ec.code(), ec.message(), e.field())));
    }

    /**
     * Bean Validation 실패. 명세서 1.1 에 따라 첫 번째 위반 필드를 {@code error.field} 로 알려 준다.
     * 화면이 그 입력만 하이라이트할 수 있게 하려는 것이다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        ErrorCode ec = ErrorCode.COMMON_INVALID_REQUEST;
        return ResponseEntity.status(ec.status())
                .body(e.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(f -> ApiResponse.<Void>fail(ApiError.of(
                                ec.code(),
                                f.getDefaultMessage() == null ? ec.message() : f.getDefaultMessage(),
                                f.getField())))
                        .orElseGet(() -> ApiResponse.fail(ApiError.of(ec.code(), ec.message()))));
    }

    /**
     * 마지막 그물. 봉투 모양을 전 API 에서 하나로 유지하기 위해 둔다.
     *
     * <p>명세서 1.1 이 "스택 트레이스·내부 용어 금지"를 못박았으므로 원인은 로그에만 남기고
     * 사용자에게는 일반 문구만 내보낸다. 예외를 삼키지 않도록 로그는 스택까지 남긴다.
     *
     * <p>단, Spring MVC 가 이미 상태를 정해 둔 오류(405 · 415 · 필수 파라미터 누락 등)까지
     * 500 으로 뭉개면 안 된다. {@link ErrorResponse} 를 구현한 예외는 그 상태를 그대로 살린다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatusCode status = er.getStatusCode();
            ErrorCode ec = status.is4xxClientError()
                    ? ErrorCode.COMMON_INVALID_REQUEST
                    : ErrorCode.COMMON_INTERNAL_ERROR;
            log.warn("요청을 처리할 수 없음 ({}): {}", status, e.toString());
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail(ApiError.of(ec.code(), ec.message())));
        }
        ErrorCode ec = ErrorCode.COMMON_INTERNAL_ERROR;
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(ec.status())
                .body(ApiResponse.fail(ApiError.of(ec.code(), ec.message())));
    }
}
