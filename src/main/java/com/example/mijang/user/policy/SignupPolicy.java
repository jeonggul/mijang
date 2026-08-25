/*
 * SignupPolicy — 가입 입력 규칙
 *
 * 이 파일이 하는 일
 *   비밀번호가 어때야 하는지, 닉네임에 무엇을 쓸 수 있는지를 한 곳에서 정한다.
 *   가입·재설정·변경 세 군데가 같은 규칙을 봐야 하는데, 각자 적으면
 *   언젠가 한 곳만 느슨해진다.
 */
package com.example.mijang.user.policy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 가입 입력 규칙. 비밀번호 형식과 닉네임 형식·금지어를 한 곳에서 정한다.
 *
 * <p>화면에도 같은 규칙을 두지만 <b>판정의 근거는 여기다.</b> 화면 검사는 사용자를 돕기 위한 것이고,
 * 브라우저 개발자 도구로 얼마든지 우회된다. 서버가 다시 보지 않으면 규칙이 없는 것과 같다.
 */
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

    /**
     * 닉네임 금지어.
     *
     * <p>두 갈래다 — <b>사칭</b>(운영자로 오인시키는 말)과 <b>비속어</b>.
     * 포함만 해도 막는다. "관리자1" 같은 우회를 열어 두면 금지어를 두는 의미가 없다.
     *
     * <p>완전한 목록은 만들 수 없다. 신고(COM-005)로 사후 대응하는 것이 전제이고,
     * 이 목록은 가장 흔한 것만 앞에서 걸러 준다.
     */
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

    /**
     * 닉네임/이메일 아이디가 너무 짧으면 우연히 겹친다. 의미 있는 길이부터만 본다.
     *
     * <p>2자 미만을 검사하면 닉네임이 "김" 인 사람은 비밀번호에 "김" 을 못 쓴다.
     */
    private static final int MIN_PROFILE_MATCH_LENGTH = 2;

    /**
     * 비밀번호가 닉네임이나 이메일 아이디를 품고 있는가.
     *
     * <p>형식 규칙만으로는 {@code mijang12} 같은 값을 막지 못한다. 형식은 통과하지만
     * <b>공개된 정보로 만든 비밀번호</b>다. 닉네임은 커뮤니티에 그대로 노출되고
     * 이메일 아이디도 알아내기 어렵지 않아, 둘 다 시도 목록의 맨 앞에 온다.
     *
     * <p>대소문자를 구분하지 않는다. {@code Mijang12} 를 다르게 보면 막는 의미가 없다.
     *
     * @param email 전체 주소. {@code @} 앞부분만 본다 — 뒷부분은 도메인이라 개인 정보가 아니다
     */
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

    /** {@code @} 앞부분. {@code @} 로 시작하는 값은 아이디가 없는 것으로 본다. */
    private static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : null;
    }

    /** 형식이 맞는 비밀번호인가. */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD.matcher(password).matches();
    }

    /** 형식이 맞는 닉네임인가. 금지어는 보지 않는다. */
    public static boolean isValidNicknameFormat(String nickname) {
        return nickname != null && NICKNAME.matcher(nickname).matches();
    }

    /**
     * 금지어를 품고 있는가.
     *
     * <p>대소문자를 구분하지 않는다. {@code Admin} 과 {@code admin} 을 다르게 보면 막는 의미가 없다.
     */
    public static boolean containsForbiddenWord(String nickname) {
        if (nickname == null) {
            return false;
        }
        String lower = nickname.toLowerCase(Locale.ROOT);
        return FORBIDDEN.stream().anyMatch(lower::contains);
    }

    /**
     * 닉네임을 형식·금지어 기준으로 판정한다. 중복 확인은 여기서 하지 않는다(DB 가 필요하다).
     *
     * @return 문제가 없으면 null, 있으면 사용자에게 보여줄 사유
     */
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
