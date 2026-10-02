package com.example.mijang.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** 요청마다 CSP nonce 를 만들어 응답 헤더와 화면에 넘긴다. */
@Component
public class CspInterceptor implements HandlerInterceptor {

    /** 화면이 nonce 를 꺼내 쓰는 속성 이름이다. */
    public static final String NONCE_ATTRIBUTE = "cspNonce";

    /* nonce 는 예측 불가능해야 하므로 SecureRandom 을 바꾸면 안 된다. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int NONCE_BYTES = 16;

    /** nonce 를 만들어 요청 속성과 CSP 헤더에 싣는다. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String nonce = newNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        response.setHeader("Content-Security-Policy", policy(nonce));
        return true;
    }

    /** Thymeleaf 가 쓸 수 있게 nonce 를 모델에 넣는다. */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView == null) {
            return;
        }
        /* 리다이렉트 모델에 넣으면 nonce 가 질의 문자열로 새어 나가므로 건너뛴다. */
        if (modelAndView.getViewName() != null
                && modelAndView.getViewName().startsWith("redirect:")) {
            return;
        }
        modelAndView.addObject(NONCE_ATTRIBUTE, request.getAttribute(NONCE_ATTRIBUTE));
    }

    private String newNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /* 지시어를 좁히면 구글 폰트·벤더 이미지가, 넓히면 XSS 방어가 깨진다. */
    private String policy(String nonce) {
        return "default-src 'self'; "
             + "script-src 'self' 'nonce-" + nonce + "'; "
             + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
             + "font-src 'self' https://fonts.gstatic.com; "
             + "img-src 'self' data: https:; "
             + "connect-src 'self'; "
             + "object-src 'none'; "
             + "base-uri 'self'; "
             + "form-action 'self'; "
             + "frame-ancestors 'none'";
    }
}
