package com.example.mijang.web;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.config.PasswordResetProperties;
import com.example.mijang.user.service.PasswordService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 화면 라우팅 전용 컨트롤러.
 *
 * <p>프로토타입 26페이지를 Thymeleaf 템플릿으로 옮기면서 경로만 연결한 상태다.
 * 화면에 보이는 값은 전부 템플릿에 하드코딩되어 있으며, 아직 서비스·DB를 거치지 않는다.
 * 기능을 붙일 때 이 클래스의 메서드를 도메인별 컨트롤러로 옮기고 Model 을 채우면 된다.
 *
 * <p>오류 화면(templates/error.html)은 Spring Boot 기본 오류 뷰로 동작하므로 여기서 매핑하지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final PasswordResetProperties resetProperties;
    private final PasswordService passwordService;

    /* ── 소개 · 인증 ─────────────────────────────────────────── */

    /**
     * 서비스 진입점. 화면설계서 3장 "첫 방문 → 랜딩" 흐름이라 루트가 SR-001 이다.
     *
     * <p>인증이 붙으면(P1) 여기서 로그인 여부로 갈라 로그인 상태면 대시보드로 보낸다.
     * 지금은 인증 전이라 항상 랜딩을 렌더한다.
     */
    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    /**
     * 비밀번호 찾기 화면.
     *
     * <p>재전송 간격을 화면에 내려 준다. 화면이 따로 60 을 적어 두면 설정을 바꿨을 때
     * 두 값이 어긋나 "다 기다렸는데 또 안 온다" 가 된다.
     *
     * <p>이 값은 계정마다 다르지 않은 설정값이라 내려 줘도 가입 여부가 드러나지 않는다.
     */
    @GetMapping("/password-forgot")
    public String passwordForgot(Model model) {
        model.addAttribute("resendCooldownSeconds", resetProperties.getResendCooldown().toSeconds());
        return "password-forgot";
    }

    /**
     * 메일 링크로 들어오는 화면.
     *
     * <p>토큰을 <b>여기서 미리 확인한다.</b> 확인하지 않으면 만료된 링크로도 입력 화면이
     * 뜨고, 새 비밀번호를 다 적어 제출한 뒤에야 실패한다.
     *
     * <p>유효 시간도 함께 넘긴다. 화면이 "30분"을 글자로 들고 있으면 설정을 바꿨을 때
     * 거짓말이 된다.
     */
    @GetMapping("/password-reset")
    public String passwordReset(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("resetMinutes", resetProperties.getTokenTtl().toMinutes());
        try {
            passwordService.validateToken(token);
            model.addAttribute("token", token);
        } catch (BusinessException e) {
            // 없음·이미 씀·만료를 구분하지 않는다. 화면이 할 일은 어느 쪽이든 같다
            model.addAttribute("invalid", true);
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "password-reset";
    }

    /** 이용약관. 가입 화면에서 새 탭으로 연다. 비로그인도 볼 수 있어야 한다. */
    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    /** 개인정보 처리방침. */
    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    /**
     * 점검 화면.
     *
     * <p>{@link com.example.mijang.config.MaintenanceInterceptor} 가 화면 요청을 이리로
     * 넘긴다. 주소를 직접 쳐서 들어올 자리는 아니지만, 인터셉터가 forward 하려면
     * 매핑이 실재해야 한다.
     */
    @GetMapping("/maintenance")
    public String maintenance() {
        return "maintenance";
    }

    /* ── 대시보드 · 포트폴리오 ───────────────────────────────── */

    @GetMapping("/dashboard")
    public String dashboard() {
        return "index";
    }

    @GetMapping("/portfolio")
    public String portfolio() {
        return "portfolio";
    }

    @GetMapping("/report")
    public String report() {
        return "report";
    }

    @GetMapping("/dividend")
    public String dividend() {
        return "dividend";
    }

    @GetMapping("/tax")
    public String tax() {
        return "tax";
    }

    /* ── 매매 기록 · 회고 ────────────────────────────────────── */

    @GetMapping("/record-list")
    public String recordList() {
        return "record-list";
    }

    @GetMapping("/record-new")
    public String recordNew() {
        return "record-new";
    }

    @GetMapping("/retrospect")
    public String retrospect() {
        return "retrospect";
    }

    /* ── 종목 ────────────────────────────────────────────────── */

    @GetMapping("/search")
    public String search() {
        return "search";
    }

    @GetMapping("/stock")
    public String stock() {
        return "stock";
    }

    @GetMapping("/watchlist")
    public String watchlist() {
        return "watchlist";
    }

    /* ── 커뮤니티 ────────────────────────────────────────────── */

    /**
     * 일반 커뮤니티. 헤더 메뉴의 "커뮤니티" 가 여기로 온다.
     *
     * <p>게시판은 자유와 질문 둘이고 종목이 없다. 종목별 게시판은 아래
     * {@link #communityStock(String, Model)} 이 맡는다 — 경로를 나눠 두면 링크만 보고도
     * 어느 커뮤니티인지 알 수 있고, 화면도 종목 유무를 모델 하나로 갈라 그린다.
     *
     * @param board {@code free}(기본) 또는 {@code qna}
     */
    @GetMapping("/community")
    public String community(@RequestParam(defaultValue = "free") String board, Model model) {
        model.addAttribute("board", "qna".equalsIgnoreCase(board) ? "QNA" : "FREE");
        model.addAttribute("symbol", null);
        return "community";
    }

    /** 종목별 게시판. 종목 상세의 "커뮤니티" 메뉴와 사이드바 보유 종목이 여기로 온다. */
    @GetMapping("/community/{symbol}")
    public String communityStock(@PathVariable String symbol, Model model) {
        model.addAttribute("board", "STOCK");
        model.addAttribute("symbol", symbol.toUpperCase(Locale.ROOT));
        return "community";
    }

    /**
     * 게시글 상세.
     *
     * <p>글 번호가 경로에 있어야 링크를 복사해 남에게 보낼 수 있다. 번호 없는
     * {@code /community-post} 는 예전 링크라 목록으로 돌려보낸다.
     */
    @GetMapping("/community-post/{postId}")
    public String communityPost(@PathVariable Long postId, Model model) {
        model.addAttribute("postId", postId);
        return "community-post";
    }

    @GetMapping("/community-post")
    public RedirectView communityPostWithoutId() {
        RedirectView redirect = new RedirectView("/community");
        /* 모델을 붙이지 않는다. 기본값으로 두면 CSP nonce 가 쿼리스트링에 실려
           주소창과 리퍼러에 남는다 — 매 요청 새로 만드는 값이라 새는 것 자체가 문제다 */
        redirect.setExposeModelAttributes(false);
        return redirect;
    }

    /** 일반 커뮤니티 글쓰기. 게시판(자유·질문)을 화면에서 고른다. */
    @GetMapping("/community-write")
    public String communityWrite(Model model) {
        model.addAttribute("board", "FREE");
        model.addAttribute("symbol", null);
        return "community-write";
    }

    /**
     * 종목별 글쓰기.
     *
     * <p>게시판을 고르는 칸이 없다 — 경로가 이미 게시판을 정했다. 대신 그 종목의
     * 내 매매를 골라 본문에 카드로 붙일 수 있다.
     */
    @GetMapping("/community-write/{symbol}")
    public String communityWriteStock(@PathVariable String symbol, Model model) {
        model.addAttribute("board", "STOCK");
        model.addAttribute("symbol", symbol.toUpperCase(Locale.ROOT));
        return "community-write";
    }

    /* ── 마이페이지 · 설정 ───────────────────────────────────── */

    @GetMapping("/mypage")
    public String mypage() {
        return "mypage";
    }

    @GetMapping("/profile-edit")
    public String profileEdit() {
        return "profile-edit";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    /** 공지 상세는 일반 게시글과 달리 반응·댓글·신고 기능이 없는 읽기 전용 화면이다. */
    @GetMapping("/notices/{noticeId}")
    public String notice(@PathVariable Long noticeId, Model model) {
        model.addAttribute("noticeId", noticeId);
        return "notice-detail";
    }

    /* ── 관리자 ──────────────────────────────────────────────── */

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
