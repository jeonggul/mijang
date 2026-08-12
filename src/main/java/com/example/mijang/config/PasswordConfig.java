package com.example.mijang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시기.
 *
 * <p>SecurityConfig 가 아니라 여기 따로 두는 이유 — 순환 참조를 끊기 위해서다.
 * SecurityConfig 는 JwtAuthenticationFilter 를 받고, 그 필터는 AuthService 를,
 * AuthService 는 PasswordEncoder 를 받는다. 인코더를 SecurityConfig 가 만들면
 * SecurityConfig → 필터 → 서비스 → SecurityConfig 로 고리가 닫힌다.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt 로 저장한다 (users.password_hash 주석).
     *
     * <p>해시마다 salt 를 안에 품으므로 같은 비밀번호도 매번 다른 문자열이 된다.
     * 그래서 검증은 문자열 비교가 아니라 {@code matches()} 로 해야 한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
