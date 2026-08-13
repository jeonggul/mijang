package com.example.mijang.user.mail;

/**
 * 재설정 링크를 실어 보내는 수단.
 *
 * <p>구현을 갈아 끼우는 것이 목적이다. 로컬에서는 로그로 남기고 배포에서는 SMTP 로 보낸다.
 * 서비스 코드는 어느 쪽인지 알 필요가 없다.
 *
 * <p>구현은 모두 {@code @Async} 다. 발송이 요청 스레드에 붙어 있으면 가입된 주소일 때만
 * 응답이 느려져 가입 여부가 시간으로 드러난다(미장-auth-구현 8.1.3).
 */
public interface MailTransport {

    /**
     * 재설정 링크를 보낸다. 호출 즉시 반환하고 실제 발송은 다른 스레드에서 끝난다.
     *
     * @param toEmail    받는 사람. 가입돼 있는 주소만 넘어온다
     * @param resetUrl   토큰이 붙은 전체 URL
     * @param ttlMinutes 링크 유효 시간(분). 본문에 그대로 적는다
     */
    void sendResetLink(String toEmail, String resetUrl, long ttlMinutes);

    /**
     * 로그에 남길 주소를 가린다. 앞 한 글자와 도메인만 남긴다.
     *
     * <p>이메일은 개인정보다. 발송 기록을 남기더라도 원본을 그대로 적지 않는다.
     */
    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(없음)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
