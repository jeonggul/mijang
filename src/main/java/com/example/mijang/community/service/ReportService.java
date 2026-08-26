package com.example.mijang.community.service;

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
        return reportMapper.findLastInsertedId();
    }
}
