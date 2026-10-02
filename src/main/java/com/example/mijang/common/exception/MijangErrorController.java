package com.example.mijang.common.exception;

import com.example.mijang.common.response.ApiError;
import com.example.mijang.common.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/** 컨트롤러에 닿기 전에 끝난 요청(404·405 등)을 API 는 JSON 봉투, 화면은 error.html 로 나눠 응답한다. */
@Controller
public class MijangErrorController implements ErrorController {

    private static final String API_PREFIX = "/api/";

    /** 오류 요청을 JSON 봉투 또는 error.html 로 응답한다. */
    @RequestMapping("${server.error.path:/error}")
    public Object handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String uri = asString(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        if (wantsJson(request, uri)) {
            ErrorCode errorCode = toErrorCode(status);
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail(ApiError.of(errorCode.code(), errorCode.message())));
        }
        return new ModelAndView("error", Map.of("status", status.value()), status);
    }

    /** API 경로이거나 Accept 에 HTML 이 없으면 JSON 으로 판단한다. */
    private boolean wantsJson(HttpServletRequest request, String uri) {
        if (uri != null && uri.startsWith(API_PREFIX)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept == null || !accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (raw instanceof Integer code) {
            HttpStatus resolved = HttpStatus.resolve(code);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /** 상태에 맞는 코드를 고른다. 코드와 실제 HTTP 상태가 어긋나지 않게 404·405 는 전용 코드를 둔다. */
    private ErrorCode toErrorCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ErrorCode.COMMON_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.COMMON_METHOD_NOT_ALLOWED;
            case UNAUTHORIZED -> ErrorCode.AUTH_REQUIRED;
            case FORBIDDEN -> ErrorCode.COMMON_FORBIDDEN;
            default -> status.is4xxClientError()
                    ? ErrorCode.COMMON_INVALID_REQUEST
                    : ErrorCode.COMMON_INTERNAL_ERROR;
        };
    }

    private String asString(Object value) {
        return value instanceof String s ? s : null;
    }
}
