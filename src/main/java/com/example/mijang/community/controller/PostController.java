/*
 * PostController — 게시글 API
 *
 * 이 파일이 하는 일
 *   커뮤니티가 둘이라 경로도 둘이다.
 *     /api/posts                    자유 · 질문. 게시판은 파라미터가 정한다
 *     /api/stocks/{symbol}/posts    종목별. 게시판은 경로가 정한다
 *   종목별 경로에서 본문의 board 를 읽지 않는 것이 중요하다 — 읽으면 AAPL 경로로
 *   자유 게시판 글을 넣을 수 있다.
 *
 *   목록·상세·작성 모두 로그인이 필요하다 (화면설계서 2.0 — 커뮤니티는 로그인 후).
 *   전에는 이 줄이 "목록·상세는 로그인 없이" 였는데 실제 규칙과 달랐다.
 *   누가 썼는지는 요청에서 받지 않고 토큰에서 꺼낸다.
 */
package com.example.mijang.community.controller;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.response.PageResponse;
import com.example.mijang.community.domain.BoardType;
import com.example.mijang.community.dto.PostDetail;
import com.example.mijang.community.dto.PostForm;
import com.example.mijang.community.dto.PostUpdateForm;
import com.example.mijang.community.dto.ReactionForm;
import com.example.mijang.community.dto.PostSummary;
import com.example.mijang.community.service.PostService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 API. 개발명세서(API) COM-001~003 · 화면 SR-009 — 확장(부록 C)
 */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * 일반 커뮤니티 목록. {@code COM-001}
     *
     * @param board {@code FREE} 또는 {@code QNA}. {@code STOCK} 은 여기로 오면 안 된다 —
     *              종목별 목록은 종목을 알아야 하고, 이 경로에는 종목이 없다
     * @param sort  {@code NEW}(기본) 또는 {@code HOT}
     */
    @GetMapping("/api/posts")
    public ApiResponse<PageResponse<PostSummary>> list(
            @RequestParam(defaultValue = "FREE") BoardType board,
            @RequestParam(defaultValue = "NEW") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireGeneral(board);
        List<PostSummary> content = postService.listByBoard(board, sort, page, size);
        return ApiResponse.ok(
                PageResponse.of(content, page, size, postService.countByBoard(board)));
    }

    /** COM-001 종목별 게시글 목록 */
    @GetMapping("/api/stocks/{symbol}/posts")
    public ApiResponse<PageResponse<PostSummary>> listBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "NEW") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostSummary> content = postService.listBySymbol(symbol, sort, page, size);
        return ApiResponse.ok(
                PageResponse.of(content, page, size, postService.countBySymbol(symbol)));
    }

    /**
     * 일반 커뮤니티 글 작성. {@code COM-002}
     *
     * <p>게시판은 본문의 {@code board} 가 정한다. 안 적었으면 자유 게시판이다.
     */
    @PostMapping("/api/posts")
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @Valid @RequestBody PostForm form) {
        BoardType board = form.getBoard() == null ? BoardType.FREE : form.getBoard();
        requireGeneral(board);
        return ApiResponse.ok(postService.create(me.userId(), board, null, form));
    }

    /**
     * COM-002 종목별 게시글 작성
     *
     * <p>게시판은 경로가 정한다. 본문의 {@code board} 는 읽지 않는다.
     */
    @PostMapping("/api/stocks/{symbol}/posts")
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @PathVariable String symbol,
                                    @Valid @RequestBody PostForm form) {
        return ApiResponse.ok(postService.create(me.userId(), BoardType.STOCK, symbol, form));
    }

    /**
     * COM-003 게시글 상세·댓글
     *
     * <p>로그인하지 않아도 읽을 수 있다. 로그인했으면 내 글인지를 함께 알려 준다 —
     * 화면이 수정·삭제 버튼을 띄울지 정하는 데 쓴다.
     */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<PostDetail> detail(@LoginUser SessionUser me, @PathVariable Long postId) {
        return ApiResponse.ok(postService.detail(me == null ? null : me.userId(), postId));
    }

    /**
     * 좋아요·스크랩 토글. 누르면 켜지고 다시 누르면 꺼진다.
     *
     * <p>켜기·끄기를 경로로 나누지 않는다 — 화면은 지금 상태를 모른 채 누른 사실만
     * 보내고, 서버가 토글해 결과를 돌려준다. 상태 판단이 한 곳(서버)에만 있다.
     */
    @PostMapping("/api/posts/{postId}/reactions")
    public ApiResponse<PostService.ReactionState> toggleReaction(
            @LoginUser SessionUser me,
            @PathVariable Long postId,
            @Valid @RequestBody ReactionForm form) {
        return ApiResponse.ok(postService.toggleReaction(
                me.userId(), postId, form.getType().toUpperCase(java.util.Locale.ROOT)));
    }

    /** 글 수정. 제목·본문만 — 작성 시점 값은 등록 때 한 번만 기록한다(2.3). */
    @PatchMapping("/api/posts/{postId}")
    public ApiResponse<Void> update(@LoginUser SessionUser me,
                                    @PathVariable Long postId,
                                    @Valid @RequestBody PostUpdateForm form) {
        postService.update(me.userId(), postId, form.getTitle(), form.getContent());
        return ApiResponse.ok(null);
    }

    /** 글 삭제. 지우지 않고 status 만 바꾼다(2.6). */
    @DeleteMapping("/api/posts/{postId}")
    public ApiResponse<Void> delete(@LoginUser SessionUser me, @PathVariable Long postId) {
        postService.delete(me.userId(), postId);
        return ApiResponse.ok(null);
    }

    /** 이 경로가 다루는 것은 종목 없는 게시판뿐이다. */
    private static void requireGeneral(BoardType board) {
        if (board.needsSymbol()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "board");
        }
    }
}
