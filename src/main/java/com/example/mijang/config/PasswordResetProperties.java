package com.example.mijang.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 비밀번호 재설정 링크({@code mijang.password-reset.*}) 설정 바인딩이다. */
@ConfigurationProperties(prefix = "mijang.password-reset")
public class PasswordResetProperties {

    /** 링크 유효 시간. 메일을 확인하는 데 걸리는 시간만 열어 둔다. */
    private Duration tokenTtl = Duration.ofMinutes(30);

    /** 재전송 간격. 이 안에 다시 요청하면 새 링크를 만들지 않는다. 메일 폭탄을 막는다. */
    private Duration resendCooldown = Duration.ofSeconds(60);

    // 아래는 스프링이 값을 넣고 꺼내기 위한 접근자다.

    /** 링크 유효 시간을 읽는다. 메일 본문과 화면 안내가 같은 값을 쓴다. */
    public Duration getTokenTtl() { return tokenTtl; }
    /** mijang.password-reset.token-ttl 주입. 30m 같은 표기를 받는다. */
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }

    /** 재전송 간격을 읽는다. */
    public Duration getResendCooldown() { return resendCooldown; }
    /** mijang.password-reset.resend-cooldown 주입. 60s 같은 표기를 받는다. */
    public void setResendCooldown(Duration resendCooldown) { this.resendCooldown = resendCooldown; }
}
