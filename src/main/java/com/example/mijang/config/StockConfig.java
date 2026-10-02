package com.example.mijang.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** StockProperties 등록. ExternalApiConfig 와 같은 방식이다. */
@Configuration
@EnableConfigurationProperties(StockProperties.class)
public class StockConfig {
}
