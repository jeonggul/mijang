package com.example.mijang.user.mail;

import com.example.mijang.config.MailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 메일을 보내지 않고 로그에만 남기는 개발용 전송이다 — 토큰 링크 전체는 mijang.mail.log-links가 켜져 있을 때만 찍는다. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mijang.mail", name = "transport", havingValue = "log", matchIfMissing = true)
@RequiredArgsConstructor
public class LoggingMailTransport implements MailTransport {

    private final MailProperties props;

    /** 재설정 링크를 발송하는 대신 경고 로그로 남긴다. */
    @Async
    @Override
    public void sendResetLink(String toEmail, String resetUrl, long ttlMinutes) {
        if (props.isLogLinks()) {
            log.warn("[메일 미발송] {} 로 보낼 재설정 링크({}분 유효) — {}",
                    MailTransport.mask(toEmail), ttlMinutes, resetUrl);
        } else {
            log.warn("[메일 미발송] {} 로 보낼 재설정 링크를 만들었으나 발송 수단이 없습니다. "
                    + "링크를 보려면 mijang.mail.log-links=true 로 두세요",
                    MailTransport.mask(toEmail));
        }
    }
}
