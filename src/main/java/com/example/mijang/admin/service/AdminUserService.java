package com.example.mijang.admin.service;

import com.example.mijang.admin.domain.AdminUserAccount;
import com.example.mijang.admin.dto.AdminUserResponse;
import com.example.mijang.admin.mapper.AdminLogMapper;
import com.example.mijang.admin.mapper.AdminUserMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.PasswordVersionRegistry;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 조회·정지·해제를 담당하는 관리자 기능. {@code ADMIN-03}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String WITHDRAWN = "WITHDRAWN";
    private static final String ADMIN = "ADMIN";
    private static final String TARGET_USER = "USER";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final AdminUserMapper userMapper;
    private final AdminLogMapper adminLogMapper;
    private final PasswordVersionRegistry versions;

    /** 사용자 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> users(Long adminId, String status, String q, int limit) {
        return userMapper.findUsers(adminId, statusFilter(status), blankToNull(q), clamp(limit));
    }

    /** 같은 조건의 사용자 전체 건수를 돌려준다. */
    @Transactional(readOnly = true)
    public int userCount(String status, String q) {
        return userMapper.countUsers(statusFilter(status), blankToNull(q));
    }

    /** 사용자를 정지·해제한다 — 본인과 마지막 활성 관리자는 정지할 수 없고, 토큰 세대를 올려 기존 토큰을 끊는다. */
    @Transactional
    public void changeStatus(Long adminId, Long userId, String requestedStatus) {
        String nextStatus = mutableStatus(requestedStatus);
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_STATUS_CHANGE);
        }

        List<Long> activeAdminIds = userMapper.lockActiveAdminIds();
        AdminUserAccount target = userMapper.findAccount(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (WITHDRAWN.equals(target.status())) {
            throw new BusinessException(ErrorCode.ADMIN_WITHDRAWN_USER);
        }
        if (nextStatus.equals(target.status())) {
            return;
        }
        if (ADMIN.equals(target.role()) && ACTIVE.equals(target.status())
                && SUSPENDED.equals(nextStatus) && activeAdminIds.size() <= 1) {
            throw new BusinessException(ErrorCode.ADMIN_LAST_ACTIVE);
        }

        int changed = userMapper.updateStatus(target.id(), nextStatus, target.status());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.ADMIN_USER_STATUS_CONFLICT);
        }

        versions.record(target.id(), target.passwordVersion() + 1);
        writeLog(adminId, nextStatus, target);
    }

    /** 관리자를 일반 사용자로 내린다 — 본인·마지막 활성 관리자는 못 내리고, 토큰 세대를 올려 기존 토큰을 끊는다. */
    @Transactional
    public void demote(Long adminId, Long userId) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_STATUS_CHANGE);
        }

        List<Long> activeAdminIds = userMapper.lockActiveAdminIds();
        AdminUserAccount target = userMapper.findAccount(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (WITHDRAWN.equals(target.status())) {
            throw new BusinessException(ErrorCode.ADMIN_WITHDRAWN_USER);
        }
        if (!ADMIN.equals(target.role())) {
            return;   // 이미 일반 사용자다. 다시 눌러도 같은 결과여야 한다
        }
        if (ACTIVE.equals(target.status()) && activeAdminIds.size() <= 1) {
            throw new BusinessException(ErrorCode.ADMIN_LAST_ACTIVE);
        }

        int changed = userMapper.updateRole(target.id(), "USER", ADMIN);
        if (changed != 1) {
            throw new BusinessException(ErrorCode.ADMIN_USER_STATUS_CONFLICT);
        }

        versions.record(target.id(), target.passwordVersion() + 1);
        writeRoleLog(adminId, target);
    }

    private String statusFilter(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return ACTIVE.equals(normalized) || SUSPENDED.equals(normalized) || WITHDRAWN.equals(normalized)
                ? normalized : null;
    }

    private String mutableStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !SUSPENDED.equals(normalized)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "status");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    /** 권한 변경 로그를 남긴다. 실패해도 본 작업은 되돌리지 않는다. */
    private void writeRoleLog(Long adminId, AdminUserAccount target) {
        try {
            adminLogMapper.insert(adminId, "USER_DEMOTE", TARGET_USER, String.valueOf(target.id()),
                    target.nickname() + " (" + target.email() + ")",
                    "관리자 권한 해제", RESULT_SUCCESS);
        } catch (RuntimeException e) {
            log.warn("[운영로그] 기록 실패 — USER_DEMOTE {}", target.id(), e);
        }
    }

    /** 상태 변경 로그를 남긴다. 실패해도 본 작업은 되돌리지 않는다. */
    private void writeLog(Long adminId, String status, AdminUserAccount target) {
        String action = ACTIVE.equals(status) ? "USER_RESTORE" : "USER_SUSPEND";
        try {
            adminLogMapper.insert(adminId, action, TARGET_USER, String.valueOf(target.id()),
                    target.nickname() + " (" + target.email() + ")",
                    ACTIVE.equals(status) ? "계정 정지 해제" : "계정 정지",
                    RESULT_SUCCESS);
        } catch (RuntimeException e) {
            log.warn("[운영로그] 기록 실패 — {} {}", action, target.id(), e);
        }
    }
}
