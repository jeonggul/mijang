package com.example.mijang.config;

import com.example.mijang.security.LoginUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 부가 설정.
 *
 * <p>화면 접근 가드는 인터셉터가 아니라 SecurityFilterChain 이 맡는다.
 * 필터가 컨트롤러보다 앞이라 가드가 한 겹으로 끝난다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final CspInterceptor cspInterceptor;

    public WebConfig(LoginUserArgumentResolver loginUserArgumentResolver,
                     CspInterceptor cspInterceptor) {
        this.loginUserArgumentResolver = loginUserArgumentResolver;
        this.cspInterceptor = cspInterceptor;
    }

    /**
     * 모든 요청에 실행 규칙(CSP)을 붙인다.
     *
     * <p>화면만이 아니라 API 응답에도 붙는다. 머리말 하나라 부담이 없고,
     * 경로마다 켜고 끄면 빠뜨리는 곳이 생긴다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cspInterceptor).addPathPatterns("/**");
    }

    /**
     * {@code @LoginUser} 리졸버를 MVC 에 등록한다.
     *
     * <p>등록하지 않으면 어노테이션이 무시되고 파라미터에 null 이 들어온다.
     * 컴파일은 통과하므로 빠뜨리면 런타임에야 드러난다.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
