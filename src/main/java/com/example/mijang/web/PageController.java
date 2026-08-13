package com.example.mijang.web;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.config.PasswordResetProperties;
import com.example.mijang.user.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    @GetMapping("/password-forgot")
    public String passwordForgot() {
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

    /* ── 대시보드 · 포트폴리오 ───────────────────────────────── */

    @GetMapping("/dashboard")
    public String dashboard() {
        return "index";
    }

    /** 보유 종목이 없을 때의 대시보드. 빈 상태 확인용. */
    @GetMapping("/dashboard-empty")
    public String dashboardEmpty() {
        return "dashboard-empty";
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

    /** 검색 결과가 없을 때. 빈 상태 확인용. */
    @GetMapping("/search-empty")
    public String searchEmpty() {
        return "search-empty";
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

    @GetMapping("/community")
    public String community() {
        return "community";
    }

    @GetMapping("/community-post")
    public String communityPost() {
        return "community-post";
    }

    @GetMapping("/community-write")
    public String communityWrite() {
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

    /* ── 관리자 ──────────────────────────────────────────────── */

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
