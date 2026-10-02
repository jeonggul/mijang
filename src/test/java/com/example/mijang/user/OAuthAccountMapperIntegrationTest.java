package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.mapper.OAuthAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 연동 목록 SQL 이 실제 MySQL 스키마에서 실행되고 record 로 매핑되는지 확인한다.
 *
 * <p><b>읽기만 한다.</b> 해제(DELETE)는 여기서 검증하지 않는다 — 실제 DB 의 연동을
 * 지우는 테스트는 개발 계정의 로그인 수단을 건드린다. 해제·멱등·본인 것만 지우는지는
 * Task 5 Step 5 의 화면 검증에서 확인한다.
 */
@SpringBootTest
class OAuthAccountMapperIntegrationTest {

    @Autowired
    private OAuthAccountMapper mapper;

    @Test
    @DisplayName("연동이 없는 회원은 빈 목록이다 — 화면이 행을 그리지 않는다")
    void 연동없음() {
        assertThat(mapper.findByUser(-1L)).isEmpty();
    }

    @Test
    @DisplayName("연동 목록이 provider 와 linkedAt 을 빠짐없이 매핑한다")
    void 목록매핑() {
        var accounts = mapper.findByUser(89L);

        assertThat(accounts).allSatisfy(account -> {
            assertThat(account.provider()).isIn("GOOGLE", "KAKAO");
            assertThat(account.linkedAt()).isNotNull();
        });
    }
}
