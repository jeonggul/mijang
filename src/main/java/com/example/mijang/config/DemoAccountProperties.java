/*
 * DemoAccountProperties — 로그인 화면에 안내할 계정 목록
 *
 * 이 파일이 하는 일
 *   로그인 화면 말풍선에 띄울 계정을 담는다. 여러 개를 둘 수 있다 —
 *   관리자와 일반 회원을 오가며 확인할 일이 많다.
 *
 *   왜 설정으로 빼는가
 *     템플릿에 비밀번호를 적으면 git 에 평문으로 들어가고 커밋 히스토리에 남는다.
 *     저장소를 공개로 돌릴 가능성이 있는 한 되돌리기 어렵다. 그래서 값은
 *     application-secret.properties(미추적)에 두고, 여기서는 자리만 만든다.
 *
 *   비밀번호는 <b>비워 둘 수 있다.</b> 본인 계정처럼 남이 알면 안 되는 것은
 *   이메일만 채워 주고 비밀번호는 직접 치게 한다. 편의를 조금 덜 얻는 대신
 *   그 값이 파일에 남지 않는다.
 *
 *   목록이 비어 있으면 화면에 아이콘 자체가 뜨지 않는다. 운영에 그대로 올라가도
 *   설정을 채우지 않는 한 아무것도 새어 나가지 않는다 — 끄는 것을 잊어서 생기는
 *   사고를 막으려면 "켜야 보이는" 쪽이 안전하다.
 */
package com.example.mijang.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 로그인 화면 계정 안내. {@code mijang.demo-account.accounts[n].*} */
@Component
@ConfigurationProperties(prefix = "mijang.demo-account")
public class DemoAccountProperties {

    private List<Account> accounts = new ArrayList<>();

    /** 이메일이 있는 것만 내보낸다. 이메일 없는 줄은 눌러도 채울 것이 없다. */
    public List<Account> usable() {
        return accounts.stream().filter(Account::hasEmail).toList();
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts == null ? new ArrayList<>() : accounts;
    }

    /**
     * 계정 한 줄. 비밀번호는 없어도 된다.
     *
     * <p>따로 이름을 두지 않는다. properties 파일은 ISO-8859-1 로 읽혀 한글을 넣으면
     * 깨진다 — 이메일이 어느 계정인지 충분히 말해 주므로 그것을 그대로 쓴다.
     */
    public static class Account {

        private String email;
        /** 비워 두면 이메일만 채우고 비밀번호는 사용자가 직접 친다 */
        private String password;

        public boolean hasEmail() {
            return email != null && !email.isBlank();
        }

        public boolean hasPassword() {
            return password != null && !password.isBlank();
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
