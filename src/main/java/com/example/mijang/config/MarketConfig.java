package com.example.mijang.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** MarketProperties 를 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(MarketProperties.class)
public class MarketConfig {
}
