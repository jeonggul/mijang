/* 공통 헤더의 사용자·장 상태·알림 데이터를 실제 API에 연결한다. */
(function () {
  var header = document.querySelector(".header");
  if (!header || header.dataset.ready) return;
  header.dataset.ready = "true";

  function data(url, options) {
    return fetch(url, options).then(function (res) {
      if (!res.ok) return null;
      return res.json();
    }).then(function (body) { return body && body.success ? body.data : null; });
  }

  function highlightNav() {
    var path = location.pathname;
    var key = path.startsWith("/portfolio") || path.startsWith("/record") || path.startsWith("/report") || path.startsWith("/retrospect") ? "portfolio"
      : path.startsWith("/search") || path.startsWith("/stock") || path.startsWith("/watchlist") ? "search"
      : path.startsWith("/community") ? "community" : path.startsWith("/dashboard") ? "dashboard" : "";
    header.querySelectorAll("[data-nav]").forEach(function (a) {
      if (a.dataset.nav === key) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
  }

  function loadUser() {
    data("/api/users/me").then(function (me) {
      if (!me) {
        var avatar = header.querySelector(".avatar-btn");
        avatar.removeAttribute("data-open");
        avatar.setAttribute("data-go", "/login");
        header.querySelector("[data-header-nickname]").textContent = "로그인";
        header.querySelector("[data-header-email]").textContent = "";
        var notificationAnchor = header.querySelector('[data-open="notif-menu"]');
        if (notificationAnchor) notificationAnchor.closest(".menu-anchor").hidden = true;
        return;
      }
      header.querySelector("[data-header-nickname]").textContent = me.nickname || "";
      header.querySelector("[data-header-email]").textContent = me.email || "";
      var baseCurrency = me.baseCurrency === "USD" ? "USD" : "KRW";
      localStorage.setItem("mijang-base-currency", baseCurrency);
      document.dispatchEvent(new CustomEvent("mijang:currency-change", { detail: baseCurrency }));
      if (me.theme && me.theme !== "SYSTEM") {
        document.documentElement.dataset.theme = me.theme.toLowerCase();
        localStorage.setItem("mijang-theme", me.theme.toLowerCase());
      }
    });
  }

  var market = { label: "장 상태 확인 중", regular: false };
  function paintMarket() {
    var box = header.querySelector("#market-status");
    if (!box) return;
    box.querySelector("[data-market-label]").textContent = market.label;
    box.querySelector("[data-market-clock]").textContent = new Intl.DateTimeFormat("ko-KR", {
      timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false
    }).format(new Date());
    box.classList.toggle("market-regular", market.regular);
    box.classList.toggle("market-closed", !market.regular);
  }
  function loadMarket() {
    data("/api/market/session").then(function (status) {
      if (!status) { market = { label: "장 상태 확인 실패", regular: false }; return; }
      market = { label: status.label, regular: status.regular };
      paintMarket();
    });
  }

  function relative(value) {
    var diff = Math.max(0, Date.now() - new Date(value + "Z").getTime());
    var min = Math.floor(diff / 60000);
    if (min < 1) return "방금 전";
    if (min < 60) return min + "분 전";
    var hour = Math.floor(min / 60);
    if (hour < 24) return hour + "시간 전";
    return Math.floor(hour / 24) + "일 전";
  }
  function loadNotifications() {
    data("/api/notifications").then(function (items) {
      var list = header.querySelector("[data-notification-list]");
      var dot = header.querySelector("[data-notif-dot]");
      if (!list) return;
      list.replaceChildren();
      if (!items || items.length === 0) {
        var empty = document.createElement("li");
        empty.className = "empty sub";
        empty.textContent = "새 알림이 없습니다";
        list.appendChild(empty);
        if (dot) dot.hidden = true;
        return;
      }
      items.forEach(function (item) {
        var li = document.createElement("li");
        var a = document.createElement("a");
        a.className = "row" + (item.read ? "" : " unread");
        a.href = item.linkUrl || (item.symbol ? "/stock?symbol=" + encodeURIComponent(item.symbol) : "/settings");
        var meta = document.createElement("span");
        meta.className = "meta";
        var kind = document.createElement("span");
        kind.className = "kind";
        kind.textContent = item.title;
        var time = document.createElement("time");
        time.dateTime = item.createdAt;
        time.textContent = relative(item.createdAt);
        meta.append(kind, time);
        var body = document.createElement("span");
        body.className = "body";
        body.textContent = item.body || item.title;
        a.append(meta, body);
        li.appendChild(a);
        list.appendChild(li);
      });
      if (dot) dot.hidden = !items.some(function (item) { return !item.read; });
    });
  }

  var readAll = header.querySelector("[data-read-all]");
  if (readAll) readAll.addEventListener("click", function () {
    data("/api/notifications/read", { method: "PATCH" }).then(loadNotifications);
  });

  highlightNav();
  loadUser();
  loadMarket();
  loadNotifications();
  paintMarket();
  setInterval(paintMarket, 1000);
  setInterval(loadMarket, 60000);
})();
