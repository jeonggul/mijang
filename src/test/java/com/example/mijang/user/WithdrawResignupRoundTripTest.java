package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 후 같은 이메일로 실제로 재가입이 되는지 끝까지 확인한다(4.13 #8).
 * 슬롯이 비는 것뿐 아니라 새 INSERT 가 성공하는 것까지 본다. @Transactional 로 롤백.
 */
@SpringBootTest
@Transactional
class WithdrawResignupRoundTripTest {

    @Autowired UserMapper userMapper;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("탈퇴 후 같은 이메일로 재가입하면 새 계정(다른 id)이 만들어진다")
    void resignupSameEmailSucceeds() {
        String email = "roundtrip@mijang.app";
        var insert = new UserMapper.UserInsert(email, passwordEncoder.encode("pass1234"), "왕복테스트");
        userMapper.insert(insert);
        Long oldId = insert.getId();

        authService.withdraw(oldId, "pass1234");

        var form = new SignupForm();
        form.setEmail(email);
        form.setPassword("newpass12");
        form.setNickname("재가입닉");
        Long newId = authService.signup(form);

        assertThat(newId).isNotNull().isNotEqualTo(oldId);
        // 옛 글이 새 사람 것이 되지 않는다 — 완전히 새 id
        assertThat(userMapper.findById(newId).email()).isEqualTo(email);
    }
}
