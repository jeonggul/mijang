package com.example.mijang.user.mail;

import com.example.mijang.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SMTP 로 실제 발송한다. mijang.mail.transport=smtp 일 때만 뜬다.
 *
 * <p>본문을 HTML 로 보낸다. 버튼이 눌리지 않는 메일 앱을 위해 주소도 함께 적는다.
 *
 * <p>발송 실패를 밖으로 던지지 않는다. 던지면 요청이 500 으로 끝나는데,
 * 그러면 "가입된 이메일일 때만 오류가 난다"가 되어 8.1.3 이 무너진다.
 * 어차피 발송은 응답 뒤에 일어나므로 던져도 화면에 닿지 못한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mijang.mail", name = "transport", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final MailProperties props;

    @Async
    @Override
    public void sendResetLink(String toEmail, String resetUrl, long ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(props.getFrom(), props.getFromName());
            helper.setTo(toEmail);
            helper.setSubject("[미장] 비밀번호 재설정 안내");
            helper.setText(buildBody(resetUrl, ttlMinutes), true);
            mailSender.send(message);
            /* 성공도 남긴다. 예전에는 실패만 적어서, 로그가 조용하면 보낸 것인지
               아예 부르지 않은 것인지 구분할 수 없었다 — 메일이 안 온다는 말에
               어디부터 봐야 할지 알 수 없었다. 주소는 가려서 남긴다 */
            log.info("재설정 메일 발송 — {}", MailTransport.mask(toEmail));
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            // 사용자에게는 성공과 같은 응답이 이미 나갔다. 원인은 로그에만 남긴다
            log.error("재설정 메일 발송 실패 — {}", MailTransport.mask(toEmail), e);
        }
    }

    /**
     * 메일 본문.
     *
     * <p>유효 시간을 글자로 박지 않고 인자로 받는다. 설정을 바꿨을 때 메일이 거짓말을
     * 하지 않게 하기 위해서다.
     */
    private String buildBody(String resetUrl, long ttlMinutes) {
        return """
                <div style="max-width:520px;margin:0 auto;padding:32px 24px;\
                font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#16233D;">
                  <h2 style="margin:0 0 16px;font-size:20px;">미장 비밀번호 재설정</h2>
                  <p style="line-height:1.7;">아래 버튼을 눌러 새 비밀번호를 설정해주세요.</p>
                  <p style="margin:24px 0;">
                    <a href="%s" style="display:inline-block;padding:12px 28px;border-radius:9px;\
                background:#1B2A4A;color:#ffffff;text-decoration:none;font-weight:700;">비밀번호 재설정</a>
                  </p>
                  <p style="font-size:13px;color:#7A879E;line-height:1.7;">
                    이 링크는 <strong>%d분</strong> 동안만 유효하며, 한 번 쓰면 만료됩니다.<br>
                    버튼이 눌리지 않으면 아래 주소를 브라우저에 붙여넣어 주세요.<br>
                    <a href="%s" style="color:#233A66;">%s</a>
                  </p>
                  <p style="font-size:13px;color:#7A879E;line-height:1.7;">
                    본인이 요청하지 않았다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않습니다.
                  </p>
                </div>
                """.formatted(resetUrl, ttlMinutes, resetUrl, resetUrl);
    }
}
