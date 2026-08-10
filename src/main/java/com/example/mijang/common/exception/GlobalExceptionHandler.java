package com.example.mijang.common.exception;

import com.example.mijang.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
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
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.errorCode();
        return ResponseEntity.status(ec.status())
                .body(ApiResponse.fail(ec.code(), ec.message()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse(ErrorCode.INVALID_REQUEST.message());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST.code(), detail));
    }
}
