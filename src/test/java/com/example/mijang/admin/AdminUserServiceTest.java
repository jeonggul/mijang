package com.example.mijang.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.domain.AdminUserAccount;
import com.example.mijang.admin.dto.AdminLogResponse;
import com.example.mijang.admin.dto.AdminUserResponse;
import com.example.mijang.admin.mapper.AdminLogMapper;
import com.example.mijang.admin.mapper.AdminUserMapper;
import com.example.mijang.admin.service.AdminUserService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.JwtProperties;
import com.example.mijang.security.PasswordVersionRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdminUserServiceTest {

    private Users users;
    private Logs logs;
    private PasswordVersionRegistry versions;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        users = new Users();
        logs = new Logs();
        versions = new PasswordVersionRegistry(new JwtProperties());
        service = new AdminUserService(users, logs, versions);
    }

    @Test
    @DisplayName("목록 조건을 정규화하고 limit은 200으로 제한한다")
    void 목록조건() {
        service.users(1L, " suspended ", "  정하  ", 999);

        assertThat(users.listAdminId).isEqualTo(1L);
        assertThat(users.listStatus).isEqualTo("SUSPENDED");
        assertThat(users.listQuery).isEqualTo("정하");
        assertThat(users.listLimit).isEqualTo(200);
    }

    @Nested
    @DisplayName("사용자 상태 변경")
    class 상태변경 {

        @Test
        @DisplayName("정지하면 상태와 토큰 세대를 바꾸고 운영 로그를 남긴다")
        void 정지() {
            service.changeStatus(1L, 2L, "SUSPENDED");

            assertThat(users.updated).isEqualTo("2|ACTIVE|SUSPENDED");
            assertThat(versions.isStale(2L, 3)).isTrue();
            assertThat(versions.isStale(2L, 4)).isFalse();
            assertThat(logs.written).singleElement().asString()
                    .contains("USER_SUSPEND|USER|2").contains("사용자 (user@example.com)");
        }

        @Test
        @DisplayName("정지 해제도 이전 토큰을 재사용하지 못하게 한다")
        void 정지해제() {
            users.account = account("USER", "SUSPENDED");

            service.changeStatus(1L, 2L, "ACTIVE");

            assertThat(users.updated).isEqualTo("2|SUSPENDED|ACTIVE");
            assertThat(versions.isStale(2L, 3)).isTrue();
            assertThat(logs.written).singleElement().asString().contains("USER_RESTORE");
        }

        @Test
        @DisplayName("같은 상태 요청은 변경과 로그 없이 끝난다")
        void 같은상태() {
            service.changeStatus(1L, 2L, "ACTIVE");

            assertThat(users.updated).isNull();
            assertThat(logs.written).isEmpty();
        }

        @Test
        @DisplayName("본인 계정은 정지할 수 없다")
        void 본인보호() {
            assertError(ErrorCode.ADMIN_SELF_STATUS_CHANGE,
                    () -> service.changeStatus(1L, 1L, "SUSPENDED"));

            assertThat(users.updated).isNull();
        }

        @Test
        @DisplayName("마지막 활성 관리자는 정지할 수 없다")
        void 마지막관리자보호() {
            users.account = account("ADMIN", "ACTIVE");
            users.activeAdminIds = List.of(2L);

            assertError(ErrorCode.ADMIN_LAST_ACTIVE,
                    () -> service.changeStatus(1L, 2L, "SUSPENDED"));

            assertThat(users.updated).isNull();
        }

        @Test
        @DisplayName("탈퇴 계정은 되살리지 않는다")
        void 탈퇴계정보호() {
            users.account = account("USER", "WITHDRAWN");

            assertError(ErrorCode.ADMIN_WITHDRAWN_USER,
                    () -> service.changeStatus(1L, 2L, "ACTIVE"));
        }

        @Test
        @DisplayName("조회 뒤 상태가 바뀌었으면 충돌로 알린다")
        void 동시변경() {
            users.updateResult = 0;

            assertError(ErrorCode.ADMIN_USER_STATUS_CONFLICT,
                    () -> service.changeStatus(1L, 2L, "SUSPENDED"));

            assertThat(logs.written).isEmpty();
            assertThat(versions.isStale(2L, 3)).isFalse();
        }
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(code));
    }

    private AdminUserAccount account(String role, String status) {
        return new AdminUserAccount(2L, "user@example.com", "사용자", role, status, 3);
    }

    private static class Users implements AdminUserMapper {
        AdminUserAccount account = new AdminUserAccount(
                2L, "user@example.com", "사용자", "USER", "ACTIVE", 3);
        List<Long> activeAdminIds = List.of(1L);
        int updateResult = 1;
        String updated;
        Long listAdminId;
        String listStatus;
        String listQuery;
        int listLimit;

        @Override
        public List<AdminUserResponse> findUsers(Long adminId, String status, String q, int limit) {
            listAdminId = adminId;
            listStatus = status;
            listQuery = q;
            listLimit = limit;
            return List.of();
        }

        @Override public int countUsers(String status, String q) { return 0; }
        @Override public AdminUserAccount findAccount(Long id) { return account; }
        @Override public List<Long> lockActiveAdminIds() { return activeAdminIds; }

        @Override
        public int updateStatus(Long id, String status, String expectedStatus) {
            if (updateResult == 1) {
                updated = id + "|" + expectedStatus + "|" + status;
            }
            return updateResult;
        }
    }

    private static class Logs implements AdminLogMapper {
        final List<String> written = new ArrayList<>();

        @Override
        public int insert(Long adminId, String action, String targetType, String targetId,
                          String targetLabel, String detail, String result) {
            written.add(String.join("|", action, targetType, targetId, targetLabel, detail, result));
            return 1;
        }

        @Override public List<AdminLogResponse> findRecent(int limit) { return List.of(); }
    }
}
