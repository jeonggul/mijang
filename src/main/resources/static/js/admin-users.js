/* 관리자 사용자 탭 (ADMIN-03). 목록은 서버를 다시 읽어 상태 변경 결과와 맞춘다. */
(function () {
  var list = document.getElementById("admin-user-list");
  if (!list) return;

  var filter = "ALL";
  var STATUS_LABEL = { ACTIVE: "활성", SUSPENDED: "정지", WITHDRAWN: "탈퇴 대기" };
  var STATUS_BADGE = { ACTIVE: "badge-ok", SUSPENDED: "badge-err", WITHDRAWN: "badge-warn" };

  async function request(url, options) {
    var response = await fetch(url, options);
    if (response.status === 401) { location.href = "/login"; return { error: "로그인이 필요합니다" }; }
    var body = await response.json().catch(function () { return null; });
    if (!response.ok || !body || !body.success) {
      return { error: body && body.error ? body.error.message : "요청을 처리하지 못했습니다" };
    }
    return { data: body.data };
  }

  function query(includeLimit) {
    var params = new URLSearchParams();
    if (includeLimit) params.set("limit", "100");
    if (filter !== "ALL") params.set("status", filter);
    var q = document.getElementById("us-q").value.trim();
    if (q) params.set("q", q);
    return params.toString();
  }

  function cell(row, value, className) {
    var td = row.insertCell();
    if (className) td.className = className;
    td.textContent = value == null ? "—" : value;
    return td;
  }

  function empty(message) {
    list.replaceChildren();
    var td = list.insertRow().insertCell();
    td.colSpan = 7;
    td.className = "sub";
    td.style.cssText = "padding:22px;text-align:center";
    td.textContent = message;
  }

  function badge(status) {
    var value = document.createElement("span");
    value.className = "badge " + (STATUS_BADGE[status] || "");
    value.textContent = STATUS_LABEL[status] || status;
    return value;
  }

  function actionCell(row, user) {
    var td = row.insertCell();
    td.className = "r";
    if (user.role === "ADMIN") {
      var role = document.createElement("span");
      role.className = "badge badge-accent";
      role.textContent = "관리자";
      td.appendChild(role);
    }
    if (!user.manageable) return;

    var button = document.createElement("button");
    button.className = "btn btn-sm";
    button.type = "button";
    button.style.marginLeft = user.role === "ADMIN" ? "6px" : "0";
    button.textContent = user.status === "ACTIVE" ? "정지" : "해제";
    button.dataset.userId = user.userId;
    button.dataset.nextStatus = user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    button.dataset.nickname = user.nickname;
    td.appendChild(button);

    /* 관리자에게만 붙는 권한 해제. 정지와 다른 일이라 버튼을 따로 둔다 —
       하나로 합치면 "해제" 가 정지 해제인지 권한 해제인지 알 수 없다 */
    if (user.role === "ADMIN") {
      var demote = document.createElement("button");
      demote.className = "btn btn-sm";
      demote.type = "button";
      demote.style.marginLeft = "6px";
      demote.textContent = "권한 해제";
      demote.dataset.demoteId = user.userId;
      demote.dataset.nickname = user.nickname;
      td.appendChild(demote);
    }
  }

  function draw(users) {
    if (users.length === 0) { empty("조건에 맞는 사용자가 없습니다"); return; }
    list.replaceChildren();
    users.forEach(function (user) {
      var row = list.insertRow();
      cell(row, user.nickname, "txt");
      cell(row, user.email, "txt");
      cell(row, user.createdAt ? String(user.createdAt).slice(0, 10) : "—");
      cell(row, Number(user.transactionCount).toLocaleString("ko-KR"), "r num");
      cell(row, Number(user.postCount).toLocaleString("ko-KR"), "r num");
      var state = row.insertCell();
      state.appendChild(badge(user.status));
      actionCell(row, user);
    });
  }

  async function load() {
    var suffix = query(true);
    var countSuffix = query(false);
    var results = await Promise.all([
      request("/api/admin/users?" + suffix),
      request("/api/admin/users/count?" + countSuffix),
      request("/api/admin/users/count")
    ]);
    if (results[0].error) { empty(results[0].error); return; }
    draw(results[0].data || []);
    if (!results[1].error) {
      document.getElementById("admin-user-count").textContent =
        Number(results[1].data).toLocaleString("ko-KR") + "명";
    }
    if (!results[2].error) {
      document.getElementById("ad-user-total").textContent =
        Number(results[2].data).toLocaleString("ko-KR");
    }
  }

  list.addEventListener("click", async function (event) {
    var demote = event.target.closest("button[data-demote-id]");
    if (demote) {
      if (!confirm(demote.dataset.nickname + " 을(를) 일반 사용자로 전환할까요?\n"
                 + "전환하면 그 계정은 관리자 화면에 들어올 수 없습니다.")) return;
      demote.disabled = true;
      var out = await request("/api/admin/users/" + demote.dataset.demoteId + "/demote",
                              { method: "PATCH" });
      demote.disabled = false;
      if (out.error) { alert(out.error); await load(); return; }
      await load();
      window.dispatchEvent(new CustomEvent("admin:logs-changed"));
      return;
    }

    var button = event.target.closest("button[data-user-id]");
    if (!button) return;
    var verb = button.dataset.nextStatus === "SUSPENDED" ? "정지" : "정지 해제";
    if (!confirm(button.dataset.nickname + " 계정을 " + verb + "할까요?")) return;

    button.disabled = true;
    var result = await request("/api/admin/users/" + button.dataset.userId + "/status", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: button.dataset.nextStatus })
    });
    button.disabled = false;
    if (result.error) { alert(result.error); await load(); return; }
    await load();
    window.dispatchEvent(new CustomEvent("admin:logs-changed"));
  });

  var timer;
  document.getElementById("us-q").addEventListener("input", function () {
    clearTimeout(timer);
    timer = setTimeout(load, 250);
  });
  document.querySelector("[data-user-filter]").addEventListener("mj:change", function (event) {
    filter = event.detail;
    load();
  });

  load();
})();
