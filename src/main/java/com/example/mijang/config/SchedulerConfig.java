/*
 * SchedulerConfig — 주기 작업을 돌리는 자리
 *
 * 이 파일이 하는 일
 *   @Scheduled 를 켜고, 그 작업들이 서로를 막지 않을 만큼 스레드를 준다.
 */
package com.example.mijang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 배치 스케줄러 설정.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.config
 *
 * <p><b>스레드를 여러 개 둔다.</b> 기본값은 하나라서 모든 주기 작업이 한 줄로 선다.
 * 밤에 도는 일봉 수집은 100종목씩 130번 벤더를 부르며 몇 분씩 걸리는데, 그동안
 * 25초짜리 SSE 심박과 20초짜리 지연 폴러, 1분짜리 피드 전환이 전부 멈춘다.
 * 심박이 멈추면 죽은 연결을 걷어내지 못해 구독 자리 30칸이 유령에게 묶인다 —
 * 심박을 넣은 이유가 통째로 무효가 된다.
 *
 * <p>주기 작업이 여덟 개쯤 되고 대부분 짧아 넷이면 충분하다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("mijang-sched-");
        // 종료할 때 돌던 작업은 끝내고 나간다. 반쯤 쓴 배치가 남는 것보다 낫다
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
