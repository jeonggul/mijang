package com.example.mijang.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 로그인 화면에 안내할 데모 계정 목록 설정({@code mijang.demo-account.*})이다. 값은 미추적 설정 파일에만 둔다. */
@Component
@ConfigurationProperties(prefix = "mijang.demo-account")
public class DemoAccountProperties {

    private List<Account> accounts = new ArrayList<>();

    /* 관리자 계정 노출 스위치다. 운영 설정에서는 절대 켜면 안 된다. */
    private boolean allowAdmin = false;

    /** 이메일이 있는 계정만 골라 반환한다. */
    public List<Account> usable() {
        return accounts.stream().filter(Account::hasEmail).toList();
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts == null ? new ArrayList<>() : accounts;
    }

    public boolean isAllowAdmin() {
        return allowAdmin;
    }

    public void setAllowAdmin(boolean allowAdmin) {
        this.allowAdmin = allowAdmin;
    }

    /** 데모 계정 한 건이다. 비밀번호는 비워 둘 수 있다. */
    public static class Account {

        private String email;
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
