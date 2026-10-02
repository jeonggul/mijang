package com.example.mijang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 메일({@code mijang.mail.*}) 설정 바인딩이다. 부트의 spring.mail MailProperties 와 다른 클래스이니 혼동하지 않는다. */
@ConfigurationProperties(prefix = "mijang.mail")
public class MailProperties {

    /** 발송 수단이다. log 면 로그에만 남기고 smtp 면 실제로 보낸다. */
    private String transport = "log";
    /** 보내는 사람 주소다. Gmail 발송 시에는 무시되고 로그인 계정 주소로 바뀐다. */
    private String from = "no-reply@mijang.app";

    /** 받는 쪽 메일함에 보이는 이름이다. */
    private String fromName = "미장";
    /* 링크의 토큰은 계정 탈취 열쇠라 배포에서 켜면 안 된다. */
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
