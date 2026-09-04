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

/**
 * 인증·인가 설정.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.config
 * <p>토큰 검증은 {@link JwtAuthenticationFilter} 가 하고, 여기서는 "어디를 막을지"만 정한다.
 *
 * <p>체인을 둘로 나눈 이유 — 미인증 응답이 달라야 한다.
 * API 는 401 과 JSON 봉투, 화면은 /login 리다이렉트다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)   // ExternalApiConfig 와 같은 방식으로 등록
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final SocialAuthHandlers socialAuthHandlers;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          SocialAuthHandlers socialAuthHandlers) {
        this.jwtFilter = jwtFilter;
        this.socialAuthHandlers = socialAuthHandlers;
    }

    /**
     * API 체인. {@code /api/**} 만 맡고 미인증이면 401 과 JSON 봉투를 돌려준다.
     *
     * <p>{@code @Order(1)} 로 화면 체인보다 먼저 매칭시킨다. 순서가 뒤바뀌면
     * 화면 체인이 모든 경로를 삼켜 API 요청이 로그인 화면으로 리다이렉트된다.
     *
     * <p>permitAll 목록은 비로그인 허용 범위다. 종목 검색·시세는 가입 없이 볼 수 있다는
     * 기획서 4장 방침을 여기서 구현한다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            // 토큰 인증이라 세션을 만들지 않는다. 세션이 없으면 CSRF 토큰도 둘 곳이 없다
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(auth -> auth
                // 로그아웃은 토큰이 만료됐거나 서버가 재시작된 뒤에도 반드시 성공해야 한다.
                // 인증을 요구하면 그때 401 이 나고 쿠키가 지워지지 않은 채 남는다.
                // 비밀번호 재설정은 로그인할 수 없는 사람이 쓰는 경로라 인증을 걸 수 없다.
                // PATCH /api/auth/password 는 여기 없다 — 그쪽은 로그인 상태에서만 부른다.
                .requestMatchers("/api/auth/signup", "/api/auth/login",
                                 "/api/auth/refresh", "/api/auth/logout",
                                 "/api/auth/check-nickname",
                                 "/api/auth/password/forgot", "/api/auth/password/reset",
                                 // 소셜 계정 연결은 로그인 <b>전에</b> 부른다. 인증을 걸면
                                 // 연결하려는 사람이 영영 들어올 수 없다. 대신 연결할 신원을
                                 // 세션에서만 꺼내고, 비밀번호를 다시 확인한다.
                                 "/api/auth/social/link").permitAll()
                // 종목별 커뮤니티는 종목 정보가 아니라 커뮤니티다. 아래 /api/stocks/** 보다
                // 먼저 둔다 — 규칙은 처음 걸리는 것이 이긴다.
                //
                // 이 줄이 없으면 GET /api/stocks/{symbol}/posts 가 아래 접두사에 걸려
                // 비로그인에게 열린다. 그쪽에는 작성자 닉네임과 실현손익 매매카드가 실려
                // 있고, 같은 내용을 담은 GET /api/posts 는 로그인을 요구한다.
                // 화면설계서 2.0 — 커뮤니티는 로그인 후.
                .requestMatchers("/api/stocks/*/posts").authenticated()
                // 비로그인도 종목 검색·시세 조회가 가능하다 (미장-기획서 4장).
                // 반드시 GET 만 연다. 접두사로 열면 같은 경로의 쓰기 API 까지 열린다 —
                // POST /api/stocks/{symbol}/posts 가 이 아래에 있다.
                .requestMatchers(HttpMethod.GET, "/api/stocks/**", "/api/calendar/**", "/api/fx/**")
                    .permitAll()
                // 시세 조회와 실시간 스트림은 공개다. 종목 정보와 같은 성격이다.
                // 다만 POST /api/market/subscriptions 는 열지 않는다 — 30종목뿐인
                // 공유 구독 풀을 아무나 채워 버릴 수 있다.
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

    /**
     * 화면 체인. API 를 제외한 나머지 전부를 맡는다.
     *
     * <p>미인증이면 401 이 아니라 {@code /login} 으로 보낸다. 브라우저 주소창에
     * JSON 이 뜨면 안 되기 때문이다.
     *
     * <p>정적 리소스를 permitAll 에 넣지 않으면 로그인 화면의 CSS 까지 막혀
     * 스타일 없는 화면이 뜬다.
     */
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
                /* 소셜 로그인이 오가는 길. 여기가 막히면 제공자에서 돌아오지 못한다 */
                .requestMatchers("/oauth2/**", "/login/oauth2/**", "/social-link").permitAll()
                .requestMatchers("/admin").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendRedirect("/login"))
                // sendError 를 써야 ERROR 디스패치를 타고 상태 코드가 보존된다.
                // sendRedirect 로 보내면 새 GET 이라 상태 속성이 없어 500 으로 떨어진다.
                .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
            /* 소셜 로그인. 인증이 끝나면 우리 핸들러가 JWT 쿠키를 굽는다 —
               세션 로그인으로 남기지 않는다(인증 방식을 두 벌로 만들지 않으려고) */
            .oauth2Login(o -> o
                .loginPage("/login")
                .successHandler(socialAuthHandlers.success())
                .failureHandler(socialAuthHandlers.failure()))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * API 체인의 미인증 진입점. 토큰이 없거나 만료됐을 때 불린다.
     *
     * <p>화면 쪽 리다이렉트와 달리 여기서는 상태 코드와 본문을 직접 써야 한다.
     */
    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res,
                                   org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {
        writeJson(res, 401, ErrorCode.AUTH_REQUIRED);
    }

    /**
     * 명세서 1.1 봉투를 직접 써 내려보낸다.
     *
     * <p>필터 단계라 {@code GlobalExceptionHandler} 가 잡지 못한다. 그쪽은 컨트롤러
     * 안에서 던져진 예외만 처리한다. 그래서 같은 모양의 JSON 을 여기서 한 번 더 만든다.
     * 봉투 모양이 바뀌면 두 곳을 함께 고쳐야 한다.
     */
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
