package com.example.mijang.config;

import com.example.mijang.security.LoginUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 부가 설정(인터셉터·리졸버 등록)이다. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final CspInterceptor cspInterceptor;
    private final MaintenanceInterceptor maintenanceInterceptor;

    public WebConfig(LoginUserArgumentResolver loginUserArgumentResolver,
                     CspInterceptor cspInterceptor,
                     MaintenanceInterceptor maintenanceInterceptor) {
        this.loginUserArgumentResolver = loginUserArgumentResolver;
        this.cspInterceptor = cspInterceptor;
        this.maintenanceInterceptor = maintenanceInterceptor;
    }

    /** 모든 요청에 CSP·점검 모드 인터셉터를 건다. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cspInterceptor).addPathPatterns("/**");
        /* 점검 모드는 반드시 CSP 뒤에 둔다. 막힌 응답에도 CSP 가 붙어야 한다. */
        registry.addInterceptor(maintenanceInterceptor).addPathPatterns("/**");
    }

    /** {@code @LoginUser} 리졸버를 등록한다. 빠지면 파라미터에 null 이 들어온다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
