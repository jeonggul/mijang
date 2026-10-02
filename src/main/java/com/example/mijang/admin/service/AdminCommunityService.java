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

/** 커뮤니티 운영 서비스 — 게시글·댓글 숨김·복원과 신고 처리를 하고 admin_logs 에 남긴다. 지우는 경로는 없다. */
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

    /** 게시글 목록을 조회한다 — 숨김·삭제 상태까지 본다. */
    @Transactional(readOnly = true)
    public List<AdminPostResponse> posts(String status, String q, int limit) {
        return mapper.findPosts(normalizeStatus(status), blankToNull(q), limit);
    }

    /** 댓글 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<AdminCommentResponse> comments(String status, String q, int limit) {
        return mapper.findComments(normalizeStatus(status), blankToNull(q), limit);
    }

    /** 신고 목록을 조회한다. ALL 이면 전체다. */
    @Transactional(readOnly = true)
    public List<AdminReportResponse> reports(String status, int limit) {
        return mapper.findReports("ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(),
                limit);
    }

    /** 게시글을 숨기거나 복원한다. 없는 글이면 404 다. */
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

    /** 댓글을 숨기거나 복원한다. 없는 댓글이면 404 다. */
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

    /** 신고를 처리한다 — RESOLVE 는 대상을 숨기고 닫고 REJECT 는 그냥 닫는다. 이미 처리됐으면 409 다. */
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
            // 받아들인 신고의 대상은 같은 트랜잭션에서 숨긴다
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

    /** ALL 이면 null(전체), 그 외에는 대문자 ENUM 값으로 다듬는다. */
    private static String normalizeStatus(String status) {
        return status == null || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase();
    }

    private static String blankToNull(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    /** 라벨 칸에 넣을 앞부분 40자를 만든다. */
    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 40 ? content : content.substring(0, 40) + "…";
    }

    /** 운영 로그를 남긴다. 실패해도 본 작업은 되돌리지 않는다. */
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
