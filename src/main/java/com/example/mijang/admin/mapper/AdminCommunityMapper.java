/*
 * AdminCommunityMapper — 관리자용 게시글·댓글·신고 접근
 *
 * 이 파일이 하는 일
 *   커뮤니티 매퍼와 달리 숨김·삭제 상태까지 본다. 되살리려면 보여야 한다.
 */
package com.example.mijang.admin.mapper;

import com.example.mijang.admin.dto.AdminCommentResponse;
import com.example.mijang.admin.dto.AdminPostResponse;
import com.example.mijang.admin.dto.AdminReportResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 화면의 커뮤니티 세 탭(4.5 점검 3.1).
 *
 * <p>커뮤니티 쪽 매퍼를 재사용하지 않는 이유 — 그쪽은 전부
 * {@code status = 'PUBLISHED'} 가 박혀 있다. 조건을 파라미터로 풀면 일반 화면이
 * 실수로 숨김 글을 내보낼 길이 생긴다. 보는 범위가 다르면 문도 다르게 둔다.
 */
@Mapper
public interface AdminCommunityMapper {

    /**
     * 게시글 목록. 최신순.
     *
     * @param status PUBLISHED·HIDDEN·DELETED. null 이면 전부
     * @param q      제목·작성자 닉네임 부분 일치. null 이면 전부
     */
    List<AdminPostResponse> findPosts(@Param("status") String status,
                                      @Param("q") String q,
                                      @Param("limit") int limit);

    AdminPostResponse findPostById(@Param("postId") Long postId);

    /** 상태 전환. 지우는 경로는 없다(2.6). */
    int updatePostStatus(@Param("postId") Long postId, @Param("status") String status);

    List<AdminCommentResponse> findComments(@Param("status") String status,
                                            @Param("q") String q,
                                            @Param("limit") int limit);

    AdminCommentResponse findCommentById(@Param("commentId") Long commentId);

    int updateCommentStatus(@Param("commentId") Long commentId, @Param("status") String status);

    /**
     * 신고 목록. 오래 기다린 것부터.
     *
     * @param status PENDING·RESOLVED·REJECTED. null 이면 전부
     */
    List<AdminReportResponse> findReports(@Param("status") String status,
                                          @Param("limit") int limit);

    AdminReportResponse findReportById(@Param("reportId") Long reportId);

    /**
     * 신고를 닫는다. PENDING 인 것만 듣는 조건부 갱신이다.
     *
     * @return 바뀐 행 수. 0 이면 다른 관리자가 먼저 처리했다는 뜻이다
     */
    int handleReport(@Param("reportId") Long reportId,
                     @Param("status") String status,
                     @Param("adminId") Long adminId);
}
