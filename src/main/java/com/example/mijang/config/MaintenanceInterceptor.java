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

/** 점검 모드가 켜져 있으면 관리자·로그인 경로를 뺀 접근을 막는다. */
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    /* 점검 중에도 여는 경로다. 관리자·로그인을 막으면 점검 모드를 끌 수 없다. */
    private static final List<String> ALWAYS_OPEN = List.of(
            "/admin", "/api/admin",
            "/login", "/api/auth/login", "/api/auth/logout", "/api/auth/refresh",
            "/css/", "/js/", "/img/", "/favicon.ico", "/error", "/maintenance");

    private final AdminSettingService settingService;

    /** 점검 모드면 API 는 503 예외, 화면은 점검 페이지로 막는다. */
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
        /* API 는 예외로 던져 503 봉투로 내보낸다. */
        if (path.startsWith("/api/")) {
            throw new BusinessException(ErrorCode.MAINTENANCE_MODE);
        }
        /* 화면은 예외로 던지면 콘텐츠 협상 실패로 500 이 되므로 여기서 직접 점검 화면을 내보낸다. */
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        try {
            request.getRequestDispatcher("/maintenance").forward(request, response);
        } catch (ServletException | IOException e) {
            throw new BusinessException(ErrorCode.MAINTENANCE_MODE);
        }
        return false;
    }

    /** ROLE_ADMIN 여부를 판정한다. */
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
