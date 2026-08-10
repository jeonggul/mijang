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

/**
 * 컨트롤러에 닿기 전에 끝난 요청의 오류 응답.
 *
 * <p>{@link GlobalExceptionHandler} 는 {@code @RestController} 안에서 던져진 예외만 잡는다.
 * 없는 경로(404)나 허용되지 않은 메서드(405)는 핸들러를 고르는 단계에서 끝나므로 거기에 닿지 않고,
 * 그대로 두면 Spring 기본 오류 응답({@code timestamp} · {@code status} · {@code trace} …)이 나간다.
 * 봉투가 두 종류가 되어 화면 쪽에서 분기해야 하고, 개발 모드에서는 스택 트레이스까지 실려 나간다.
 *
 * <p>그래서 두 갈래로 나눈다. 이 규칙은 templates/error.html 에 이미 적혀 있던 것을 코드로 옮긴 것이다.
 * <ul>
 *   <li>{@code /api/**} 이거나 HTML 을 원하지 않는 요청 → [[미장-API명세서]] 1.1 봉투(JSON)</li>
 *   <li>그 외(브라우저 화면 요청) → templates/error.html</li>
 * </ul>
 */
@Controller
public class MijangErrorController implements ErrorController {

    private static final String API_PREFIX = "/api/";

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

    /**
     * API 경로면 무조건 JSON 이다. 그 밖에는 Accept 에 HTML 이 없을 때만 JSON 으로 내려
     * fetch 로 부른 쪽이 화면 마크업을 받지 않게 한다.
     */
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
