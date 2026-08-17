/*
 * MarketConfig — 실시간 시세 설정을 스프링에 등록하는 조각
 *
 * 이 파일이 하는 일
 *   MarketProperties 를 빈으로 만들어 준다.
 *   이게 없으면 properties 에 적은 구독 한도·스트림 주소를 어디서도 읽지 못한다.
 */
package com.example.mijang.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketProperties.class)
public class MarketConfig {
}
