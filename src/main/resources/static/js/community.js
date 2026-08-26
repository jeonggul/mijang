/* ==========================================================================
   미장 — SR-009 커뮤니티

   화면 셋이 같은 값을 그린다 — 목록 · 상세 · 글쓰기.
   매매 카드와 "주주" 배지를 만드는 코드가 셋 다 필요해서 한 파일에 둔다.
   페이지 구분은 그 화면에만 있는 엘리먼트가 있는지로 한다.

   사용자가 쓴 글자는 전부 textContent 로 넣는다. innerHTML 로 넣으면
   제목에 <script> 를 적은 사람이 남의 화면에서 코드를 돌린다.
   ========================================================================== */

const BOARD = window.MIJANG_BOARD || { board: "FREE", symbol: null };
const DASH = "—";

/* ── 응답 봉투 ────────────────────────────────────────────────
   401 이면 로그인으로 보낸다. 커뮤니티는 로그인 전용 화면이다. */
async function api(url, options) {
  const res = await fetch(url, options);
  if (res.status === 401) { location.href = "/login"; return null; }
  const body = await res.json();
  return body.success ? body.data : null;
}

/* ── 표시 형식 ──────────────────────────────────────────────── */

function usd(v) {
  if (v === null || v === undefined) return DASH;
  return "$" + Number(v).toLocaleString("en-US",
    { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** 부호를 붙인 원화. 0 도 부호 없이 그대로 쓴다 — +0 은 오해를 부른다. */
function sKrw(v) {
  if (v === null || v === undefined) return DASH;
  const n = Number(v);
  const sign = n > 0 ? "+" : n < 0 ? "-" : "";
  return sign + "₩" + Math.abs(n).toLocaleString("ko-KR", { maximumFractionDigits: 0 });
}

function pct(v) {
  if (v === null || v === undefined) return "";
  const n = Number(v) * 100;
  return (n > 0 ? "+" : "") + n.toFixed(1) + "%";
}

/** 손익 방향 클래스. 0 은 색을 주지 않는다. */
function dir(v) {
  if (v === null || v === undefined) return "";
  return Number(v) > 0 ? "rise" : Number(v) < 0 ? "fall" : "";
}

/** "3시간 전" · "어제" · "3일 전". 일주일이 넘으면 날짜로 적는다. */
function ago(iso) {
  if (!iso) return "";
  const then = new Date(iso);
  const minutes = Math.floor((Date.now() - then.getTime()) / 60000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return minutes + "분 전";
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + "시간 전";
  const days = Math.floor(hours / 24);
  if (days === 1) return "어제";
  if (days < 7) return days + "일 전";
  return then.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric" });
}

/** 체결 시각. "8월 22일 07:44" */
function tradedAt(iso) {
  if (!iso) return "";
  const at = new Date(iso);
  return at.toLocaleDateString("ko-KR", { month: "long", day: "numeric" })
    + " " + at.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false });
}

/* ── 조각 만들기 ────────────────────────────────────────────── */

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

/**
 * 글에 붙은 매매 카드.
 *
 * 수량은 그리지 않는다. 매수에는 손익 줄이 아예 없다 —
 * 산 순간에는 확정된 손익이 없어서 서버가 null 로 준다.
 */
function tradeCard(trade) {
  if (!trade) return null;

  const card = el("div", "trade-card");
  const sell = trade.side === "SELL";
  card.appendChild(el("span", "side", sell ? "매도" : "매수"));

  const body = el("div", "body");
  body.appendChild(el("p", "tk", trade.symbol));
  if (sell && trade.realizedPnlKrw !== null && trade.realizedPnlKrw !== undefined) {
    const rate = trade.realizedPnlRate;
    const pnl = sKrw(trade.realizedPnlKrw) + (rate === null || rate === undefined ? "" : " (" + pct(rate) + ")");
    body.appendChild(el("p", ("pnl num " + dir(trade.realizedPnlKrw)).trim(), pnl));
  }
  body.appendChild(el("p", "sub", "1주당 " + usd(trade.price) + " · " + tradedAt(trade.tradedAt)));

  card.appendChild(body);
  return card;
}

/** "주주" 배지. 수량은 붙이지 않는다. */
function shareholderBadge() {
  return el("span", "badge badge-line", "주주");
}

/* ── 목록 화면 ──────────────────────────────────────────────── */

function listUrl(sort) {
  const query = "?sort=" + encodeURIComponent(sort);
  return BOARD.symbol
    ? "/api/stocks/" + encodeURIComponent(BOARD.symbol) + "/posts" + query
    : "/api/posts" + query + "&board=" + encodeURIComponent(BOARD.board);
}

function postRow(post) {
  const li = document.createElement("li");
  const a = el("a", "post");
  a.href = "/community-post/" + post.id;

  a.appendChild(el("p", "ttl", post.title));
  if (post.excerpt) a.appendChild(el("p", "sum", post.excerpt));

  const card = tradeCard(post.trade);
  if (card) a.appendChild(card);

  const meta = el("p", "meta");
  if (post.priceAtWrite !== null && post.priceAtWrite !== undefined) {
    const at = el("span", "at", "작성 시점 ");
    at.appendChild(el("b", "num", usd(post.priceAtWrite)));
    meta.appendChild(at);
  }
  meta.appendChild(el("span", "who", post.authorName));
  if (post.shareholder) meta.appendChild(shareholderBadge());
  meta.appendChild(el("time", null, ago(post.createdAt)));
  meta.appendChild(el("span", "spacer"));
  meta.appendChild(el("span", "react", "좋아요 " + post.likeCount));
  meta.appendChild(el("span", "react", "댓글 " + post.commentCount));

  a.appendChild(meta);
  li.appendChild(a);
  return li;
}

async function loadList(sort) {
  const page = await api(listUrl(sort));
  const list = document.getElementById("post-list");
  const card = document.getElementById("post-card");
  const empty = document.getElementById("post-empty");

  list.replaceChildren();
  const posts = page ? page.content : [];
  posts.forEach(p => list.appendChild(postRow(p)));

  document.getElementById("post-count").textContent = (page ? page.totalElements : 0) + "건";
  card.hidden = posts.length === 0;
  empty.hidden = posts.length !== 0;
}

/** 사이드바의 내 보유 종목. 각 종목의 게시판으로 간다. */
async function loadMyBoards() {
  const nav = document.getElementById("my-boards");
  if (!nav) return;
  const holdings = (await api("/api/portfolio/holdings")) || [];

  nav.replaceChildren();
  holdings.forEach(h => {
    const li = document.createElement("li");
    const a = el("a");
    a.href = "/community/" + encodeURIComponent(h.symbol);
    if (h.symbol === BOARD.symbol) a.setAttribute("aria-current", "page");
    a.appendChild(el("span", "tk", h.symbol));
    li.appendChild(a);
    nav.appendChild(li);
  });
  document.getElementById("my-boards-empty").hidden = holdings.length > 0;
}

function setupList() {
  const pills = document.getElementById("sort");
  if (!pills) return false;

  pills.addEventListener("click", event => {
    const btn = event.target.closest("button[data-value]");
    if (btn) loadList(btn.dataset.value);
  });
  loadList("NEW");
  loadMyBoards();
  return true;
}

/* ── 상세 화면 ──────────────────────────────────────────────── */

function commentRow(comment) {
  const li = document.createElement("li");
  if (comment.parentId) li.className = "reply";

  const meta = el("p", "meta");
  if (comment.parentId) meta.appendChild(el("span", "arrow", "↳"));
  meta.appendChild(el("span", "who", comment.authorName));
  meta.appendChild(el("time", null, ago(comment.createdAt)));
  li.appendChild(meta);
  li.appendChild(el("p", "body", comment.content));
  return li;
}

async function loadDetail(postId) {
  const post = await api("/api/posts/" + postId);
  if (!post) {
    /* 숨김·삭제·없는 글. 조용히 돌아가면 제목 없는 껍데기 화면이 남는다 —
       실제로 브라우저 검증에서 그렇게 떴다. 무슨 일인지 말하고 액션은 감춘다 */
    document.getElementById("post-title").textContent = "게시글을 찾을 수 없습니다";
    document.getElementById("post-body").textContent =
      "삭제되었거나 숨김 처리된 글입니다. 목록에서 다른 글을 봐주세요.";
    document.querySelector(".post-actions").hidden = true;
    const commentCard = document.getElementById("comment-list");
    if (commentCard) commentCard.closest("section").hidden = true;
    return;
  }

  document.title = "미장 — " + post.title;
  document.getElementById("post-title").textContent = post.title;
  document.getElementById("post-body").textContent = post.content;

  /* 목록으로 · 글쓰기 둘 다 이 글이 속한 게시판으로 보낸다. 상세는 어느 게시판에서
     열렸는지 모른 채 뜨므로 글을 읽어야 알 수 있다 */
  const board = post.symbol ? "/community/" + encodeURIComponent(post.symbol)
      : post.board === "QNA" ? "/community?board=qna" : "/community";
  document.getElementById("board-link").href = board;
  document.getElementById("write-link").href =
    post.symbol ? "/community-write/" + encodeURIComponent(post.symbol) : "/community-write";
  document.getElementById("board-name").textContent =
    post.symbol ? post.symbol + " 게시판" : post.board === "QNA" ? "질문 게시판" : "자유 게시판";

  const meta = document.getElementById("post-meta");
  meta.replaceChildren();
  meta.appendChild(el("span", "who", post.authorName));
  if (post.shareholder) meta.appendChild(shareholderBadge());
  meta.appendChild(el("time", null, ago(post.createdAt) + " · 조회 " + post.viewCount));
  if (post.priceAtWrite !== null && post.priceAtWrite !== undefined) {
    meta.appendChild(el("span", "spacer"));
    const at = el("span", "at", "작성 시점 ");
    at.appendChild(el("b", "num", usd(post.priceAtWrite)));
    meta.appendChild(at);
  }

  const slot = document.getElementById("post-trade");
  slot.replaceChildren();
  const card = tradeCard(post.trade);
  if (card) slot.appendChild(card);

  paintReaction("like-btn", "좋아요 " + post.likeCount, post.myLike);
  paintReaction("scrap-btn", "스크랩", post.myScrap);
  document.getElementById("edit-btn").hidden = !post.mine;
  document.getElementById("delete-btn").hidden = !post.mine;
  document.getElementById("price-note").hidden = post.symbol === null;

  document.getElementById("comment-count").textContent = post.comments.length + "개";
  const list = document.getElementById("comment-list");
  list.replaceChildren();
  post.comments.forEach(c => list.appendChild(commentRow(c)));
}

function setupDetail() {
  const body = document.getElementById("post-body");
  if (!body) return false;

  const postId = window.MIJANG_POST_ID;
  loadDetail(postId);

  document.getElementById("comment-submit").addEventListener("click", async () => {
    const box = document.getElementById("cmt");
    if (!box.value.trim()) return;
    const saved = await api("/api/posts/" + postId + "/comments", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ content: box.value.trim() }),
    });
    if (saved !== null) { box.value = ""; loadDetail(postId); }
  });

  /* 좋아요·스크랩. 화면은 지금 상태를 판단하지 않는다 — 누른 사실만 보내고
     서버가 토글한 결과(active·likeCount)로 다시 그린다. 상태 판단은 서버 한 곳이다 */
  [["like-btn", "LIKE"], ["scrap-btn", "SCRAP"]].forEach(pair => {
    const btn = document.getElementById(pair[0]);
    btn.addEventListener("click", async () => {
      btn.disabled = true;
      const state = await api("/api/posts/" + postId + "/reactions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: pair[1] }),
      });
      btn.disabled = false;
      if (state === null) return;
      if (pair[1] === "LIKE") paintReaction("like-btn", "좋아요 " + state.likeCount, state.active);
      else paintReaction("scrap-btn", "스크랩", state.active);
    });
  });

  /* 신고. 409(중복)는 api() 가 null 로 접어 버리므로 여기서만 fetch 를 직접 쓴다 —
     "이미 신고했다" 와 "실패했다" 는 다른 안내가 필요하다 */
  const reportSubmit = document.getElementById("report-submit");
  if (reportSubmit) reportSubmit.addEventListener("click", async () => {
    const reason = document.querySelector('input[name="reason"]:checked');
    const msg = document.getElementById("report-msg");
    reportSubmit.disabled = true;
    const res = await fetch("/api/reports", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ targetType: "POST", targetId: postId, reason: reason.value }),
    });
    reportSubmit.disabled = false;
    if (res.ok) {
      document.querySelector("#report-modal [data-close]").click();
      msg.hidden = true;
      return;
    }
    msg.textContent = res.status === 409
      ? "이미 신고한 게시글입니다"
      : "신고를 접수하지 못했습니다. 잠시 후 다시 시도해주세요";
    msg.hidden = false;
  });

  /* 수정 — 글쓰기 화면을 수정 모드로 연다. 어느 화면인지는 글이 속한 게시판이 정한다 */
  document.getElementById("edit-btn").addEventListener("click", async () => {
    const post = await api("/api/posts/" + postId);
    if (!post) return;
    const base = post.symbol
      ? "/community-write/" + encodeURIComponent(post.symbol) : "/community-write";
    location.href = base + "?edit=" + postId;
  });

  /* 삭제 — 지워진 글의 주소로 돌아오면 404 라 목록으로 보낸다 */
  document.getElementById("delete-btn").addEventListener("click", async () => {
    if (!confirm("이 글을 삭제할까요? 되돌릴 수 없습니다.")) return;
    const res = await fetch("/api/posts/" + postId, { method: "DELETE" });
    if (res.ok) location.href = document.getElementById("board-link").href;
  });
  return true;
}

/** 반응 버튼 한 개를 그린다. 눌린 상태는 aria-pressed 로 남겨 CSS 와 보조기기가 같이 본다. */
function paintReaction(id, label, active) {
  const btn = document.getElementById(id);
  if (!btn) return;
  btn.textContent = label;
  btn.setAttribute("aria-pressed", String(!!active));
  btn.classList.toggle("btn-primary", !!active);
}

/* ── 글쓰기 화면 ────────────────────────────────────────────── */

/** 고를 수 있는 매매. 이 게시판 종목의 내 기록만 부른다. */
async function loadTradeOptions() {
  const select = document.getElementById("trade");
  if (!select || !BOARD.symbol) return;

  const page = await api("/api/transactions?symbol=" + encodeURIComponent(BOARD.symbol) + "&size=30");
  const rows = page ? page.content : [];
  rows.forEach(tx => {
    const option = document.createElement("option");
    option.value = tx.id;
    option.textContent = (tx.side === "SELL" ? "매도" : "매수")
      + " · " + tradedAt(tx.tradedAt) + " · 1주당 " + usd(tx.price);
    select.appendChild(option);
  });
  document.getElementById("trade-empty").hidden = rows.length > 0;
  select.disabled = rows.length === 0;

  select.addEventListener("change", () => previewTrade(rows, select.value));
}

/** 고른 매매를 글에 붙었을 때 모습 그대로 보여 준다. */
function previewTrade(rows, value) {
  const slot = document.getElementById("trade-preview");
  slot.replaceChildren();
  const tx = rows.find(r => String(r.id) === value);
  if (!tx) { slot.hidden = true; return; }

  /* 손익은 서버가 등록 시점에 다시 계산해서 박는다. 여기 값은 미리보기다 */
  const card = tradeCard({
    side: tx.side, symbol: tx.symbol, price: tx.price, tradedAt: tx.tradedAt,
    realizedPnlKrw: tx.realizedPnlKrw, realizedPnlRate: null,
  });
  slot.appendChild(card);
  slot.hidden = false;
}

/** 글자 수 표시. 한도가 maxlength 로 이미 걸려 있으니 세어서 보여 주기만 한다. */
function setupCounters() {
  document.querySelectorAll("[data-count]").forEach(input => {
    const out = document.getElementById(input.dataset.count);
    const limit = Number(input.getAttribute("maxlength"));
    const paint = () => {
      out.textContent = input.value.length.toLocaleString("ko-KR")
        + " / " + limit.toLocaleString("ko-KR");
    };
    input.addEventListener("input", paint);
    paint();
  });
}

function setupWrite() {
  const form = document.getElementById("write-form");
  if (!form) return false;

  setupCounters();

  /* ?edit={id} 면 수정 모드다. 제목·본문만 바꾼다 — 게시판·매매 카드·배지는
     등록 때 정해진 값이라 수정 화면에서 통째로 감춘다(2.3) */
  const editId = new URLSearchParams(location.search).get("edit");
  if (editId) {
    setupEdit(form, editId);
    return true;
  }

  loadTradeOptions();

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const trade = document.getElementById("trade");
    const badge = document.getElementById("badge");
    const board = document.getElementById("board");

    const url = BOARD.symbol
      ? "/api/stocks/" + encodeURIComponent(BOARD.symbol) + "/posts"
      : "/api/posts";
    const saved = await api(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: document.getElementById("ttl").value.trim(),
        content: document.getElementById("bd").value.trim(),
        board: board ? board.value : null,
        tradeTxId: trade && trade.value ? Number(trade.value) : null,
        showHoldingBadge: badge ? badge.checked : false,
      }),
    });
    if (saved !== null) location.href = "/community-post/" + saved;
  });
  return true;
}

/** 수정 모드. 기존 값을 채우고, 저장은 PATCH 로 보낸다. */
async function setupEdit(form, editId) {
  const post = await api("/api/posts/" + editId);
  if (!post) { location.href = "/community"; return; }
  if (!post.mine) { location.href = "/community-post/" + editId; return; }

  document.querySelector("h1").textContent = "글 수정";
  document.getElementById("ttl").value = post.title;
  document.getElementById("bd").value = post.content;
  /* 글자 수 카운터가 초기값을 다시 세도록 */
  document.getElementById("ttl").dispatchEvent(new Event("input"));
  document.getElementById("bd").dispatchEvent(new Event("input"));

  ["board", "trade", "badge"].forEach(id => {
    const control = document.getElementById(id);
    const field = control && control.closest(".field, .switch-row");
    if (field) field.hidden = true;
    else if (control) control.hidden = true;
  });
  const preview = document.getElementById("trade-preview");
  if (preview) preview.hidden = true;

  form.addEventListener("submit", async event => {
    event.preventDefault();
    /* 수정 성공의 data 는 null 이라 api() 로는 성공·실패가 안 갈린다. 상태 코드로 본다 */
    const res = await fetch("/api/posts/" + editId, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: document.getElementById("ttl").value.trim(),
        content: document.getElementById("bd").value.trim(),
      }),
    });
    if (res.ok) location.href = "/community-post/" + editId;
  });
}

/* ── 시작 ────────────────────────────────────────────────────
   화면마다 자기 것만 걸린다. 나머지는 엘리먼트가 없어서 그냥 지나간다. */
setupList() || setupDetail() || setupWrite();
