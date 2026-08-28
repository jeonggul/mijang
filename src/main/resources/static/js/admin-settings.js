/* ==========================================================================
   미장 — SR-013 운영 설정

   화면의 알약·스위치를 admin_settings 에 연결한다.
   한 칸을 누르면 그 칸만 저장한다 — 전체를 보내면 두 관리자가 다른 칸을 만졌을 때
   나중 저장이 앞 것을 덮는다.

   저장에 실패하면 화면을 되돌린다. 실패했는데 눌린 채로 두면 관리자는 적용된 줄 안다.
   ========================================================================== */
(function () {
  var panel = document.getElementById("a-config");
  if (!panel) return;

  var state = document.getElementById("cfg-state");

  function api(url, options) {
    return fetch(url, options).then(function (res) {
      if (res.status === 401) { location.href = "/login"; return null; }
      if (res.status === 403) return { forbidden: true };
      return res.json();
    }).catch(function () { return null; });
  }

  /* 저장 결과를 한 줄로 알린다. 성공은 잠깐만 띄우고 지운다 */
  var hideTimer = null;
  function say(text, ok) {
    if (!state) return;
    state.textContent = text;
    state.className = "msg" + (ok ? "" : " msg-err");
    state.hidden = false;
    clearTimeout(hideTimer);
    if (ok) hideTimer = setTimeout(function () { state.hidden = true; }, 2000);
  }

  /* 서버가 준 값으로 화면을 맞춘다. 화면의 기본 선택을 믿지 않는다 —
     마이그레이션 기본값과 화면 마크업이 어긋나면 눌린 것과 실제가 달라진다 */
  function paint(settings) {
    if (!settings) return;
    panel.querySelectorAll("[data-setting]").forEach(function (el) {
      var value = settings[el.dataset.setting];
      if (value === undefined) return;
      if (el.tagName === "INPUT" && el.type === "checkbox") {
        el.checked = value === "true";
      } else {
        el.querySelectorAll("button[data-value]").forEach(function (b) {
          b.setAttribute("aria-pressed", String(b.dataset.value === value));
        });
      }
    });
  }

  function save(key, value, revert) {
    return api("/api/admin/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ key: key, value: value }),
    }).then(function (body) {
      if (!body || body.forbidden || !body.success) {
        revert();
        say((body && body.error && body.error.message) || "저장하지 못했습니다", false);
        return;
      }
      paint(body.data);
      say("저장했습니다", true);
    });
  }

  /* 스위치 — 켜짐/꺼짐이 곧 값이다 */
  panel.querySelectorAll('input[type="checkbox"][data-setting]').forEach(function (box) {
    box.addEventListener("change", function () {
      var before = !box.checked;
      save(box.dataset.setting, String(box.checked), function () { box.checked = before; });
    });
  });

  /* 알약 — ui.js 가 aria-pressed 를 먼저 옮기고 mj:change 를 쏜다 */
  panel.querySelectorAll("[data-setting][data-toggle-group]").forEach(function (group) {
    var before = group.querySelector('button[aria-pressed="true"]');
    group.addEventListener("mj:change", function (e) {
      var previous = before;
      before = group.querySelector('button[aria-pressed="true"]');
      save(group.dataset.setting, e.detail, function () {
        if (!previous) return;
        group.querySelectorAll("button").forEach(function (b) {
          b.setAttribute("aria-pressed", String(b === previous));
        });
        before = previous;
      });
    });
  });

  api("/api/admin/settings").then(function (body) {
    if (body && body.success) paint(body.data);
  });
})();
