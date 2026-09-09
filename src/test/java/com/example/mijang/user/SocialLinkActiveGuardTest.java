package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한(비활성) 사용자에게는 소셜 연동이 새로 붙지 않는지 잠근다(4.13 #4).
 * 붙으면 그 유령 연동이 uk_oauth_provider_user 를 점유해 같은 소셜 재가입을 막는다.
 */
@SpringBootTest
@Transactional
class SocialLinkActiveGuardTest {

    @Autowired UserMapper userMapper;
    @Autowired OAuthAccountMapper oauthMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("ACTIVE 사용자에는 소셜 연동이 붙는다")
    void insertsForActiveUser() {
        var insert = new UserMapper.UserInsert("active-link@mijang.app", passwordEncoder.encode("pass1234"), "활성연동");
        userMapper.insert(insert);
        int rows = oauthMapper.insert(insert.getId(), "GOOGLE", "gid-active-1");
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 사용자에는 소셜 연동이 붙지 않는다")
    void skipsWithdrawnUser() {
        var insert = new UserMapper.UserInsert("gone-link@mijang.app", passwordEncoder.encode("pass1234"), "탈퇴연동");
        userMapper.insert(insert);
        Long id = insert.getId();
        userMapper.withdraw(id, insert.getPasswordHash());   // Task 1 의 2인자 withdraw

        int rows = oauthMapper.insert(id, "GOOGLE", "gid-gone-1");
        assertThat(rows).isZero();
        assertThat(oauthMapper.findByUser(id)).isEmpty();
    }
}
