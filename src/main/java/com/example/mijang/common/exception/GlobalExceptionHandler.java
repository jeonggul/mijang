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

/** REST 컨트롤러 전용 예외 처리다. annotations 제한을 풀면 화면(Thymeleaf) 오류까지 잡으므로 바꾸면 안 된다. */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** BusinessException 을 API 오류 봉투로 변환한다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.errorCode();
        return ResponseEntity.status(ec.status())
                .body(ApiResponse.fail(ApiError.of(ec.code(), ec.message(), e.field())));
    }

    /** Bean Validation 실패를 첫 번째 위반 필드와 함께 400 으로 내린다. */
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

    /** 남은 예외를 일반 문구로 감싼다. ErrorResponse 구현 예외는 원래 상태(405 등)를 살려야 한다. */
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
