package com.example.mijang.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 메일·재설정 설정 등록. ExternalApiConfig 와 같은 방식이다. */
@Configuration
@EnableConfigurationProperties({MailProperties.class, PasswordResetProperties.class})
public class MailConfig {
}
