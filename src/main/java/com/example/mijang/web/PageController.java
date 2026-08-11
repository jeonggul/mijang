package com.example.mijang.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
public class PageController {

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

    @GetMapping("/password-reset")
    public String passwordReset() {
        return "password-reset";
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
