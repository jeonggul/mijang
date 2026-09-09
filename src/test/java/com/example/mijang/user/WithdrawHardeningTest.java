package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.mapper.AdminUserMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.PasswordVersionRegistry;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.AuthService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴가 비밀번호 변경과 같은 급의 상태 전이인지 잠근다 — 옛 토큰 무효화, 상태·비밀번호
 * CAS, 마지막 관리자 보호. @Transactional 로 각 테스트가 롤백된다.
 */
@SpringBootTest
@Transactional
class WithdrawHardeningTest {

    @Autowired UserMapper userMapper;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PasswordVersionRegistry versions;
    @Autowired AdminUserMapper adminUserMapper;

    private Long newUser(String email, String rawPw) {
        var insert = new UserMapper.UserInsert(email, passwordEncoder.encode(rawPw), "탈퇴보강테스트");
        userMapper.insert(insert);
        return insert.getId();
    }

    @Test
    @DisplayName("탈퇴하면 옛 access 토큰이 무효가 된다 — 토큰 세대를 올려 registry 에 기록한다")
    void withdrawInvalidatesOldTokens() {
        Long id = newUser("tok@mijang.app", "pass1234");
        User before = userMapper.findById(id);
        // 탈퇴 전 세대의 토큰은 아직 유효
        assertThat(versions.isStale(id, before.passwordVersion())).isFalse();

        authService.withdraw(id, "pass1234");

        // 탈퇴 전 세대의 토큰은 이제 낡은 것으로 거부된다
        assertThat(versions.isStale(id, before.passwordVersion())).isTrue();
    }

    @Test
    @DisplayName("확인한 비밀번호와 실제 비밀번호가 어긋나면 탈퇴가 진행되지 않는다 (CAS)")
    void withdrawRequiresMatchingHashInUpdate() {
        Long id = newUser("cas@mijang.app", "pass1234");
        // 매퍼를 직접 불러 CAS 를 검증한다 — 엉뚱한 해시로는 0행
        int changed = userMapper.withdraw(id, "$2a$10$wrong.hash.value.that.will.not.match.anything");
        assertThat(changed).isZero();
        // 계정은 그대로 ACTIVE
        assertThat(userMapper.findById(id).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("유일한 활성 관리자는 탈퇴할 수 없다")
    void lastActiveAdminCannotWithdraw() {
        // 실제 DB 의 활성 관리자에 기대지 않고, 이 사용자를 관리자로 만들어 검증한다.
        Long id = newUser("admin@mijang.app", "pass1234");
        userMapper.promoteToAdminForTest(id);   // 테스트 전용 — Step 6 에서 추가

        /* 실제 DB 에 이미 다른 활성 관리자(QA 계정 등)가 있을 수 있다. 그러면 이 사용자를
           승격해도 "유일한 활성 관리자"가 아니게 되어 가드의 전제가 깨진다. 이 트랜잭션은
           테스트가 끝나면 롤백되므로, 기존 활성 관리자를 여기서 잠시 SUSPENDED 로
           돌려도 안전하다 — 다른 테스트나 실제 데이터에 영향을 남기지 않는다. */
        List<Long> activeAdminIds = adminUserMapper.lockActiveAdminIds();
        for (Long adminId : activeAdminIds) {
            if (!adminId.equals(id)) {
                adminUserMapper.updateStatus(adminId, "SUSPENDED", "ACTIVE");
            }
        }
        // 이제 이 사용자만 활성 관리자여야 한다
        assertThat(adminUserMapper.lockActiveAdminIds()).containsExactly(id);

        assertThatThrownBy(() -> authService.withdraw(id, "pass1234"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ADMIN_LAST_ACTIVE);
    }
}
