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

/** 게시글·댓글 신고를 접수하고 기준을 넘으면 자동 숨김한다. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final AdminCommunityMapper adminCommunityMapper;
    private final AdminSettingService settingService;

    /** 신고를 PENDING 으로 접수한다. 같은 대상 중복 신고는 409 다. */
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

    /** 미처리 신고가 기준을 넘으면 자동으로 숨긴다. 기준이 0 이하면 꺼 둔 것으로 본다. */
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
