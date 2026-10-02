package com.example.mijang.community.service;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.mapper.AdminCommunityMapper;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.community.dto.ReportForm;
import com.example.mijang.community.mapper.PostMapper;
import com.example.mijang.community.mapper.ReportMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글·댓글 신고. 개발명세서(API) COM-005 — 확장(부록 C)
 *
 * <p>신고는 지우지 않는다. 관리자가 RESOLVE·REJECT 로 닫을 뿐이다 — 처리 이력이
 * 곧 운영 기록이다(2.2).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final AdminCommunityMapper adminCommunityMapper;
    private final AdminSettingService settingService;

    /**
     * 신고를 접수한다. PENDING 으로 시작한다.
     *
     * <p>같은 사람이 같은 대상을 다시 신고하면 409 — 하나면 관리자가 보기에 충분하고,
     * 여러 개를 허용하면 한 사람이 눌러 대는 것과 여럿이 신고한 것을 구분할 수 없다.
     *
     * @throws BusinessException 없는 대상(404) · 중복 신고(409)
     */
    @Transactional
    public Long create(Long userId, ReportForm form) {
        String type = form.getTargetType().toUpperCase(Locale.ROOT);

        /* 없는 글을 신고로 접수하면 관리자 목록에 열 수 없는 항목이 쌓인다 */
        if ("POST".equals(type) && postMapper.findById(form.getTargetId()) == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        if (reportMapper.countByReporterAndTarget(userId, type, form.getTargetId()) > 0) {
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_DUPLICATED);
        }
        try {
            reportMapper.insert(userId, type, form.getTargetId(),
                    form.getReason(), form.getDetail());
        } catch (DuplicateKeyException e) {
            // 확인과 저장 사이에 같은 신고가 먼저 들어왔다. uk_reports_reporter_target 이 잡는다
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_DUPLICATED);
        }
        Long id = reportMapper.findLastInsertedId();
        autoHideIfPiledUp(type, form.getTargetId());
        return id;
    }

    /**
     * 미처리 신고가 운영 설정의 기준을 넘으면 자동으로 숨긴다.
     *
     * <p>지우지 않고 상태만 내린다 — 관리자가 신고 탭에서 반려하면 그대로 돌아온다.
     * 기준이 0 이하면 기능을 꺼 둔 것으로 본다.
     *
     * <p>이미 숨겨진 글은 다시 내리지 않는다. 상태 갱신이 조건 없이 돌면 관리자가
     * 손으로 복원해 둔 글을 신고 한 건이 다시 끌어내린다.
     */
    private void autoHideIfPiledUp(String targetType, Long targetId) {
        int threshold = settingService.number(AdminSettingKey.COMMUNITY_AUTOHIDE_REPORTS);
        if (threshold <= 0) {
            return;
        }
        if (reportMapper.countPendingByTarget(targetType, targetId) < threshold) {
            return;
        }
        if ("POST".equals(targetType)) {
            postMapper.updateStatusIfPublished(targetId, "HIDDEN");
        } else if ("COMMENT".equals(targetType)) {
            adminCommunityMapper.updateCommentStatusIfPublished(targetId, "HIDDEN");
        }
    }
}
