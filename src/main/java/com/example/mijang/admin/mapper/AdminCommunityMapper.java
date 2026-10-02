package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.AdminCommentResponse;
import com.example.mijang.admin.dto.AdminPostResponse;
import com.example.mijang.admin.dto.AdminReportResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자용 게시글·댓글·신고 SQL 접근 — 커뮤니티 매퍼와 달리 숨김·삭제 상태까지 본다. */
@Mapper
public interface AdminCommunityMapper {

    /** 게시글을 최신순으로 조회한다. status·q 는 null 이면 전부다. */
    List<AdminPostResponse> findPosts(@Param("status") String status,
                                      @Param("q") String q,
                                      @Param("limit") int limit);

    /** 게시글 한 건을 조회한다. */
    AdminPostResponse findPostById(@Param("postId") Long postId);

    /** 게시글 상태를 바꾼다. 지우는 경로는 없다. */
    int updatePostStatus(@Param("postId") Long postId, @Param("status") String status);

    /** 댓글을 최신순으로 조회한다. */
    List<AdminCommentResponse> findComments(@Param("status") String status,
                                            @Param("q") String q,
                                            @Param("limit") int limit);

    /** 댓글 한 건을 조회한다. */
    AdminCommentResponse findCommentById(@Param("commentId") Long commentId);

    /** 댓글 상태를 바꾼다. */
    int updateCommentStatus(@Param("commentId") Long commentId, @Param("status") String status);

    /** 공개 상태일 때만 댓글 상태를 바꾼다 — 관리자가 복원한 댓글을 신고가 다시 끌어내리지 않게 한다. */
    int updateCommentStatusIfPublished(@Param("commentId") Long commentId,
                                       @Param("status") String status);

    /** 신고를 오래 기다린 것부터 조회한다. status 는 null 이면 전부다. */
    List<AdminReportResponse> findReports(@Param("status") String status,
                                          @Param("limit") int limit);

    /** 신고 한 건을 조회한다. */
    AdminReportResponse findReportById(@Param("reportId") Long reportId);

    /** 신고를 닫는다 — PENDING 인 것만 듣는 조건부 갱신이라 0 이면 다른 관리자가 먼저 처리한 것이다. */
    int handleReport(@Param("reportId") Long reportId,
                     @Param("status") String status,
                     @Param("adminId") Long adminId);
}
