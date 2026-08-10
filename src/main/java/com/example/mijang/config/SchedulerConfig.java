package com.example.mijang.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 스케줄러 설정.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.config
 * <p>TODO: 환율 수집·일별 스냅샷·종목 마스터 갱신 배치가 여기에 붙는다. (개발명세서 '실시간·배치 상세' 시트)
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
