package com.example.mijang.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 필요 경로 가드.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.security
 * <p>TODO: 아직 어디에도 등록하지 않았다. 인증 방식이 정해지면 WebMvcConfigurer 로 경로를 지정해 붙인다.
 * 지금 등록하면 프로토타입 화면이 전부 막히므로 일부러 비워 둔다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }
}
