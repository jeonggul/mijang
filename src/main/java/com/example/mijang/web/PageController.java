package com.example.mijang.web;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.config.PasswordResetProperties;
import com.example.mijang.user.oauth.SocialAuthHandlers;
import com.example.mijang.user.service.LoginHintService;
import com.example.mijang.user.service.PasswordService;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** Thymeleaf 화면 라우팅 전용 컨트롤러다. */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final LoginHintService loginHint;

    private final PasswordResetProperties resetProperties;
    private final PasswordService passwordService;

    /* ── 소개 · 인증 ─────────────────────────────────────────── */

    /** 랜딩 화면을 렌더한다. {@code SR-001} */
    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    /** 로그인 화면을 렌더한다. 공개 화면이라 노출 가능한 체험 계정만 싣는다. */
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("demoAccounts", loginHint.visibleAccounts());
        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    /** 비밀번호 찾기 화면을 렌더한다. 재전송 간격 설정값을 함께 내린다. */
    @GetMapping("/password-forgot")
    public String passwordForgot(Model model) {
        model.addAttribute("resendCooldownSeconds", resetProperties.getResendCooldown().toSeconds());
        return "password-forgot";
    }

    /** 비밀번호 재설정 화면을 렌더한다. 토큰을 미리 검증해 만료 링크를 거른다. */
    @GetMapping("/password-reset")
    public String passwordReset(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("resetMinutes", resetProperties.getTokenTtl().toMinutes());
        try {
            passwordService.validateToken(token);
            model.addAttribute("token", token);
        } catch (BusinessException e) {
            // 없음·이미 씀·만료를 구분하지 않는다.
            model.addAttribute("invalid", true);
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "password-reset";
    }

    /** 소셜 계정 연결 확인 화면을 렌더한다. 검증 없는 자동 연결은 하지 않는다. {@code AUTH-07} */
    @GetMapping("/social-link")
    public String socialLink(HttpServletRequest request, Model model) {
        var pending = SocialAuthHandlers.pendingOf(request);
        if (pending == null || pending.kind() != SocialAuthHandlers.Pending.Kind.LINK) {
            return "redirect:/login";   // 직접 진입이거나 가입 보류(SIGNUP)다.
        }
        model.addAttribute("linkEmail", pending.email());
        model.addAttribute("linkProvider", "GOOGLE".equals(pending.provider()) ? "구글" : "카카오");
        return "social-link";
    }

    /** 소셜 첫 가입 화면을 렌더한다. {@code AUTH-07} */
    @GetMapping("/social-signup")
    public String socialSignup(HttpServletRequest request) {
        var pending = SocialAuthHandlers.pendingOf(request);
        if (pending == null
                || pending.kind() != SocialAuthHandlers.Pending.Kind.SIGNUP) {
            return "redirect:/login";   // 직접 진입은 보여 줄 것이 없다.
        }
        // 가입 정보는 화면이 /api/auth/social/pending 으로 받아 간다.
        return "social-signup";
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

    /** 점검 화면을 렌더한다. MaintenanceInterceptor 의 forward 대상이다. */
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

    /** 종목 상세 화면을 렌더한다. 심볼이 없으면 검색으로 보낸다. */
    @GetMapping("/stock")
    public String stock(@RequestParam(required = false) String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "redirect:/search";
        }
        return "stock";
    }

    @GetMapping("/watchlist")
    public String watchlist() {
        return "watchlist";
    }

    /** 실적·배당·거시 일정 캘린더 (INFO-05·INFO-07, 4.14). */
    @GetMapping("/calendar")
    public String calendar() {
        return "calendar";
    }

    /** 하루치 일정 전체 목록. 캘린더 칸의 "더보기"가 여기로 온다. */
    @GetMapping("/calendar/day")
    public String calendarDay(@RequestParam(required = false) String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "redirect:/calendar";
        }
        return "calendar-day";
    }

    /* ── 커뮤니티 ────────────────────────────────────────────── */

    /** 일반 커뮤니티(자유·질문) 화면을 렌더한다. */
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

    /** 게시글 상세 화면을 렌더한다. */
    @GetMapping("/community-post/{postId}")
    public String communityPost(@PathVariable Long postId, Model model) {
        model.addAttribute("postId", postId);
        return "community-post";
    }

    /** 글 번호 없는 옛 링크를 목록으로 돌려보낸다. */
    @GetMapping("/community-post")
    public RedirectView communityPostWithoutId() {
        RedirectView redirect = new RedirectView("/community");
        // 모델을 붙이지 않는다 — CSP nonce 가 쿼리스트링·리퍼러로 새는 것을 막는다.
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

    /** 종목별 글쓰기 화면을 렌더한다. */
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
