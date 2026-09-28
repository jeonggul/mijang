package com.example.mijang.user.mail;

/** 비밀번호 재설정 링크를 실어 보내는 전송 수단이다 — 구현은 모두 @Async여야 가입 여부가 응답 시간으로 새지 않는다. */
public interface MailTransport {

    /** 재설정 링크를 보낸다 — 호출 즉시 반환하고 실제 발송은 다른 스레드에서 끝난다. */
    void sendResetLink(String toEmail, String resetUrl, long ttlMinutes);

    /** 로그용으로 이메일을 가린다 — 앞 한 글자와 도메인만 남긴다. */
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
