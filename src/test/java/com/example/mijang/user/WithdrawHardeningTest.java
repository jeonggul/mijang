package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.mapper.AdminUserMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.PasswordVersionRegistry;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.OAuthAccountMapper;
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
    @Autowired OAuthAccountMapper oauthMapper;

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
    @DisplayName("서비스 계층에서 비밀번호가 틀리면 CAS UPDATE 까지 가지 않고 소셜 연동도 그대로 남는다")
    void withdrawServiceLayerRejectsBeforeTouchingOauth() {
        Long id = newUser("service-cas@mijang.app", "pass1234");
        oauthMapper.insert(id, "GOOGLE", "gid-service-cas-1");

        // AuthService.withdraw 는 비밀번호 확인에서 먼저 걸린다 — CAS UPDATE·oauth 삭제
        // 어느 쪽도 실행되지 않아야 한다. withdrawRequiresMatchingHashInUpdate 는 매퍼를
        // 직접 불러 CAS 만 보므로, 여기서는 서비스 진입점을 그대로 탄다
        assertThatThrownBy(() -> authService.withdraw(id, "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_MISMATCH);

        assertThat(userMapper.findById(id).status()).isEqualTo("ACTIVE");
        // 소셜 연동이 지워지지 않고 그대로 남아 있다 — CAS 실패 뒤 oauth 삭제로 넘어가지 않았다
        assertThat(oauthMapper.findByUser(id)).isNotEmpty();
    }

    @Test
    @DisplayName("다른 활성 관리자가 있으면 관리자도 탈퇴할 수 있다")
    void adminCanWithdrawWhenOtherActiveAdminsExist() {
        Long id = newUser("admin-not-last@mijang.app", "pass1234");
        userMapper.promoteToAdminForTest(id);

        /* 이 테스트는 "유일한 활성 관리자가 아니다" 를 전제한다. 실제 DB 에 이미 다른
           활성 관리자가 있어야 하고, lastActiveAdminCannotWithdraw 와 달리 여기서는
           그들을 정지시키지 않는다 — 오히려 그대로 둬야 검증하려는 상황(다른 관리자가
           있을 때는 막지 않는다)이 만들어진다. 전제가 깨지면(활성 관리자가 이 사용자
           하나뿐이면) 통과가 아니라 실패로 드러나야 하므로 스킵 대신 명시적으로 확인한다 */
        List<Long> activeAdminIds = adminUserMapper.lockActiveAdminIds();
        assertThat(activeAdminIds)
                .as("이 테스트는 실제 DB 에 이 사용자 말고도 활성 관리자가 있어야 전제가 선다")
                .hasSizeGreaterThan(1);

        authService.withdraw(id, "pass1234");

        // 예외 없이 끝났고, 실제로 탈퇴됐다 — findById 는 WITHDRAWN 을 걸러 내므로 null 이면 확인된 것
        assertThat(userMapper.findById(id)).isNull();
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
