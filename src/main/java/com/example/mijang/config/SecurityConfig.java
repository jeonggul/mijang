package com.example.mijang.config;

import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.JwtAuthenticationFilter;
import com.example.mijang.user.oauth.SocialAuthHandlers;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 인증·인가 설정이다. 미인증 응답이 달라 API(401 JSON)와 화면(/login 리다이렉트) 체인을 나눈다. */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final SocialAuthHandlers socialAuthHandlers;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          SocialAuthHandlers socialAuthHandlers) {
        this.jwtFilter = jwtFilter;
        this.socialAuthHandlers = socialAuthHandlers;
    }

    /** API 체인을 만든다. @Order(1)을 화면 체인보다 뒤로 돌리면 화면 체인이 전 경로를 삼켜 API 가 로그인으로 리다이렉트된다. */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            // 토큰 인증이라 세션이 없고, 세션이 없으면 CSRF 토큰도 둘 곳이 없다.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(auth -> auth
                // 로그아웃·재설정·소셜 연결은 로그인 전에 부르는 경로라 인증을 걸면 안 된다.
                .requestMatchers("/api/auth/signup", "/api/auth/login",
                                 "/api/auth/refresh", "/api/auth/logout",
                                 "/api/auth/check-nickname",
                                 "/api/auth/password/forgot", "/api/auth/password/reset",
                                 "/api/auth/social/link",
                                 "/api/auth/social/pending",
                                 "/api/auth/social/signup").permitAll()
                // /api/stocks/** 보다 반드시 먼저 둔다. 뒤로 밀면 커뮤니티 글이 비로그인에게 열린다.
                .requestMatchers("/api/stocks/*/posts").authenticated()
                // 반드시 GET 만 연다. 접두사로 열면 같은 경로의 쓰기 API 까지 열린다.
                .requestMatchers(HttpMethod.GET, "/api/stocks/**", "/api/calendar/**", "/api/fx/**")
                    .permitAll()
                // GET 만 공개한다. POST /api/market/subscriptions 를 열면 공유 구독 풀이 뚫린다.
                .requestMatchers(HttpMethod.GET, "/api/market/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(this::writeUnauthorized)
                .accessDeniedHandler((req, res, ex) -> writeJson(res, 403,
                        ErrorCode.COMMON_FORBIDDEN)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 화면 체인을 만든다. 미인증이면 /login 으로 보낸다. */
    @Bean
    @Order(2)
    public SecurityFilterChain viewChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                // 진입점과 인증 화면
                .requestMatchers("/", "/login", "/signup",
                                 "/password-forgot", "/password-reset",
                                 "/terms", "/privacy").permitAll()
                // 비로그인도 볼 수 있는 화면
                .requestMatchers("/search", "/stock", "/error", "/maintenance").permitAll()
                /* 소셜 로그인 왕복 경로다. 막으면 제공자에서 돌아오지 못한다. */
                .requestMatchers("/oauth2/**", "/login/oauth2/**",
                                 "/social-link", "/social-signup").permitAll()
                .requestMatchers("/admin").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendRedirect("/login"))
                // sendError 여야 상태 코드가 보존된다. sendRedirect 로 바꾸면 500 으로 떨어진다.
                .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
            /* 소셜 로그인 성공 시 세션 대신 JWT 쿠키를 굽는다. */
            .oauth2Login(o -> o
                .loginPage("/login")
                .successHandler(socialAuthHandlers.success())
                .failureHandler(socialAuthHandlers.failure()))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** API 미인증 요청에 401 봉투를 써 내린다. */
    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res,
                                   org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {
        writeJson(res, 401, ErrorCode.AUTH_REQUIRED);
    }

    /** 필터 단계라 예외 처리기가 못 잡으므로 API 봉투 JSON 을 직접 쓴다. 봉투 모양이 바뀌면 여기도 함께 고친다. */
    private void writeJson(jakarta.servlet.http.HttpServletResponse res, int status, ErrorCode code)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("""
                {"success":false,"data":null,"error":{"code":"%s","message":"%s","field":null}}"""
                .formatted(code.code(), code.message()));
    }
}
