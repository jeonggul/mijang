(function () {
  var root = document.getElementById("notice-detail");
  if (!root) return;
  fetch("/api/support/notices/" + encodeURIComponent(root.dataset.noticeId)).then(function (res) {
    return res.json();
  }).then(function (body) {
    if (!body.success) throw new Error();
    var notice = body.data;
    document.getElementById("notice-title").textContent = notice.title;
    document.getElementById("notice-content").textContent = notice.content;
    document.getElementById("notice-date").textContent = new Intl.DateTimeFormat("ko-KR", { dateStyle: "long" }).format(new Date(notice.createdAt + "Z"));
    document.getElementById("notice-pinned").hidden = !notice.pinned;
  }).catch(function () {
    document.getElementById("notice-title").textContent = "공지를 찾을 수 없습니다";
    document.getElementById("notice-content").textContent = "삭제되었거나 존재하지 않는 공지입니다.";
  });
})();
