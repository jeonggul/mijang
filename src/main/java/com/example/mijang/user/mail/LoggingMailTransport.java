package com.example.mijang.user.mail;

import com.example.mijang.config.MailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 보내지 않고 로그에만 남긴다. mijang.mail.transport 가 log 이거나 없을 때 뜬다.
 *
 * <p>SMTP 계정 없이 전체 흐름을 확인하기 위한 것이다. 로그에 찍힌 링크를 그대로
 * 주소창에 붙여 넣으면 실제 메일을 받은 것과 같다.
 *
 * <p><b>링크 전체는 mijang.mail.log-links 가 켜져 있을 때만 찍는다.</b> 링크의 토큰은
 * 그것 하나로 임의의 비밀번호를 설정할 수 있는 열쇠라, 로그를 읽을 수 있는 사람이
 * 곧 계정을 가져갈 수 있는 사람이 된다. 기본값을 꺼 두어 배포에 이 구현이 잘못
 * 올라가더라도 열쇠까지 새지는 않게 한다.
 *
 * <p>운영에서 이 구현이 뜨면 아무도 메일을 받지 못하므로 경고로 남겨 눈에 띄게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mijang.mail", name = "transport", havingValue = "log", matchIfMissing = true)
@RequiredArgsConstructor
public class LoggingMailTransport implements MailTransport {

    private final MailProperties props;

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
