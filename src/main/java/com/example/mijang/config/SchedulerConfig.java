package com.example.mijang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 배치 스케줄러 설정이다. 풀을 1로 줄이면 긴 배치가 SSE 심박을 막아 구독 자리가 유령 연결에 묶인다. */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    /** 주기 작업용 스레드 풀 스케줄러를 만든다. */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("mijang-sched-");
        // 종료 시 돌던 작업은 끝내고 나간다.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
