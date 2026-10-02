/*
 * MaintenanceInterceptor — 점검 모드
 *
 * 이 파일이 하는 일
 *   운영 설정의 점검 모드가 켜져 있으면 관리자를 뺀 모든 접근을 막는다.
 *
 *   왜 인터셉터인가 — SecurityFilterChain 은 "누구인가" 로 가르는 자리다. 점검 모드는
 *   권한이 아니라 서비스 상태라 기준이 다르고, 설정이 바뀌면 즉시 반영돼야 하는데
 *   필터 체인은 기동 때 한 번 짜인다.
 *
 *   관리자는 통과시킨다. 막아 두면 점검 모드를 켠 사람이 그것을 끌 수 없다.
 *   로그인·로그아웃도 열어 둔다 — 관리자가 점검 중에 다시 로그인해야 할 수 있다.
 */
package com.example.mijang.config;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 점검 모드. 화면 SR-013 운영 설정 */
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    /**
     * 점검 중에도 열어 두는 경로.
     *
     * <p>관리자 화면과 그 API, 로그인·로그아웃, 정적 자원이다. 정적 자원을 막으면
     * 점검 안내 화면조차 스타일 없이 뜬다.
     */
    private static final List<String> ALWAYS_OPEN = List.of(
            "/admin", "/api/admin",
            "/login", "/api/auth/login", "/api/auth/logout", "/api/auth/refresh",
            "/css/", "/js/", "/img/", "/favicon.ico", "/error", "/maintenance");

    private final AdminSettingService settingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!settingService.isOn(AdminSettingKey.MAINTENANCE_ENABLED)) {
            return true;
        }
        String path = request.getRequestURI();
        if (ALWAYS_OPEN.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (isAdmin()) {
            return true;
        }
        /* API 는 예외로 던진다. GlobalExceptionHandler 가 503 봉투로 바꿔 준다 */
        if (path.startsWith("/api/")) {
            throw new BusinessException(ErrorCode.MAINTENANCE_MODE);
        }
        /* 화면은 여기서 직접 점검 화면을 내보낸다.
           예외로 던지면 처리기가 JSON 봉투만 만들 수 있어 Accept: text/html 과
           협상에 실패하고, 그 실패가 500 으로 바뀌어 "페이지를 찾을 수 없습니다"
           가 대신 뜬다 — 점검 중이라는 사실이 사용자에게 전달되지 않는다 */
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        try {
            request.getRequestDispatcher("/maintenance").forward(request, response);
        } catch (ServletException | IOException e) {
            throw new BusinessException(ErrorCode.MAINTENANCE_MODE);
        }
        return false;
    }

    /** ROLE_ADMIN 인지. 비로그인이면 당연히 아니다. */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
