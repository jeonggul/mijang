package com.example.mijang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호 해시기 구성이다. SecurityConfig 로 옮기면 순환 참조가 생기므로 따로 둔다. */
@Configuration
public class PasswordConfig {

    /** BCrypt 인코더를 만든다. 검증은 문자열 비교가 아니라 matches() 로 해야 한다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
