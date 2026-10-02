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

    /**
     * Open Exchange Rates 창구.
     *
     * <p>App ID 는 헤더가 아니라 <b>질의 문자열</b>로 보낸다. 벤더가 그 방식만 받는다.
     * 그래서 여기서는 주소만 잡고, 키는 부르는 쪽이 붙인다.
     */
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
