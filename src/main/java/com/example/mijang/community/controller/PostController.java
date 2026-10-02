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
import com.example.mijang.community.dto.MyCommentResponse;
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

/** 게시글 API를 제공한다. 종목별 경로에서는 본문의 board 를 읽지 않는다. */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 일반 커뮤니티(FREE·QNA) 목록을 조회한다. */
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

    /** 종목별 게시글 목록을 조회한다. */
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

    /** 일반 커뮤니티 글을 작성한다. board 미지정 시 자유 게시판이다. */
    @PostMapping("/api/posts")
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @Valid @RequestBody PostForm form) {
        BoardType board = form.getBoard() == null ? BoardType.FREE : form.getBoard();
        requireGeneral(board);
        return ApiResponse.ok(postService.create(me.userId(), board, null, form));
    }

    /** 종목별 글을 작성한다. 게시판은 경로가 정하고 본문의 board 는 읽지 않는다. */
    @PostMapping("/api/stocks/{symbol}/posts")
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @PathVariable String symbol,
                                    @Valid @RequestBody PostForm form) {
        return ApiResponse.ok(postService.create(me.userId(), BoardType.STOCK, symbol, form));
    }

    /** 내가 쓴 글을 조회한다. 토큰이 가리키는 사용자의 것만 준다. */
    @GetMapping("/api/users/me/posts")
    public ApiResponse<PageResponse<PostSummary>> myPosts(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostSummary> content = postService.listByUser(me.userId(), page, size);
        return ApiResponse.ok(
                PageResponse.of(content, page, size, postService.countByUser(me.userId())));
    }

    /** 내가 스크랩한 글을 조회한다. */
    @GetMapping("/api/users/me/scraps")
    public ApiResponse<PageResponse<PostSummary>> myScraps(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostSummary> content = postService.listScrappedByUser(me.userId(), page, size);
        return ApiResponse.ok(
                PageResponse.of(content, page, size, postService.countScrappedByUser(me.userId())));
    }

    /** 내가 쓴 댓글을 조회한다. */
    @GetMapping("/api/users/me/comments")
    public ApiResponse<PageResponse<MyCommentResponse>> myComments(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<MyCommentResponse> content = postService.listCommentsByUser(me.userId(), page, size);
        return ApiResponse.ok(PageResponse.of(content, page, size,
                postService.countCommentsByUser(me.userId())));
    }

    /** 게시글 상세와 댓글을 조회한다. 로그인 시 내 글 여부를 함께 준다. */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<PostDetail> detail(@LoginUser SessionUser me, @PathVariable Long postId) {
        return ApiResponse.ok(postService.detail(me == null ? null : me.userId(), postId));
    }

    /** 좋아요·스크랩을 토글한다. 상태 판단은 서버가 한다. */
    @PostMapping("/api/posts/{postId}/reactions")
    public ApiResponse<PostService.ReactionState> toggleReaction(
            @LoginUser SessionUser me,
            @PathVariable Long postId,
            @Valid @RequestBody ReactionForm form) {
        return ApiResponse.ok(postService.toggleReaction(
                me.userId(), postId, form.getType().toUpperCase(java.util.Locale.ROOT)));
    }

    /** 글의 제목·본문을 수정한다. */
    @PatchMapping("/api/posts/{postId}")
    public ApiResponse<Void> update(@LoginUser SessionUser me,
                                    @PathVariable Long postId,
                                    @Valid @RequestBody PostUpdateForm form) {
        postService.update(me.userId(), postId, form.getTitle(), form.getContent());
        return ApiResponse.ok(null);
    }

    /** 글을 삭제한다. 지우지 않고 status 만 바꾼다. */
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
