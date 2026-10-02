package com.example.mijang.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** FxProperties 등록과 환율 벤더용 RestClient. ExternalApiConfig 와 같은 방식이다. */
@Configuration
@EnableConfigurationProperties(FxProperties.class)
public class FxConfig {

    private final FxProperties props;
    private final ExternalApiProperties external;

    public FxConfig(FxProperties props, ExternalApiProperties external) {
        this.props = props;
        this.external = external;
    }

    /** Open Exchange Rates 클라이언트를 만든다. App ID 는 벤더가 질의 문자열만 받으므로 호출부가 붙인다. */
    @Bean
    public RestClient fxClient() {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory())
                .build();
    }

    /** 타임아웃은 다른 벤더와 같은 값을 쓴다. */
    private org.springframework.http.client.ClientHttpRequestFactory requestFactory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(java.time.Duration.ofMillis(external.connectTimeoutMs()));
        f.setReadTimeout(java.time.Duration.ofMillis(external.readTimeoutMs()));
        return f;
    }
}
