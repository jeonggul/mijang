package com.example.mijang.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 환율 범위 설정. application.properties 의 mijang.fx.* 를 받는다.
 *
 * <p>App ID 는 여기 기본값을 두지 않는다. {@code application-secret.properties} 에서만
 * 채운다(미장-fx-구현 2.9).
 */
@ConfigurationProperties(prefix = "mijang.fx")
public class FxProperties {

    /** Open Exchange Rates 주소. */
    private String baseUrl = "https://openexchangerates.org/api";

    /** App ID. 비어 있으면 배치가 돌지 않는다. */
    private String appId;

    /**
     * 폴링 주기.
     *
     * <p>무료 플랜이 <b>월 1,000회</b>이고 벤더 갱신 자체가 1시간 주기다. 1시간이면 월 720회로
     * 재시도 여유까지 들어간다. 30분으로 당기면 1,440회라 한도를 넘는다(2.4).
     */
    private Duration pollInterval = Duration.ofHours(1);

    /**
     * 대체 값을 찾을 때 거슬러 올라갈 최대 일수.
     *
     * <p>연휴가 길어도 열흘이면 닿는다. 무한정 거슬러 오르면 몇 년 전 환율로 오늘 손익을
     * 계산하게 된다 — 그럴 바에는 값이 없다고 답하는 편이 낫다.
     */
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
