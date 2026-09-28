package com.example.mijang.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 환율({@code mijang.fx.*}) 설정 바인딩이다. App ID 는 secret 파일에서만 채운다. */
@ConfigurationProperties(prefix = "mijang.fx")
public class FxProperties {

    /** Open Exchange Rates 주소. */
    private String baseUrl = "https://openexchangerates.org/api";

    /** App ID. 비어 있으면 배치가 돌지 않는다. */
    private String appId;

    /** 폴링 주기다. 무료 플랜이 월 1,000회라 30분으로 당기면 한도를 넘는다. */
    private Duration pollInterval = Duration.ofHours(1);

    /** 대체 환율을 찾을 때 거슬러 올라갈 최대 일수다. */
    private int substituteLookbackDays = 10;

    // 아래는 스프링이 값을 넣고 꺼내기 위한 접근자다.

    /** 벤더 주소를 읽는다. */
    public String getBaseUrl() { return baseUrl; }
    /** mijang.fx.base-url 주입. */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /** App ID 를 읽는다. */
    public String getAppId() { return appId; }
    /** mijang.fx.app-id 주입. secret 파일에서만 채운다. */
    public void setAppId(String appId) { this.appId = appId; }

    /** 폴링 주기를 읽는다. */
    public Duration getPollInterval() { return pollInterval; }
    /** mijang.fx.poll-interval 주입. */
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }

    /** 대체 탐색 기간을 읽는다. */
    public int getSubstituteLookbackDays() { return substituteLookbackDays; }
    /** mijang.fx.substitute-lookback-days 주입. */
    public void setSubstituteLookbackDays(int days) { this.substituteLookbackDays = days; }
}
