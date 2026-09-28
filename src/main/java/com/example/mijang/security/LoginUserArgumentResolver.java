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

    /** {@code @LoginUser} 어노테이션과 SessionUser 타입을 모두 갖춘 파라미터인지 판단한다. */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && SessionUser.class.equals(parameter.getParameterType());
    }

    /** SecurityContext 에서 로그인 사용자를 꺼내 준다. 비로그인이면 null 이다. */
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
