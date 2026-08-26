/*
 * AdminCommunityService — 게시글·댓글·신고 운영
 *
 * 이 파일이 하는 일
 *   관리자 화면의 커뮤니티 세 탭을 받친다. 게시글·댓글을 숨기고 되살리고,
 *   신고를 처리해 닫는다. 한 일은 admin_logs 에 남긴다.
 */
package com.example.mijang.admin.service;

import com.example.mijang.admin.dto.AdminCommentResponse;
import com.example.mijang.admin.dto.AdminPostResponse;
import com.example.mijang.admin.dto.AdminReportResponse;
import com.example.mijang.admin.mapper.AdminCommunityMapper;
import com.example.mijang.admin.mapper.AdminLogMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 운영. 화면 SR-013 의 게시글·댓글·신고 탭(4.5 점검 3.1).
 *
 * <p><b>지우는 경로가 없다</b>(2.6). 숨김(HIDDEN)과 복원(PUBLISHED)뿐이다 —
 * 신고·댓글이 글을 참조하고 있고, 무엇을 왜 내렸는지가 운영 기록이다.
 *
 * <p>action 값은 admin_logs 의 ENUM 그대로다. 목록에 없는 값을 넣으면 DB 가
 * 거절하고 그 실패는 삼켜진다 — 종목 토글에서 실제로 겪은 일이다(admin 2.9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommunityService {

    private static final String ACTION_POST_HIDE = "POST_HIDE";
    private static final String ACTION_POST_RESTORE = "POST_RESTORE";
    private static final String ACTION_COMMENT_HIDE = "COMMENT_HIDE";
    private static final String ACTION_COMMENT_RESTORE = "COMMENT_RESTORE";
    private static final String ACTION_REPORT_RESOLVE = "REPORT_RESOLVE";
    private static final String ACTION_REPORT_REJECT = "REPORT_REJECT";
    private static final String TARGET_POST = "POST";
    private static final String TARGET_COMMENT = "COMMENT";
    private static final String TARGET_REPORT = "REPORT";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final AdminCommunityMapper mapper;
    private final AdminLogMapper adminLogMapper;

    /** 게시글 목록. 검색 화면과 달리 숨김·삭제도 본다 — 되살리려면 보여야 한다. */
    @Transactional(readOnly = true)
    public List<AdminPostResponse> posts(String status, String q, int limit) {
        return mapper.findPosts(normalizeStatus(status), blankToNull(q), limit);
    }

    @Transactional(readOnly = true)
    public List<AdminCommentResponse> comments(String status, String q, int limit) {
        return mapper.findComments(normalizeStatus(status), blankToNull(q), limit);
    }

    /** 신고 목록. 기본은 미처리(PENDING)만 — 관리자가 볼 일은 그쪽이다. */
    @Transactional(readOnly = true)
    public List<AdminReportResponse> reports(String status, int limit) {
        return mapper.findReports("ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(),
                limit);
    }

    /**
     * 게시글 숨김·복원.
     *
     * @throws BusinessException 없는 글이면 404
     */
    @Transactional
    public void togglePost(Long adminId, Long postId, boolean hidden) {
        AdminPostResponse post = mapper.findPostById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        mapper.updatePostStatus(postId, hidden ? "HIDDEN" : "PUBLISHED");
        writeLog(adminId, hidden ? ACTION_POST_HIDE : ACTION_POST_RESTORE,
                TARGET_POST, String.valueOf(postId), post.title(),
                hidden ? "숨김" : "복원");
    }

    /**
     * 댓글 숨김·복원.
     *
     * @throws BusinessException 없는 댓글이면 404
     */
    @Transactional
    public void toggleComment(Long adminId, Long commentId, boolean hidden) {
        AdminCommentResponse comment = mapper.findCommentById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }
        mapper.updateCommentStatus(commentId, hidden ? "HIDDEN" : "PUBLISHED");
        writeLog(adminId, hidden ? ACTION_COMMENT_HIDE : ACTION_COMMENT_RESTORE,
                TARGET_COMMENT, String.valueOf(commentId), excerpt(comment.content()),
                hidden ? "숨김" : "복원");
    }

    /**
     * 신고 처리. RESOLVE 는 대상을 숨기고 닫는다. REJECT 는 그냥 닫는다.
     *
     * <p>PENDING 인 것만 닫힌다 — 조건이 붙은 갱신이라 두 관리자가 동시에 처리해도
     * 한쪽만 성공하고, 진 쪽은 이미 처리됐다는 안내를 받는다.
     *
     * @throws BusinessException 없는 신고(404) · 이미 처리된 신고(409)
     */
    @Transactional
    public void handleReport(Long adminId, Long reportId, boolean resolve) {
        AdminReportResponse report = mapper.findReportById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }
        if (mapper.handleReport(reportId, resolve ? "RESOLVED" : "REJECTED", adminId) != 1) {
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_ALREADY_HANDLED);
        }
        if (resolve) {
            /* 신고를 받아들였다는 것은 대상이 문제라는 뜻이다. 같은 트랜잭션에서 내린다 */
            if (TARGET_POST.equals(report.targetType())) {
                mapper.updatePostStatus(report.targetId(), "HIDDEN");
            } else {
                mapper.updateCommentStatus(report.targetId(), "HIDDEN");
            }
        }
        writeLog(adminId, resolve ? ACTION_REPORT_RESOLVE : ACTION_REPORT_REJECT,
                TARGET_REPORT, String.valueOf(reportId),
                report.targetType() + " #" + report.targetId(),
                report.reason() + (resolve ? " — 대상 숨김" : " — 기각"));
    }

    /** ALL 이면 전체. 그 외에는 표의 ENUM 값 그대로 받는다. */
    private static String normalizeStatus(String status) {
        return status == null || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase();
    }

    private static String blankToNull(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    /** 라벨 칸은 짧다. 원문이 사라져도 무엇이었는지 알 만큼만 남긴다(2.2). */
    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 40 ? content : content.substring(0, 40) + "…";
    }

    /** 기록이 실패해도 본 작업은 되돌리지 않는다(admin 2.3). */
    private void writeLog(Long adminId, String action, String targetType,
                          String targetId, String targetLabel, String detail) {
        try {
            adminLogMapper.insert(adminId, action, targetType, targetId,
                    targetLabel, detail, RESULT_SUCCESS);
        } catch (RuntimeException e) {
            log.warn("[운영로그] 기록 실패 — {} {}", action, targetId, e);
        }
    }
}
