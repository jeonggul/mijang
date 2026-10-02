package com.example.mijang.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link LoginUser} 가 붙은 SessionUser 파라미터를 SecurityContext 에서 꺼내 채운다. */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 이 리졸버가 채울 파라미터인지 판단한다.
     *
     * <p>어노테이션과 타입을 모두 본다. {@code @LoginUser} 만 보면 다른 타입에도 걸리고,
     * 타입만 보면 어노테이션 없는 SessionUser 파라미터까지 가로챈다.
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && SessionUser.class.equals(parameter.getParameterType());
    }

    /**
     * SecurityContext 에서 로그인 사용자를 꺼내 파라미터에 넣는다.
     *
     * <p>비로그인이면 null 을 돌려준다. 공개 화면에서도 {@code @LoginUser} 를 받아
     * "로그인했으면 이름을 보여준다" 같은 분기를 쓸 수 있게 하기 위해서다.
     * 인증이 필수인 곳은 SecurityConfig 가 이미 막으므로 null 이 올 수 없다.
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SessionUser user)) {
            return null;
        }
        return user;
    }
}
