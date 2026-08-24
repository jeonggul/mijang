(function () {
  var list = document.getElementById("admin-notice-list");
  if (!list) return;
  function api(url, options) { return fetch(url, options).then(function (res) { return res.json(); }).then(function (body) { return body.success ? body.data : null; }); }
  function date(value) { return value ? new Intl.DateTimeFormat("ko-KR").format(new Date(value + "Z")) : "—"; }
  function load() {
    api("/api/admin/notices").then(function (items) {
      list.replaceChildren(); document.getElementById("admin-notice-count").textContent = (items || []).length + "건";
      if (!items || items.length === 0) { var row = list.insertRow(), cell = row.insertCell(); cell.colSpan = 4; cell.className = "sub"; cell.style.cssText = "padding:22px;text-align:center"; cell.textContent = "등록된 공지가 없습니다"; return; }
      items.forEach(function (notice) { var row = list.insertRow(); [notice.pinned ? "고정" : "—", notice.title, date(notice.createdAt), date(notice.updatedAt)].forEach(function (value, index) { var cell = row.insertCell(); cell.textContent = value; if (index === 1) cell.className = "txt"; }); });
    });
  }
  var form = document.getElementById("admin-notice-form");
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    api("/api/admin/notices", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ title: document.getElementById("nt-title").value.trim(), content: document.getElementById("nt-body").value.trim(), pinned: document.getElementById("nt-pinned").checked }) }).then(function (id) { if (id == null) return; form.reset(); load(); document.getElementById("m-notice").click(); });
  });
  load();
})();
