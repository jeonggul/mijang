/* ==========================================================================
   미장 — 공지 관리 (ADMIN-05)

   목록·등록·수정·삭제를 한 곳에서 다룬다.
   등록 폼을 수정에도 그대로 쓴다 — 화면을 따로 만들면 글자 수 제한이나
   고정 여부 같은 규칙이 두 벌이 되어 한쪽만 고쳐진다.
   ========================================================================== */
(function () {
  var list = document.getElementById("admin-notice-list");
  if (!list) return;

  /* 고치는 중인 공지 id. null 이면 새로 등록하는 것이다 */
  var editingId = null;

  function api(url, options) {
    return fetch(url, options)
      .then(function (res) { return res.json(); })
      .then(function (body) { return body.success ? { ok: true, data: body.data }
                                                  : { ok: false, message: body.error && body.error.message }; });
  }

  function date(value) {
    return value ? new Intl.DateTimeFormat("ko-KR").format(new Date(value + "Z")) : "—";
  }

  function emptyRow(message) {
    var row = list.insertRow();
    var cell = row.insertCell();
    cell.colSpan = 5;
    cell.className = "sub";
    cell.style.cssText = "padding:22px;text-align:center";
    cell.textContent = message;
  }

  /* 제목·내용은 관리자가 쓴 글자다. 전부 textContent 로 넣는다 */
  function row(notice) {
    var tr = list.insertRow();
    [notice.pinned ? "고정" : "—", notice.title, date(notice.createdAt), date(notice.updatedAt)]
      .forEach(function (value, index) {
        var cell = tr.insertCell();
        cell.textContent = value;
        if (index === 1) cell.className = "txt";
      });

    var actions = tr.insertCell();
    actions.className = "r";
    actions.appendChild(button("수정", "edit", notice));
    var del = button("삭제", "delete", notice);
    del.style.marginLeft = "6px";
    actions.appendChild(del);
  }

  function button(label, action, notice) {
    var b = document.createElement("button");
    b.className = "btn btn-sm";
    b.type = "button";
    b.textContent = label;
    b.dataset.noticeAction = action;
    b.dataset.noticeId = notice.id;
    b.dataset.title = notice.title;
    b.dataset.content = notice.content || "";
    b.dataset.pinned = String(!!notice.pinned);
    return b;
  }

  function load() {
    api("/api/admin/notices").then(function (out) {
      var items = out.ok ? (out.data || []) : [];
      list.replaceChildren();
      document.getElementById("admin-notice-count").textContent = items.length + "건";
      if (items.length === 0) { emptyRow("등록된 공지가 없습니다"); return; }
      items.forEach(row);
    });
  }

  var form = document.getElementById("admin-notice-form");
  var title = document.getElementById("nt-title");
  var body = document.getElementById("nt-body");
  var pinned = document.getElementById("nt-pinned");
  var submit = form.querySelector('button[type="submit"]');
  var heading = document.querySelector('#a-notice-new h1');

  /* 등록으로 되돌린다. 수정하다 말고 다른 탭에 갔다 오면 그 공지가 폼에 남아
     새 공지인 줄 알고 저장하는 일이 생긴다 */
  function resetToCreate() {
    editingId = null;
    form.reset();
    if (heading) heading.textContent = "공지 등록";
    if (submit) submit.textContent = "등록";
  }

  list.addEventListener("click", async function (event) {
    var btn = event.target.closest("[data-notice-action]");
    if (!btn) return;

    if (btn.dataset.noticeAction === "delete") {
      if (!confirm("「" + btn.dataset.title + "」 공지를 삭제할까요?")) return;
      btn.disabled = true;
      var out = await api("/api/admin/notices/" + btn.dataset.noticeId, { method: "DELETE" });
      btn.disabled = false;
      if (!out.ok) { alert(out.message || "삭제하지 못했습니다"); return; }
      if (editingId === btn.dataset.noticeId) resetToCreate();
      load();
      window.dispatchEvent(new CustomEvent("admin:logs-changed"));
      return;
    }

    editingId = btn.dataset.noticeId;
    title.value = btn.dataset.title;
    body.value = btn.dataset.content;
    pinned.checked = btn.dataset.pinned === "true";
    if (heading) heading.textContent = "공지 수정";
    if (submit) submit.textContent = "수정 저장";
    document.getElementById("m-notice-new").click();
    title.focus();
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    var payload = {
      title: title.value.trim(),
      content: body.value.trim(),
      pinned: pinned.checked
    };
    var out = await api(editingId ? "/api/admin/notices/" + editingId : "/api/admin/notices", {
      method: editingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!out.ok) { alert(out.message || "저장하지 못했습니다"); return; }
    resetToCreate();
    load();
    document.getElementById("m-notice").click();
    window.dispatchEvent(new CustomEvent("admin:logs-changed"));
  });

  /* 취소를 누르면 폼만 비는 게 아니라 등록 모드로 돌아가야 한다 */
  form.addEventListener("reset", function () { setTimeout(resetToCreate, 0); });

  load();
})();
