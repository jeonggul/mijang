(function () {
  function request(url, options) {
    return fetch(url, options).then(function (res) {
      if (res.status === 401) { location.href = "/login"; return null; }
      return res.json();
    });
  }
  function api(url, options) {
    return request(url, options).then(function (body) { return body && body.success ? body.data : null; });
  }
  function choose(group, value) {
    if (!group) return;
    group.querySelectorAll("button").forEach(function (button) {
      button.setAttribute("aria-pressed", String(button.dataset.value === String(value)));
    });
  }
  function formatDate(value) {
    if (!value) return "기록 없음";
    return "마지막 변경 " + new Intl.DateTimeFormat("ko-KR", { dateStyle: "long" }).format(new Date(value + "Z"));
  }

  api("/api/users/me").then(function (me) {
    if (!me) return;
    document.getElementById("setting-email").textContent = me.email;
    document.getElementById("password-changed-at").textContent = formatDate(me.passwordChangedAt);
    choose(document.querySelector("[data-base-currency]"), me.baseCurrency);
    choose(document.querySelector("[data-theme-pref]"), me.theme);
  });

  /* 소셜 연동 — 연동한 것만 행으로 그린다.
     하나도 없으면 아무것도 그리지 않는다. 연동은 선택이라 없는 것이 정상이다. */
  var PROVIDER_NAMES = { GOOGLE: "구글", KAKAO: "카카오" };
  var unlinkTarget = null;

  function formatLinkedAt(value) {
    return new Intl.DateTimeFormat("ko-KR", { dateStyle: "long" })
      .format(new Date(value + "Z")) + " 연동";
  }

  function paintSocialAccounts(accounts) {
    var host = document.getElementById("social-rows");
    if (!host) return;
    host.replaceChildren();
    if (!accounts || !accounts.length) return;

    accounts.forEach(function (account, index) {
      var row = document.createElement("div");
      row.className = "setting-row";

      var txt = document.createElement("span");
      txt.className = "txt";
      var label = document.createElement("b");
      /* 라벨은 첫 줄에만 둔다. 줄마다 "연결된 계정" 이 반복되면 목록으로 읽히지 않는다 */
      label.textContent = index === 0 ? "연결된 계정" : "";
      var detail = document.createElement("small");
      detail.textContent = (PROVIDER_NAMES[account.provider] || account.provider)
        + " · " + formatLinkedAt(account.linkedAt);
      txt.appendChild(label);
      txt.appendChild(detail);

      var val = document.createElement("span");
      val.className = "val";
      var button = document.createElement("button");
      button.className = "btn btn-sm";
      button.type = "button";
      button.textContent = "해제";
      button.addEventListener("click", function () {
        unlinkTarget = account.provider;
        var name = PROVIDER_NAMES[account.provider] || account.provider;
        document.getElementById("um-t").textContent = name + " 연동 해제";
        document.getElementById("um-desc").textContent =
          name + " 계정 연결을 끊습니다. 계정과 기록은 그대로 남고, 언제든 다시 연동할 수 있습니다.";
        document.getElementById("um-msg").hidden = true;
        document.getElementById("unlink-modal").hidden = false;
      });
      val.appendChild(button);

      row.appendChild(txt);
      row.appendChild(val);
      host.appendChild(row);
    });
  }

  function loadSocialAccounts() {
    api("/api/users/me/social").then(function (accounts) {
      if (accounts) paintSocialAccounts(accounts);
    });
  }
  loadSocialAccounts();

  /* 해제만 api() 를 쓰지 않고 fetch 를 직접 쓴다. api() 는 실패도 null 로 돌려주는데
     이 API 는 성공해도 data 가 null 이라, 둘을 구분할 수 없다.
     커뮤니티의 신고 버튼이 같은 이유로 fetch 를 직접 쓴다(community.js) */
  document.getElementById("um-submit").addEventListener("click", function () {
    if (!unlinkTarget) return;
    var button = this;
    button.disabled = true;
    fetch("/api/users/me/social/" + unlinkTarget, { method: "DELETE" })
      .then(function (res) {
        button.disabled = false;
        if (res.status === 401) { location.href = "/login"; return; }
        if (!res.ok) {
          var msg = document.getElementById("um-msg");
          msg.textContent = "해제에 실패했습니다";
          msg.hidden = false;
          return;
        }
        unlinkTarget = null;
        document.querySelector("#unlink-modal [data-close]").click();
        loadSocialAccounts();
      })
      .catch(function () {
        button.disabled = false;
        var msg = document.getElementById("um-msg");
        msg.textContent = "일시적인 오류가 발생했습니다";
        msg.hidden = false;
      });
  });

  [{ selector: "[data-base-currency]", field: "baseCurrency" }, { selector: "[data-theme-pref]", field: "theme" }]
    .forEach(function (item) {
      var group = document.querySelector(item.selector);
      if (!group) return;
      group.addEventListener("mj:change", function (event) {
        var payload = {};
        payload[item.field] = event.detail;
        api("/api/users/me", { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) })
          .then(function (saved) {
            if (!saved) return;
            if (item.field === "baseCurrency") {
              localStorage.setItem("mijang-base-currency", saved.baseCurrency);
              document.dispatchEvent(new CustomEvent("mijang:currency-change", { detail: saved.baseCurrency }));
              return;
            }
            if (item.field !== "theme") return;
            if (event.detail === "SYSTEM") {
              delete document.documentElement.dataset.theme;
              localStorage.removeItem("mijang-theme");
            } else {
              document.documentElement.dataset.theme = event.detail.toLowerCase();
              localStorage.setItem("mijang-theme", event.detail.toLowerCase());
            }
          });
      });
    });

  var settings = null;
  function paintNotificationSettings(value) {
    settings = value;
    document.getElementById("noti-target").checked = value.targetPriceEnabled;
    document.getElementById("noti-volatility").checked = value.volatilityEnabled;
    document.getElementById("noti-dividend").checked = value.dividendEnabled;
    document.getElementById("noti-news").checked = value.newsEnabled;
    choose(document.querySelector("[data-noti-threshold]"), Number(value.volatilityThreshold).toFixed(2));
  }
  function saveNotificationSettings() {
    if (!settings) return;
    var payload = {
      targetPriceEnabled: document.getElementById("noti-target").checked,
      volatilityEnabled: document.getElementById("noti-volatility").checked,
      volatilityThreshold: Number(settings.volatilityThreshold),
      dividendEnabled: document.getElementById("noti-dividend").checked,
      newsEnabled: document.getElementById("noti-news").checked
    };
    api("/api/notifications/settings", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }).then(function (saved) {
      if (!saved) return;
      paintNotificationSettings(saved);
      var state = document.getElementById("noti-save-state");
      state.textContent = "저장했습니다"; state.className = "msg ok"; state.hidden = false;
      setTimeout(function () { state.hidden = true; }, 1200);
    });
  }
  api("/api/notifications/settings").then(function (value) { if (value) paintNotificationSettings(value); });
  ["noti-target", "noti-volatility", "noti-dividend", "noti-news"].forEach(function (id) {
    document.getElementById(id).addEventListener("change", saveNotificationSettings);
  });
  var threshold = document.querySelector("[data-noti-threshold]");
  threshold.addEventListener("click", function (event) {
    var button = event.target.closest("button");
    if (!button || !settings) return;
    settings = Object.assign({}, settings, { volatilityThreshold: Number(button.dataset.value) });
    choose(threshold, Number(button.dataset.value).toFixed(2));
    saveNotificationSettings();
  });

  function loadNotices() {
    api("/api/support/notices").then(function (items) {
      var list = document.getElementById("notice-list"); list.replaceChildren();
      document.getElementById("notice-count").textContent = (items || []).length + "건";
      if (!items || items.length === 0) { var e = document.createElement("li"); e.className = "sub"; e.style.cssText = "padding:22px;text-align:center"; e.textContent = "등록된 공지가 없습니다"; list.appendChild(e); return; }
      items.forEach(function (notice) {
        var li = document.createElement("li"), a = document.createElement("a");
        a.className = "row row-between"; a.href = "/notices/" + notice.id;
        var title = document.createElement("b"); title.textContent = (notice.pinned ? "[고정] " : "") + notice.title;
        var date = document.createElement("time"); date.className = "note"; date.textContent = new Intl.DateTimeFormat("ko-KR").format(new Date(notice.createdAt + "Z"));
        a.append(title, date); li.appendChild(a); list.appendChild(li);
      });
    });
  }
  function loadFaqs() {
    api("/api/support/faqs").then(function (items) {
      var list = document.getElementById("faq-list"); list.replaceChildren();
      document.getElementById("faq-count").textContent = (items || []).length + "개";
      if (!items || items.length === 0) { var e = document.createElement("li"); e.className = "sub"; e.style.cssText = "padding:22px;text-align:center"; e.textContent = "등록된 FAQ가 없습니다"; list.appendChild(e); return; }
      items.forEach(function (faq) {
        var li = document.createElement("li"), qa = document.createElement("div"); qa.className = "qa";
        var q = document.createElement("button"); q.className = "q"; q.type = "button"; q.setAttribute("aria-expanded", "false");
        var qm = document.createElement("span"); qm.className = "mark"; qm.textContent = "Q";
        var qt = document.createElement("span"); qt.textContent = faq.question; q.append(qm, qt);
        var a = document.createElement("p"); a.className = "a"; a.hidden = true;
        var am = document.createElement("span"); am.className = "mark"; am.textContent = "A";
        var at = document.createElement("span"); at.textContent = faq.answer; a.append(am, at);
        q.addEventListener("click", function () { var open = q.getAttribute("aria-expanded") === "true"; q.setAttribute("aria-expanded", String(!open)); a.hidden = open; });
        qa.append(q, a); li.appendChild(qa); list.appendChild(li);
      });
    });
  }
  loadNotices(); loadFaqs();

  var pwButton = document.getElementById("pm-submit");
  pwButton.addEventListener("click", function () {
    var cur = document.getElementById("pm-cur"), next = document.getElementById("pm-new"), confirm = document.getElementById("pm-new2"), msg = document.getElementById("pm-msg");
    if (next.value !== confirm.value) { msg.textContent = "두 비밀번호가 서로 다릅니다"; msg.className = "msg err"; msg.hidden = false; return; }
    request("/api/auth/password", { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ currentPassword: cur.value, newPassword: next.value }) }).then(function (body) {
      if (body && body.success) location.href = "/login";
      else { msg.textContent = body && body.error ? body.error.message : "비밀번호를 변경하지 못했습니다"; msg.className = "msg err"; msg.hidden = false; }
    });
  });
  document.getElementById("qm-submit").addEventListener("click", function () {
    var confirm = document.getElementById("qm-confirm"), password = document.getElementById("qm-pw"), msg = document.getElementById("qm-msg");
    if (confirm.value.trim() !== "탈퇴") { msg.textContent = "확인 문구를 정확히 입력해주세요"; msg.hidden = false; return; }
    request("/api/auth/account", { method: "DELETE", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ password: password.value }) }).then(function (body) {
      if (body && body.success) location.href = "/";
      else { msg.textContent = body && body.error ? body.error.message : "탈퇴하지 못했습니다"; msg.hidden = false; }
    });
  });
})();
