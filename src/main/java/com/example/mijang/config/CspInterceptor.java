/*
 * CspInterceptor — 브라우저에게 무엇을 실행해도 되는지 알려 주는 곳
 *
 * 이 파일이 하는 일
 *   요청마다 한 번 쓰는 임의의 값(nonce)을 만들어 응답 머리말에 실어 보내고,
 *   화면이 그 값을 <script> 에 붙일 수 있게 넘겨준다.
 *
 *   왜 필요한가 — 지금까지는 어떤 스크립트든 실행됐다. 화면 어딘가에 남의 글이 끼어드는
 *   구멍이 하나라도 생기면 막을 것이 없다. CSRF 도 꺼져 있어서, 그렇게 들어온 스크립트는
 *   로그인한 사람의 이름으로 아무 요청이나 보낼 수 있다.
 *
 *   nonce 를 쓰면 <b>우리가 넣어 둔 스크립트만</b> 실행된다. 값이 요청마다 바뀌므로
 *   끼워 넣은 쪽은 맞출 수가 없다. 같은 이유로 javascript: 주소도 실행되지 않는다 —
 *   그런 주소에는 nonce 를 붙일 방법이 없다.
 *
 *   style 은 nonce 를 쓰지 않는다. 화면 곳곳에 style="..." 속성이 139군데 있고,
 *   그 속성에는 nonce 를 붙일 수 없다. 스타일로 할 수 있는 나쁜 짓은 스크립트보다
 *   훨씬 제한적이라 여기서는 열어 둔다.
 */
package com.example.mijang.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class CspInterceptor implements HandlerInterceptor {

    /** 화면이 nonce 를 꺼내 쓰는 이름 */
    public static final String NONCE_ATTRIBUTE = "cspNonce";

    /**
     * 예측할 수 없어야 한다. 맞힐 수 있으면 nonce 를 두는 의미가 없다.
     * {@code SecureRandom} 은 스레드에 안전하다.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 16바이트면 맞히는 것이 사실상 불가능하다 */
    private static final int NONCE_BYTES = 16;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String nonce = newNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        response.setHeader("Content-Security-Policy", policy(nonce));
        return true;
    }

    /**
     * 화면이 쓸 수 있게 모델에 넣는다.
     *
     * <p>Thymeleaf 3.1 부터는 표현식에서 요청 객체를 직접 볼 수 없다. 그래서 값을
     * 모델로 건네줘야 한다.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView == null) {
            return;
        }
        /* 리다이렉트에는 넣지 않는다. RedirectView 는 모델 값을 질의 문자열로 붙여서
           /login?cspNonce=... 처럼 nonce 가 주소에 새어 나온다 — 브라우저 기록과
           Referer 에 남고, 그린 화면과 다른 nonce 라 쓸모도 없다 */
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

    /**
     * 무엇을 어디서 불러올 수 있는지.
     *
     * <p>글꼴만 밖에서 받는다(구글 폰트). 이미지는 종목 로고와 뉴스 사진이 벤더 주소로 오므로
     * https 를 연다 — 이미지는 실행되지 않아 위험이 작다.
     *
     * <p>{@code frame-ancestors 'none'} 은 다른 사이트가 우리 화면을 감싸 띄우는 것을 막는다.
     * {@code form-action 'self'} 는 입력한 것이 남의 서버로 날아가는 것을 막는다.
     */
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
