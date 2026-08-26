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

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users(Long adminId, String status, String q, int limit) {
        return userMapper.findUsers(adminId, statusFilter(status), blankToNull(q), clamp(limit));
    }

    @Transactional(readOnly = true)
    public int userCount(String status, String q) {
        return userMapper.countUsers(statusFilter(status), blankToNull(q));
    }

    /**
     * 사용자를 정지하거나 해제한다.
     *
     * <p>본인과 마지막 활성 관리자는 정지할 수 없다. 상태 변경 시 토큰 세대도 올려
     * 이미 발급된 access·refresh token을 즉시 끊는다.
     */
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
