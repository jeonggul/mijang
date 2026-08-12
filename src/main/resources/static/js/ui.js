/* ==========================================================================
   미장 — 공통 인터랙션
   페이지마다 필요한 것만 알아서 붙는다. 없으면 그냥 넘어간다.
   ========================================================================== */

/* ── 다크모드 ─────────────────────────────────────────────────
   헤더와 설정 두 곳에서만 전환한다. 랜딩·인증·관리자에는 두지 않는다. */
(function () {
  var root = document.documentElement;
  // 소개 화면처럼 테마가 고정된 페이지는 저장값을 무시한다
  var saved = localStorage.getItem("mijang-theme");
  if (saved && !root.hasAttribute("data-theme-lock")) root.dataset.theme = saved;

  document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var dark = root.dataset.theme === "dark" ||
        (!root.dataset.theme && matchMedia("(prefers-color-scheme: dark)").matches);
      root.dataset.theme = dark ? "light" : "dark";
      localStorage.setItem("mijang-theme", root.dataset.theme);
    });
  });
})();

/* ── 탭 ──────────────────────────────────────────────────────
   [role=tablist] 안의 버튼 → aria-controls 로 패널을 전환한다. */
(function () {
  document.querySelectorAll('[role="tablist"]').forEach(function (list) {
    list.addEventListener("click", function (e) {
      var btn = e.target.closest('[role="tab"]');
      if (!btn) return;
      list.querySelectorAll('[role="tab"]').forEach(function (t) {
        var on = t === btn;
        t.setAttribute("aria-selected", String(on));
        var panel = document.getElementById(t.getAttribute("aria-controls"));
        if (panel) panel.hidden = !on;
      });
    });
  });
})();

/* ── 단순 토글 그룹 (통화·필터) ─────────────────────────────── */
(function () {
  document.querySelectorAll("[data-toggle-group]").forEach(function (group) {
    group.addEventListener("click", function (e) {
      var btn = e.target.closest("button");
      if (!btn) return;
      group.querySelectorAll("button").forEach(function (b) {
        b.setAttribute("aria-pressed", String(b === btn));
      });
      group.dispatchEvent(new CustomEvent("mj:change", { detail: btn.dataset.value }));
    });
  });
})();

/* ── 모달 · 드롭다운 ─────────────────────────────────────────
   [data-open="아이디"] 로 열고, 딤 클릭·ESC·[data-close] 로 닫는다. */
(function () {
  function close(el) { el.hidden = true; }

  document.addEventListener("click", function (e) {
    var opener = e.target.closest("[data-open]");
    if (opener) {
      var t = document.getElementById(opener.dataset.open);
      // 하나를 열면 나머지 드롭다운은 닫는다
      document.querySelectorAll(".dropdown:not([hidden])").forEach(function (d) {
        if (d !== t) close(d);
      });
      if (t) t.hidden = !t.hidden;
      return;
    }
    var closer = e.target.closest("[data-close]");
    if (closer) { close(closer.closest(".overlay, .dropdown")); return; }

    // 딤 영역 클릭
    if (e.target.classList.contains("overlay")) { close(e.target); return; }

    // 드롭다운 바깥 클릭
    document.querySelectorAll(".dropdown:not([hidden])").forEach(function (d) {
      if (!d.contains(e.target)) close(d);
    });
  });

  document.addEventListener("keydown", function (e) {
    if (e.key !== "Escape") return;
    document.querySelectorAll(".overlay:not([hidden]), .dropdown:not([hidden])").forEach(close);
  });
})();

/* ── 랜딩 데모 캔들 ───────────────────────────────────────────
   장식용. 실데이터가 아니며 상승 추세를 보여주기 위한 것이다. */
(function () {
  var g = document.getElementById("demo-candles");
  if (!g) return;

  var N = 42, W = 516, H = 320, slot = W / N, bw = Math.floor(slot * 0.62);
  var bars = [], prev = 146;
  for (var i = 0; i < N; i++) {
    var t = i / (N - 1);
    var base = 146 + 72 * Math.pow(t, 1.12);
    var wave = 7.6 * Math.sin(i * .42) + 4.3 * Math.sin(i * 1.07)
      + 2.5 * Math.sin(i * 2.63) + 1.7 * Math.cos(i * 3.91);
    var close = base + wave, open = prev;
    var amp = 1.1 + 1.9 * Math.abs(Math.sin(i * 1.37));
    bars.push({ o: open, c: close, h: Math.max(open, close) + amp, l: Math.min(open, close) - amp * .92 });
    prev = close;
  }
  var max = Math.max.apply(null, bars.map(function (b) { return b.h; }));
  var min = Math.min.apply(null, bars.map(function (b) { return b.l; }));
  var pad = (max - min) * .06;
  var y = function (v) { return ((max + pad - v) / (max + pad - (min - pad)) * H).toFixed(1); };

  g.innerHTML = bars.map(function (b, i) {
    var cx = (i * slot + slot / 2).toFixed(1);
    var up = b.c >= b.o;                       // 상승 빨강 / 하락 파랑 — 국내 관례
    var col = up ? "var(--rise)" : "var(--fall)";
    var top = parseFloat(y(Math.max(b.o, b.c)));
    var bot = parseFloat(y(Math.min(b.o, b.c)));
    return '<line x1="' + cx + '" y1="' + y(b.h) + '" x2="' + cx + '" y2="' + y(b.l) +
      '" stroke="' + col + '" stroke-width="1.7"/>' +
      '<rect x="' + (cx - bw / 2).toFixed(1) + '" y="' + top.toFixed(1) +
      '" width="' + bw + '" height="' + Math.max(2.5, bot - top).toFixed(1) +
      '" rx="1.2" fill="' + col + '"/>';
  }).join("");
})();

/* ── 로그아웃 ────────────────────────────────────────────────
   쿠키가 HttpOnly 라 JS 가 지울 수 없다. 서버에 요청해야만 지워진다. */
(function () {
  document.addEventListener("click", function (e) {
    /* 위임으로 건다 — 헤더가 20개 템플릿에 복제돼 있어 페이지마다 붙이면 빠뜨린다 */
    if (!e.target.closest("[data-logout]")) return;
    fetch("/api/auth/logout", { method: "POST" }).then(function () {
      location.href = "/login";
    });
  });
})();
