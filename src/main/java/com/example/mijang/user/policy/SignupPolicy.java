package com.example.mijang.user.policy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 비밀번호 형식과 닉네임 형식·금지어 등 가입 입력 규칙을 한 곳에서 정한다. */
public final class SignupPolicy {

    private SignupPolicy() {
    }

    /** 영문과 숫자를 각각 하나 이상 포함하고 8~16자. 공백은 허용하지 않는다. */
    public static final String PASSWORD_REGEX =
            "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,16}$";
    public static final Pattern PASSWORD = Pattern.compile(PASSWORD_REGEX);
    public static final String PASSWORD_GUIDE = "영문과 숫자를 모두 포함해 8~16자";

    /** 한글·영문·숫자만 2~10자. 공백과 특수문자를 막아 사칭·혼동을 줄인다. */
    public static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
    public static final Pattern NICKNAME = Pattern.compile(NICKNAME_REGEX);
    public static final String NICKNAME_GUIDE = "한글·영문·숫자 2~10자";

    /** 이메일 길이 상한이다 — 탈퇴 시 withdrawn 접두를 붙여도 VARCHAR(255)를 넘지 않게 한다. */
    public static final int EMAIL_MAX_LENGTH = 225;

    /** 닉네임 금지어 목록이다 — 사칭·비속어 두 갈래이고 포함만 해도 막는다. */
    private static final List<String> FORBIDDEN = List.of(
            // 운영자 사칭
            "관리자", "운영자", "운영팀", "관리팀", "고객센터", "고객지원", "운영진",
            "admin", "administrator", "root", "system", "sysop", "master",
            "official", "support", "staff", "manager",
            // 서비스 사칭
            "미장", "mijang", "미장공식", "미장운영",
            // 비속어
            "시발", "씨발", "씨빨", "개새", "새끼", "병신", "지랄", "좆", "썅", "닥쳐",
            "fuck", "shit", "bitch", "asshole", "bastard", "dick", "pussy",
            // 혐오·차별
            "일베", "한남", "김치녀", "된장녀"
    );

    /** 프로필 정보 포함 검사를 적용할 최소 길이다 — 더 짧으면 우연히 겹친다. */
    private static final int MIN_PROFILE_MATCH_LENGTH = 2;

    /** 비밀번호가 닉네임이나 이메일 아이디(@ 앞부분)를 대소문자 무시하고 품고 있는지 판정한다. */
    public static boolean containsProfileInfo(String password, String nickname, String email) {
        if (password == null || password.isBlank()) {
            return false;
        }
        String lower = password.toLowerCase(Locale.ROOT);

        if (nickname != null && nickname.length() >= MIN_PROFILE_MATCH_LENGTH
                && lower.contains(nickname.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String localPart = emailLocalPart(email);
        return localPart != null && localPart.length() >= MIN_PROFILE_MATCH_LENGTH
                && lower.contains(localPart.toLowerCase(Locale.ROOT));
    }

    /** 이메일의 @ 앞부분을 꺼낸다 — @로 시작하면 아이디가 없는 것으로 본다. */
    private static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : null;
    }

    /** 형식이 맞는 비밀번호인지 판정한다. */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD.matcher(password).matches();
    }

    /** 형식이 맞는 닉네임인지 판정한다 — 금지어는 보지 않는다. */
    public static boolean isValidNicknameFormat(String nickname) {
        return nickname != null && NICKNAME.matcher(nickname).matches();
    }

    /** 금지어를 품고 있는지 대소문자 무시하고 판정한다. */
    public static boolean containsForbiddenWord(String nickname) {
        if (nickname == null) {
            return false;
        }
        String lower = nickname.toLowerCase(Locale.ROOT);
        return FORBIDDEN.stream().anyMatch(lower::contains);
    }

    /** 닉네임을 형식·금지어 기준으로 판정해 문제가 없으면 null, 있으면 사유 문구를 돌려준다 — 중복 확인은 하지 않는다. */
    public static String validateNickname(String nickname) {
        if (!isValidNicknameFormat(nickname)) {
            return NICKNAME_GUIDE + "로 입력해주세요";
        }
        if (containsForbiddenWord(nickname)) {
            return "사용할 수 없는 단어가 포함되어 있습니다";
        }
        return null;
    }
}
