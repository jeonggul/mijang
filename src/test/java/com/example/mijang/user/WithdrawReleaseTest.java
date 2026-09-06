package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴가 이메일·소셜 슬롯을 놓아주는지 잠근다.
 *
 * <p>@Transactional 로 각 테스트가 끝나면 롤백된다 — 실제 계정을 건드리지 않는다.
 * 새 계정을 그 안에서 만들어 탈퇴시키고 확인만 한다.
 */
@SpringBootTest
@Transactional
class WithdrawReleaseTest {

    @Autowired UserMapper userMapper;
    @Autowired OAuthAccountMapper oauthMapper;
    @Autowired AuthService authService;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("탈퇴하면 이메일이 표식으로 바뀌어 원본 이메일 슬롯이 비고, 소셜 연동이 지워진다")
    void withdrawReleasesEmailAndSocial() {
        // AuthService.withdraw 는 비밀번호를 확인하므로 실제 해시로 계정을 만든다
        var insert = new UserMapper.UserInsert(
                "release@mijang.app", passwordEncoder.encode("pass1234"), "반납테스트");
        userMapper.insert(insert);
        Long id = insert.getId();
        oauthMapper.insert(id, "GOOGLE", "gid-release-1");

        assertThat(userMapper.countByEmail("release@mijang.app")).isEqualTo(1);

        // 실제 진입점: 상태·이메일 표식·소셜 삭제가 한 트랜잭션에서 함께 일어난다
        authService.withdraw(id, "pass1234");

        // 원본 이메일로는 더 이상 안 잡힌다 — 재가입 가능
        assertThat(userMapper.countByEmail("release@mijang.app")).isZero();
        // 소셜 연동은 사라진다 — 같은 소셜로 재가입 가능
        assertThat(oauthMapper.findByUser(id)).isEmpty();
        // 행은 남아 있고 이메일에 표식이 붙는다
        assertThat(userMapper.findWithdrawnEmailForTest(id))
                .isEqualTo(id + ".withdrawn.release@mijang.app");
    }

    @Test
    @DisplayName("재탈퇴해도 이메일이 이중으로 표식되지 않는다")
    void reWithdrawDoesNotDoubleTombstone() {
        var insert = new UserMapper.UserInsert("twice@mijang.app", "$2a$10$x", "이중방지");
        userMapper.insert(insert);
        Long id = insert.getId();

        assertThat(userMapper.withdraw(id)).isEqualTo(1);
        String afterFirst = userMapper.findWithdrawnEmailForTest(id);
        // 두 번째 호출은 status 가드에 걸려 0 행
        assertThat(userMapper.withdraw(id)).isZero();
        assertThat(userMapper.findWithdrawnEmailForTest(id)).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("탈퇴자의 닉네임은 재사용 가능하다 — 이미 구현된 동작 고정")
    void withdrawnNicknameIsReusable() {
        var insert = new UserMapper.UserInsert("nick@mijang.app", "$2a$10$x", "재사용닉");
        userMapper.insert(insert);
        Long id = insert.getId();
        assertThat(userMapper.countByNickname("재사용닉")).isEqualTo(1);

        userMapper.withdraw(id);

        assertThat(userMapper.countByNickname("재사용닉")).isZero();
    }
}
