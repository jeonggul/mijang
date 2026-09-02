/*
 * DemoAccountProperties — 로그인 화면에 안내할 체험 계정
 *
 * 이 파일이 하는 일
 *   처음 들어온 사람이 곧바로 둘러볼 수 있게, 로그인 화면에 계정을 하나 알려 준다.
 *
 *   왜 설정으로 빼는가
 *     템플릿에 비밀번호를 적으면 git 에 평문으로 들어가고 커밋 히스토리에 남는다.
 *     저장소를 공개로 돌릴 가능성이 있는 한 되돌리기 어렵다. 그래서 값은
 *     application-secret.properties(미추적)에 두고, 여기서는 자리만 만든다.
 *
 *   값이 비어 있으면 화면에 아이콘 자체가 뜨지 않는다. 운영에 그대로 올라가도
 *   설정을 채우지 않는 한 아무것도 새어 나가지 않는다 — 끄는 것을 잊어서 생기는
 *   사고를 막으려면 "켜야 보이는" 쪽이 안전하다.
 */
package com.example.mijang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 체험 계정 안내. {@code mijang.demo-account.*} */
@Component
@ConfigurationProperties(prefix = "mijang.demo-account")
public class DemoAccountProperties {

    private String email;
    private String password;

    /** 둘 다 채워졌을 때만 화면에 내보낸다. 반쪽짜리 안내는 없느니만 못하다. */
    public boolean isConfigured() {
        return email != null && !email.isBlank()
                && password != null && !password.isBlank();
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
