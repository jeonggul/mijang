package com.example.mijang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 설정. application.properties 의 mijang.mail.* 를 받는다.
 *
 * <p>스프링 부트에도 같은 이름의 클래스가 있다(spring.mail.*). 그쪽은 SMTP 접속 정보이고
 * 이 클래스는 미장이 쓰는 값이다. 둘을 한 파일에서 같이 import 하지 않는다.
 */
@ConfigurationProperties(prefix = "mijang.mail")
public class MailProperties {

    /**
     * 어떤 수단으로 보낼지. {@code log} 면 발송하지 않고 로그에만 남기고, {@code smtp} 면 실제로 보낸다.
     *
     * <p>참·거짓이 아니라 이름으로 둔 이유 — 나중에 발송 대행 서비스를 붙일 때
     * 값 하나만 늘리면 되고, 켜고 끄는 스위치와 수단 선택이 뒤섞이지 않는다.
     */
    private String transport = "log";
    /**
     * 보내는 사람 주소.
     *
     * <p>Gmail 로 보내면 이 값이 무시되고 로그인한 계정 주소로 바뀐다. 아무 주소나
     * 보내는 사람으로 적을 수 있으면 그게 곧 피싱이라 막혀 있다. 원하는 주소로 보내려면
     * 그 도메인을 소유하고 발송 서비스에 등록해야 한다.
     */
    private String from = "no-reply@mijang.app";

    /** 받는 쪽 메일함에 보이는 이름. 주소 앞에 붙는다. */
    private String fromName = "미장";
    /**
     * 로그에 링크 전체를 찍을지. 링크의 토큰은 그 자체로 계정을 가져갈 수 있는 열쇠라
     * 기본은 끈다. 로컬에서 흐름을 확인할 때만 켠다.
     */
    private boolean logLinks = false;
    /** 재설정 링크 앞에 붙일 서비스 주소. 끝에 / 를 넣지 않는다. */
    private String baseUrl = "http://localhost:8080";

    // 아래는 스프링이 값을 넣고 꺼내기 위한 접근자다.

    /** 발송 수단을 읽는다. 어떤 MailTransport 구현이 뜰지 결정한다. */
    public String getTransport() { return transport; }
    /** mijang.mail.transport 주입. log 또는 smtp. */
    public void setTransport(String transport) { this.transport = transport; }

    /** 보내는 사람 이름을 읽는다. */
    public String getFromName() { return fromName; }
    /** mijang.mail.from-name 주입. */
    public void setFromName(String fromName) { this.fromName = fromName; }

    /** 보내는 사람 주소를 읽는다. */
    public String getFrom() { return from; }
    /** mijang.mail.from 주입. */
    public void setFrom(String from) { this.from = from; }

    /** 링크 전체를 로그에 찍을지 읽는다. */
    public boolean isLogLinks() { return logLinks; }
    /** mijang.mail.log-links 주입. 배포에서는 절대 켜지 않는다. */
    public void setLogLinks(boolean logLinks) { this.logLinks = logLinks; }

    /** 서비스 주소를 읽는다. 재설정 링크를 만들 때 쓴다. */
    public String getBaseUrl() { return baseUrl; }
    /** mijang.mail.base-url 주입. */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
