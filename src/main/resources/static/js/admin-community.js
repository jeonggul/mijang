/* ==========================================================================
   미장 — 관리자 게시글·댓글·신고 탭 (SR-013 · 4.5 점검 3.1)

   세 표 모두 같은 리듬이다 — 불러서 그리고, 처리 버튼이 성공하면 다시 그린다.
   화면이 행을 직접 고치지 않는 이유: 신고를 받아들이면 대상 글도 함께 내려가는데,
   그 연쇄는 서버가 안다. 다시 불러오면 화면이 서버와 어긋날 길이 없다.

   사용자가 쓴 글자(제목·내용·닉네임)는 전부 textContent 로 넣는다.
   ========================================================================== */
(function () {
  var postList = document.getElementById("admin-post-list");
  if (!postList) return;

  var STATUS_LABEL = { PUBLISHED: "공개", HIDDEN: "숨김", DELETED: "삭제됨" };
  var REASON_LABEL = { SPAM: "스팸·홍보", ABUSE: "욕설·비방", MISINFO: "허위 정보", ETC: "기타" };
  var BOARD_LABEL = { FREE: "자유", QNA: "질문" };

  function api(url, options) {
    return fetch(url, options).then(function (res) {
      if (!res.ok) return null;
      return res.json();
    }).then(function (body) { return body && body.success ? body.data : null; });
  }

  function cell(tr, text, className) {
    var td = tr.insertCell();
    if (className) td.className = className;
    td.textContent = text == null ? "—" : text;
    return td;
  }

  function statusBadge(tr, status) {
    var td = tr.insertCell();
    var badge = document.createElement("span");
    badge.className = "badge " + (status === "PUBLISHED" ? "badge-ok" : "badge-err");
    badge.textContent = STATUS_LABEL[status] || status;
    td.appendChild(badge);
    return td;
  }

  function actionButton(label, onClick) {
    var btn = document.createElement("button");
    btn.className = "btn btn-sm";
    btn.type = "button";
    btn.textContent = label;
    btn.addEventListener("click", function () {
      btn.disabled = true;
      onClick().finally(function () { btn.disabled = false; });
    });
    return btn;
  }

  function notice(tbody, span, message) {
    tbody.replaceChildren();
    var td = tbody.insertRow().insertCell();
    td.colSpan = span; td.className = "sub";
    td.style.cssText = "padding:22px;text-align:center";
    td.textContent = message;
  }

  function day(iso) { return iso ? String(iso).slice(0, 10) : "—"; }

  /* ── 게시글 ── */
  async function loadPosts() {
    var posts = await api("/api/admin/posts?limit=100");
    if (!posts) { notice(postList, 7, "목록을 불러오지 못했습니다"); return; }
    var hiddenCount = posts.filter(function (p) { return p.status !== "PUBLISHED"; }).length;
    document.getElementById("admin-post-count").textContent =
      posts.length + "건 · 숨김·삭제 " + hiddenCount;
    if (posts.length === 0) { notice(postList, 7, "게시글이 없습니다"); return; }

    postList.replaceChildren();
    posts.forEach(function (post) {
      var tr = postList.insertRow();
      var title = cell(tr, post.title, "txt");
      /* 제목을 누르면 실제 화면으로 — 숨긴 글은 404 가 맞다. 그게 숨김의 뜻이다 */
      title.replaceChildren();
      var link = document.createElement("a");
      link.href = "/community-post/" + post.id;
      link.target = "_blank"; link.rel = "noreferrer noopener";
      link.textContent = post.title;
      title.appendChild(link);
      cell(tr, post.symbol || BOARD_LABEL[post.board] || post.board);
      cell(tr, post.authorName, "txt");
      cell(tr, post.likeCount, "r num");
      cell(tr, post.commentCount, "r num");
      statusBadge(tr, post.status);
      var actions = tr.insertCell();
      actions.className = "r";
      if (post.status !== "DELETED") {
        var hide = post.status === "PUBLISHED";
        actions.appendChild(actionButton(hide ? "숨김" : "복원", function () {
          return fetch("/api/admin/posts/" + post.id + "/status", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ hidden: hide }),
          }).then(function (res) { if (res.ok) { loadPosts(); } });
        }));
      }
    });
  }

  /* ── 댓글 ── */
  var commentList = document.getElementById("admin-comment-list");
  async function loadComments() {
    var comments = await api("/api/admin/comments?limit=100");
    if (!comments) { notice(commentList, 6, "목록을 불러오지 못했습니다"); return; }
    var hiddenCount = comments.filter(function (c) { return c.status !== "PUBLISHED"; }).length;
    document.getElementById("admin-comment-count").textContent =
      comments.length + "건 · 숨김·삭제 " + hiddenCount;
    if (comments.length === 0) { notice(commentList, 6, "댓글이 없습니다"); return; }

    commentList.replaceChildren();
    comments.forEach(function (comment) {
      var tr = commentList.insertRow();
      cell(tr, comment.content, "txt");
      cell(tr, comment.postTitle, "txt");
      cell(tr, comment.authorName, "txt");
      cell(tr, day(comment.createdAt));
      statusBadge(tr, comment.status);
      var actions = tr.insertCell();
      actions.className = "r";
      if (comment.status !== "DELETED") {
        var hide = comment.status === "PUBLISHED";
        actions.appendChild(actionButton(hide ? "숨김" : "복원", function () {
          return fetch("/api/admin/comments/" + comment.id + "/status", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ hidden: hide }),
          }).then(function (res) { if (res.ok) { loadComments(); } });
        }));
      }
    });
  }

  /* ── 신고 ── */
  var reportList = document.getElementById("admin-report-list");
  var reportStatus = "PENDING";

  async function loadReports() {
    var reports = await api("/api/admin/reports?status=" + reportStatus + "&limit=100");
    if (!reports) { notice(reportList, 6, "목록을 불러오지 못했습니다"); return; }
    document.getElementById("admin-report-count").textContent = reports.length + "건";
    /* 운영 현황의 미처리 칸은 항상 PENDING 기준이다. 다른 필터를 보고 있어도 흔들리지 않는다 */
    if (reportStatus === "PENDING") {
      var overview = document.getElementById("ov-report-count");
      if (overview) overview.textContent = reports.length;
    }
    if (reports.length === 0) {
      notice(reportList, 6, reportStatus === "PENDING" ? "미처리 신고가 없습니다" : "해당 상태의 신고가 없습니다");
      return;
    }

    reportList.replaceChildren();
    reports.forEach(function (report) {
      var tr = reportList.insertRow();
      cell(tr, report.targetType === "POST" ? "게시글" : "댓글");
      cell(tr, report.targetSummary || "(삭제된 대상)", "txt");
      cell(tr, REASON_LABEL[report.reason] || report.reason);
      cell(tr, report.reporterName, "txt");
      cell(tr, day(report.createdAt));
      var actions = tr.insertCell();
      actions.className = "r";
      if (report.status === "PENDING") {
        actions.appendChild(actionButton("숨김 처리", function () { return handle(report.id, "RESOLVE"); }));
        actions.appendChild(document.createTextNode(" "));
        actions.appendChild(actionButton("반려", function () { return handle(report.id, "REJECT"); }));
      } else {
        actions.textContent = report.status === "RESOLVED" ? "처리 완료" : "반려됨";
      }
    });
  }

  function handle(reportId, action) {
    return fetch("/api/admin/reports/" + reportId, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: action }),
    }).then(function (res) {
      if (res.ok || res.status === 409) {
        /* 409 는 다른 관리자가 방금 처리한 것 — 다시 불러오면 목록에서 빠져 있다 */
        loadReports();
        loadPosts();
        loadComments();
      }
    });
  }

  var filter = document.getElementById("report-filter");
  if (filter) filter.querySelectorAll("button").forEach(function (btn) {
    btn.addEventListener("click", function () {
      reportStatus = btn.dataset.value;
      loadReports();
    });
  });

  loadPosts();
  loadComments();
  loadReports();
})();
