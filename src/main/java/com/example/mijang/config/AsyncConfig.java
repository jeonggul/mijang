package com.example.mijang.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @Async} 를 켠다. Executor 빈을 직접 선언하면 부트 기본 실행기가 사라져 응답 시간으로 가입 여부가 새는 문제가 되살아나므로 만들지 않는다. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
