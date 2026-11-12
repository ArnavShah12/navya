/* Navya – browser app. Talks to the Navya Java server (REST under /api, WebSocket for chat). */

const CATS = ["Clothes", "Shoes", "Bags", "Accessories"];
const CONDS = ["New with tags", "Like new", "Good", "Fair"];

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const rupee = n => "₹" + Number(n).toLocaleString("en-IN");
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);

function toast(m) {
  const t = $("#toast");
  t.textContent = m; t.hidden = false;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (t.hidden = true), 3000);
}

/* ---------- app state ---------- */
let token = localStorage.getItem("navya.token");
let me = null;                 // { id, email, name, city, role } when logged in
let favIds = new Set();        // ids of listings the user saved
let activeCat = "";            // selected category chip on the home page
let routeToken = 0;            // lets old async renders notice they are out of date
let socket = null, socketTimer = null, pingTimer = null;   // live chat
let backTo = null;             // page the user wanted before being sent to log in
let postImgs = [];             // photos in the sell form: { key, url } (uploaded) or { blob, preview } (new)
let boostKey = uid();          // one key per payment attempt, so a double click cannot charge twice
let items = [], total = 0, pageNo = 0, fetchTok = 0, fetchTimer = null;   // home page results

/* ---------- server calls ---------- */
async function call(method, path, body, raw) {
  const headers = {};
  if (token) headers.Authorization = "Bearer " + token;
  if (body !== undefined && !raw) headers["Content-Type"] = "application/json";
  if (raw) headers["Content-Type"] = "image/jpeg";
  let r;
  try {
    r = await fetch(path, { method, headers, body: body === undefined ? undefined : raw ? body : JSON.stringify(body) });
  } catch {
    throw new Error("Can't reach the server. Check your connection.");
  }
  const data = r.status === 204 ? null : await r.json().catch(() => null);
  if (r.status === 401 && token && !path.startsWith("/api/auth/")) { setSession(null); setTimeout(route, 0); }
  if (!r.ok) throw Object.assign(new Error(data?.message || "Something went wrong"), { status: r.status, code: data?.code });
  return data;
}

function setSession(t, user) {
  token = t; me = user || null;
  if (t) localStorage.setItem("navya.token", t); else { localStorage.removeItem("navya.token"); favIds = new Set(); }
}

const api = {
  async loadMe() {
    if (!token) return;
    try {
      me = await call("GET", "/api/me");
      favIds = new Set(await call("GET", "/api/favourites"));
    } catch { setSession(null); }
  },
  async auth(path, payload) {
    try {
      const r = await call("POST", path, payload);
      setSession(r.token, r.user);
      favIds = new Set(await call("GET", "/api/favourites"));
      return { user: me };
    } catch (e) { return { error: e.message }; }
  },
  signup: p => api.auth("/api/auth/signup", p),
  login: (email, password) => api.auth("/api/auth/login", { email, password }),
  logout() { setSession(null); },

  listing: id => call("GET", "/api/listings/" + encodeURIComponent(id)).catch(e => { if (e.status === 404) return null; throw e; }),
  async toggleFav(id) {
    const r = await call("POST", "/api/favourites/" + id);
    if (r.saved) favIds.add(id); else favIds.delete(id);
  },
  async uploadImage(blob) { return (await call("POST", "/api/images", blob, true)).key; },
  report: (listingId, reason) => call("POST", "/api/reports", { listingId, reason }).then(() => true, () => false),
  async startConvo(listingId) { try { return (await call("POST", "/api/conversations", { listingId })).id; } catch (e) { toast(e.message); return null; } }
};

/* ---------- small render helpers ---------- */
// CSP forbids inline style attributes, so the blurred card background is set from script after the HTML is in place
function paintBg(root = document) {
  $$("[data-bg]", root).forEach(e => { e.style.setProperty("--bg", `url('${e.dataset.bg}')`); e.removeAttribute("data-bg"); });
}
function setHtml(el, html) { el.innerHTML = html; paintBg(el); }

const thumb = url => url ? `<img class="thumb" src="${esc(url)}" alt="">` : `<div class="thumb ph"></div>`;
const firstImg = l => l?.images?.[0]?.url || "";

function card(l) {
  const fav = favIds.has(l.id), img = firstImg(l);
  return `<div class="card"${img ? ` data-bg="${esc(img)}"` : ""}>
    <a href="#/listing/${l.id}">
      <div class="img">${img ? `<img src="${esc(img)}" alt="" loading="lazy">` : `<span class="ph-ico"></span>`}${l.status === "sold" ? '<span class="badge">Sold</span>' : l.boosted ? '<span class="badge feat">Featured</span>' : ""}</div>
      <div class="body"><span class="price">${rupee(l.price)}</span><strong>${esc(l.title)}</strong><small>${esc(l.city)} · ${esc(l.condition)}</small></div>
    </a>
    <button class="heart ${fav ? "on" : ""}" data-fav="${l.id}" aria-label="Save">${fav ? "♥" : "♡"}</button>
  </div>`;
}

/* ---------- views ---------- */
function show(name) {
  $$(".view").forEach(v => (v.hidden = v.id !== "view-" + name));
  $("#safety").hidden = !["listing", "chat"].includes(name); // safety note only where deals happen
  window.scrollTo(0, 0);
}

function resetFilters() {
  $("#filters").reset();
  activeCat = "";
  $("#filterPanel").hidden = true;
}

function renderHome() {
  const f = Object.fromEntries(new FormData($("#filters")));
  $("#chips").innerHTML = ["", ...CATS].map(c =>
    `<button type="button" data-cat="${c}" class="${activeCat === c ? "on" : ""}">${c || "All"}</button>`).join("");
  const n = [f.city, f.min, f.max, f.favs].filter(Boolean).length;
  $("#filterBtn").textContent = n ? `Filters (${n})` : "Filters";
  scheduleFetch();
}

// filtering, sorting and paging happen on the server, so a change waits briefly (typing) and then asks again
function scheduleFetch(delay = 250) {
  clearTimeout(fetchTimer);
  fetchTimer = setTimeout(() => loadListings(false), delay);
}

async function loadListings(more) {
  const tk = ++fetchTok;
  const f = Object.fromEntries(new FormData($("#filters")));
  const qs = new URLSearchParams();
  if (f.q) qs.set("q", f.q);
  if (activeCat) qs.set("category", activeCat);
  if (f.city) qs.set("city", f.city);
  if (f.min) qs.set("min", f.min);
  if (f.max) qs.set("max", f.max);
  if (f.favs && me) qs.set("favs", "1");
  if (f.sort) qs.set("sort", f.sort);
  qs.set("page", more ? pageNo + 1 : 0);
  if (!more) { $("#count").textContent = "Loading…"; }
  let data;
  try { data = await call("GET", "/api/listings?" + qs); } catch (e) { if (tk === fetchTok) { $("#count").textContent = ""; toast(e.message); } return; }
  if (tk !== fetchTok) return;
  pageNo = more ? pageNo + 1 : 0;
  items = more ? items.concat(data.items) : data.items;
  total = data.total;
  $("#count").textContent = `${total} ${total === 1 ? "item" : "items"}`;
  setHtml($("#grid"), items.map(card).join(""));
  $("#empty").hidden = items.length > 0;
  $("#more").hidden = items.length >= total;
}

async function renderDetail(id) {
  const tk = routeToken;
  $("#detail").innerHTML = `<p class="empty">Loading…</p>`;
  let l;
  try { l = await api.listing(id); } catch (e) { $("#detail").innerHTML = `<p class="empty">${esc(e.message)}</p>`; return; }
  if (tk !== routeToken) return;
  if (!l) {
    $("#detail").innerHTML = `<button class="back" data-back>← Back</button><div class="empty"><h3>Listing not found</h3><p>It may have been removed.</p></div>`;
    return;
  }
  const seller = l.seller;
  const own = me && me.id === l.sellerId;
  const fav = favIds.has(l.id), img = firstImg(l);
  setHtml($("#detail"), `
  <button class="back" data-back>← Back</button>
  <div class="detail">
    <div>
      <div class="main-img"${img ? ` data-bg="${esc(img)}"` : ""}>${img ? `<img id="mainImg" src="${esc(img)}" alt="">` : `<span class="ph-ico"></span>`}${l.status === "sold" ? '<span class="badge">Sold</span>' : ""}</div>
      ${l.images.length > 1 ? `<div class="thumbs">${l.images.map(s => `<img src="${esc(s.url)}" data-thumb alt="">`).join("")}</div>` : ""}
    </div>
    <div class="info">
      <h1>${esc(l.title)}</h1>
      <p class="big-price">${rupee(l.price)}</p>
      <p class="tags"><span>${esc(l.category)}</span><span>${esc(l.condition)}</span>${l.size ? `<span>Size ${esc(l.size)}</span>` : ""}<span>${esc(l.city)}</span></p>
      <p class="desc">${esc(l.description)}</p>
      <a class="seller-card" href="#/seller/${seller.id}"><span class="avatar">${esc((seller.name || "?")[0].toUpperCase())}</span><span><strong>${esc(seller.name)}</strong><small>Seller · ${esc(seller.city || l.city)}</small></span></a>
      <div class="actions">
        ${own ? `<a class="btn" href="#/post/${l.id}">Edit listing</a>${l.status === "available" ? `<a class="btn ghost" href="#/boost/${l.id}">Boost</a>` : ""}`
          : l.status !== "available" ? `<button class="btn" disabled>${l.status === "sold" ? "Sold" : "Unavailable"}</button>`
          : `<button class="btn" data-chat="${l.id}">Message seller</button>`}
        <button class="btn ghost" data-fav="${l.id}">${fav ? "♥ Saved" : "♡ Save"}</button>
        ${own ? "" : `<button class="btn ghost" data-report="${l.id}">Report</button>`}
      </div>
    </div>
  </div>`);
}

async function renderMine() {
  const tk = routeToken;
  let mine = [];
  try { mine = await call("GET", "/api/listings/mine"); } catch (e) { toast(e.message); }
  if (tk !== routeToken) return;
  setHtml($("#mineList"), mine.map(l => `
    <div class="row">
      <a href="#/listing/${l.id}">${thumb(firstImg(l))}</a>
      <div class="grow"><strong>${esc(l.title)}</strong>
        <div>${rupee(l.price)} <span class="pill ${l.status === "sold" ? "sold" : ""}">${esc(l.status[0].toUpperCase() + l.status.slice(1))}</span>${l.boosted ? '<span class="pill">Featured</span>' : ""}</div>
        <div class="btns">
          <a class="btn ghost small" href="#/post/${l.id}">Edit</a>
          ${l.status === "available" ? `<a class="btn ghost small" href="#/boost/${l.id}">Boost</a>` : ""}
          ${l.status === "available" || l.status === "sold" ? `<button class="btn ghost small" data-mark="${l.id}" data-status="${l.status}">${l.status === "sold" ? "Mark available" : "Mark sold"}</button>` : ""}
          <button class="btn danger small" data-del="${l.id}">Delete</button>
        </div>
      </div>
    </div>`).join(""));
  $("#noMine").hidden = mine.length > 0;
}

async function renderInbox() {
  const tk = routeToken;
  $("#chatList").innerHTML = ""; $("#noChats").hidden = true;
  let rows = [];
  try { rows = await call("GET", "/api/conversations"); } catch (e) { toast(e.message); }
  if (tk !== routeToken) return;
  $("#chatList").innerHTML = rows.map(c => `<a class="row" href="#/chat/${c.id}">${thumb(c.listingImage)}<div class="grow"><strong>${esc(c.otherName)} · ${esc(c.listingTitle)}</strong><small>${c.lastBody ? esc(c.lastBody) : "No messages yet"}</small></div></a>`).join("");
  $("#noChats").hidden = rows.length > 0;
}

async function renderMsgs(id) {
  let msgs;
  try { msgs = await call("GET", `/api/conversations/${id}/messages`); } catch { return; }
  if ($("#chatForm").dataset.id !== id || $("#view-chat").hidden) return;
  $("#msgs").innerHTML = msgs.map(m => `<div class="msg ${m.senderId === me.id ? "me" : ""}"><p>${esc(m.body)}</p>
    <small>${new Date(m.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</small></div>`).join("")
    || `<p class="empty">Say hi. Ask about condition, price or where to meet.</p>`;
  $("#msgs").scrollTop = 1e9;
}

// live updates: the server pings this socket when a message arrives, then we fetch what is new
function closeSocket() {
  clearTimeout(socketTimer); clearInterval(pingTimer);
  if (socket) { socket.onclose = null; socket.close(); socket = null; }
}

function openSocket(id) {
  closeSocket();
  const ws = new WebSocket(`${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/chat/${id}`);
  socket = ws;
  ws.onopen = () => {
    ws.send(JSON.stringify({ token }));
    pingTimer = setInterval(() => ws.readyState === 1 && ws.send("ping"), 20000);
  };
  ws.onmessage = e => { try { if (JSON.parse(e.data).type === "new") renderMsgs(id); } catch { /* ignore */ } };
  ws.onclose = () => {
    clearInterval(pingTimer);
    // lost the connection: catch up, then try again, only while this chat is still open
    socketTimer = setTimeout(() => { if ($("#chatForm").dataset.id === id && !$("#view-chat").hidden) { renderMsgs(id); openSocket(id); } }, 3000);
  };
}

async function renderChat(id) {
  const tk = routeToken;
  $("#chatHead").innerHTML = ""; $("#msgs").innerHTML = "";
  let c;
  try { c = await call("GET", "/api/conversations/" + id); } catch { location.hash = "#/chats"; return; }
  if (tk !== routeToken) return;
  $("#chatHead").innerHTML = `<a href="#/chats">←</a><div><strong>${esc(c.other.name)}</strong><br>
    <a href="#/listing/${c.listing.id}">${esc(c.listing.title)} · ${rupee(c.listing.price)}</a></div>`;
  $("#chatForm").dataset.id = id;
  await renderMsgs(id);
  if (tk !== routeToken) return;
  openSocket(id);
}

async function renderSeller(id) {
  const tk = routeToken;
  $("#sellerBox").innerHTML = `<p class="empty">Loading…</p>`; $("#sellerGrid").innerHTML = "";
  let r;
  try { r = await call("GET", "/api/sellers/" + encodeURIComponent(id)); } catch { if (tk === routeToken) $("#sellerBox").innerHTML = `<p class="empty">Seller not found.</p>`; return; }
  if (tk !== routeToken) return;
  const s = r.seller;
  $("#sellerBox").innerHTML = `<div class="seller-head"><span class="avatar big">${esc((s.name || "?")[0].toUpperCase())}</span><div><h2>${esc(s.name)}</h2><p class="muted">${s.city ? esc(s.city) + " · " : ""}${r.listings.length} listing${r.listings.length === 1 ? "" : "s"}</p></div></div>`;
  setHtml($("#sellerGrid"), r.listings.map(card).join(""));
}

async function renderBoost(id) {
  const tk = routeToken;
  boostKey = uid(); $("#boostErr").textContent = ""; $("#boostPay").disabled = false;
  $("#boostForm").dataset.id = id;
  try {
    const [l, q] = await Promise.all([api.listing(id), call("GET", "/api/boost/quote")]);
    if (tk !== routeToken) return;
    if (!l || l.sellerId !== me.id || l.status !== "available") { location.hash = "#/mine"; return; }
    $("#boostInfo").textContent = `Pin "${l.title}" at the top of the home page for ${q.days} days. Price: ${rupee(q.price)}.`;
  } catch (e) { toast(e.message); location.hash = "#/mine"; }
}

async function renderOrders() {
  const tk = routeToken;
  let rows = [];
  try { rows = await call("GET", "/api/orders"); } catch (e) { toast(e.message); }
  if (tk !== routeToken) return;
  $("#orderList").innerHTML = rows.map(o => `<div class="row"><div class="grow"><strong>Boost · ${esc(o.listingTitle || "Deleted listing")}</strong>
    <div>${rupee(o.amount)} <span class="pill ${o.status === "paid" ? "paid" : ""}">${esc(o.status[0].toUpperCase() + o.status.slice(1))}</span></div>
    <small>${new Date(o.createdAt).toLocaleString()}</small></div></div>`).join("");
  $("#noOrders").hidden = rows.length > 0;
}

async function renderAdmin() {
  const tk = routeToken;
  let rows = [], stats = {};
  try { [rows, stats] = await Promise.all([call("GET", "/api/admin/reports"), call("GET", "/api/admin/stats")]); } catch (e) { toast(e.message); location.hash = "#/"; return; }
  if (tk !== routeToken) return;
  $("#reportList").innerHTML = rows.map(r => `<div class="row"><div class="grow"><strong><a href="#/listing/${r.listingId}">${esc(r.listingTitle)}</a></strong>
    <small>${esc(r.reason)}</small>
    <div class="btns"><button class="btn danger small" data-resolve="${r.id}" data-action="remove">Take down</button>
    <button class="btn ghost small" data-resolve="${r.id}" data-action="dismiss">Dismiss</button></div></div></div>`).join("");
  $("#noReports").hidden = rows.length > 0;
  $("#adminStats").textContent = Object.entries(stats).map(([k, v]) => `${k}: ${v}`).join("\n");
}

/* ---------- sell / edit form ---------- */
function renderPreview() {
  $("#preview").innerHTML = postImgs.map((im, i) => `<div><img src="${esc(im.url || im.preview)}" alt=""><button type="button" data-rm="${i}">×</button></div>`).join("");
}

async function initPost(id) {
  const f = $("#postForm");
  f.reset(); postImgs = [];
  f.elements.listingId.value = ""; // reset() doesn't clear hidden inputs
  $("#postTitle").textContent = "Sell an item";
  f.elements.city.value = me.city || "";
  if (id) {
    const l = await api.listing(id);
    if (!l || l.sellerId !== me.id) { location.hash = "#/mine"; return; }
    $("#postTitle").textContent = "Edit listing";
    f.elements.listingId.value = l.id;
    ["title", "price", "category", "condition", "size", "city", "description"].forEach(k => (f.elements[k].value = l[k] ?? ""));
    postImgs = l.images.map(im => ({ key: im.key, url: im.url }));
  }
  renderPreview();
}

// shrink photos in the browser first so they upload fast and stay under the server's size limit
const resize = (file, max = 1000) => new Promise(res => {
  const img = new Image();
  const url = URL.createObjectURL(file);
  img.onload = () => {
    const s = Math.min(1, max / Math.max(img.width, img.height));
    const c = document.createElement("canvas");
    c.width = Math.round(img.width * s); c.height = Math.round(img.height * s);
    c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
    URL.revokeObjectURL(url);
    c.toBlob(b => res(b), "image/jpeg", 0.75);
  };
  img.onerror = () => res(null);
  img.src = url;
});

/* ---------- router ---------- */
function refreshNav() {
  $$("[data-auth]").forEach(el => {
    const need = el.dataset.auth;
    el.hidden = need === "admin" ? !(me && me.role === "ADMIN") : (need === "in") !== !!me;
  });
  $$(".nav-link").forEach(a => a.classList.toggle("active", !a.hasAttribute("data-logout") && a.getAttribute("href") === (location.hash || "#/")));
  $("#nav").classList.remove("open");
  $("#menuBtn").classList.remove("open");
}

async function route() {
  ++routeToken;
  closeSocket();
  const [, page = "", arg] = location.hash.slice(1).split("/");
  refreshNav();

  if (!["login", "signup"].includes(page)) backTo = null;
  if (["login", "signup"].includes(page) && me) { location.hash = "#/"; return; }
  if (["post", "mine", "chats", "chat", "boost", "orders", "admin"].includes(page) && !me) { backTo = location.hash; location.hash = "#/login"; return; }

  switch (page) {
    case "listing": show("listing"); await renderDetail(arg); break;
    case "login": show("login"); break;
    case "signup": show("signup"); break;
    case "post": show("post"); await initPost(arg); break;
    case "mine": show("mine"); await renderMine(); break;
    case "chats": show("chats"); await renderInbox(); break;
    case "chat": show("chat"); await renderChat(arg); break;
    case "seller": show("seller"); await renderSeller(arg); break;
    case "boost": show("boost"); await renderBoost(arg); break;
    case "orders": show("orders"); await renderOrders(); break;
    case "admin": show("admin"); await renderAdmin(); break;
    default: show("home"); renderHome();
  }
}

/* ---------- events ---------- */
$$("select[name=category]").forEach(s => CATS.forEach(c => s.add(new Option(c, c))));
CONDS.forEach(c => $("#postForm").elements.condition.add(new Option(c, c)));

$("#menuBtn").onclick = () => $("#menuBtn").classList.toggle("open", $("#nav").classList.toggle("open"));
$("#filters").addEventListener("input", renderHome);
$("#more").onclick = () => loadListings(true);
// search icon toggles the search field (works the same on phone and desktop)
$("#searchBtn").onclick = () => {
  const p = $("#searchPanel");
  const open = p.classList.toggle("open");
  $("#searchBtn").classList.toggle("on", open);
  if (open) $("#q").focus();
};
// the search box lives in the header (outside the form), so it needs its own listener
$("#q").addEventListener("input", () => {
  if (location.hash === "" || location.hash === "#/") renderHome();
  else location.hash = "#/"; // searching from another page takes you to the results
});
$("#filters").addEventListener("submit", e => e.preventDefault());

async function authSubmit(e, fn, errBox) {
  e.preventDefault();
  const form = e.target, btn = form.querySelector("button.btn");
  btn.disabled = true;
  const r = await fn(form.elements);
  btn.disabled = false;
  if (r.error) { $(errBox).textContent = r.error; return; }
  form.reset(); $(errBox).textContent = "";
  const dest = backTo || "#/"; backTo = null; location.hash = dest;
}
$("#loginForm").addEventListener("submit", e => authSubmit(e, f => api.login(f.email.value.trim(), f.password.value), "#loginErr"));
$("#signupForm").addEventListener("submit", e => authSubmit(e, f => api.signup({ name: f.name.value.trim(), email: f.email.value.trim(), city: f.city.value.trim(), password: f.password.value, consent: f.consent.checked }), "#signupErr"));

$("#photos").addEventListener("change", async e => {
  for (const file of [...e.target.files]) {
    if (postImgs.length >= 4) { toast("Max 4 photos"); break; }
    const blob = await resize(file);
    if (!blob) { toast("Couldn't read that image"); continue; }
    postImgs.push({ blob, preview: URL.createObjectURL(blob) });
  }
  e.target.value = ""; renderPreview();
});

$("#postForm").addEventListener("submit", async e => {
  e.preventDefault();
  const form = e.target, btn = form.querySelector("button.btn"), label = btn.textContent;
  btn.disabled = true; btn.textContent = "Publishing…";
  try {
    const f = Object.fromEntries(new FormData(form));
    const images = [];
    for (const im of postImgs) images.push(im.key || await api.uploadImage(im.blob));
    const body = { title: f.title.trim(), price: +f.price, category: f.category, condition: f.condition, size: (f.size || "").trim(), city: f.city.trim(), description: f.description.trim(), images };
    await (f.listingId ? call("PUT", "/api/listings/" + f.listingId, body) : call("POST", "/api/listings", body));
    toast("Listing published");
    location.hash = "#/mine";
  } catch (err) {
    toast(err.message || "Something went wrong");
  } finally {
    btn.disabled = false; btn.textContent = label;
  }
});

$("#boostForm").addEventListener("submit", async e => {
  e.preventDefault();
  const form = e.target, btn = $("#boostPay");
  btn.disabled = true; $("#boostErr").textContent = "";
  try {
    const r = await call("POST", `/api/listings/${form.dataset.id}/boost`, { token: form.elements.token.value, key: boostKey });
    toast(r.status === "paid" ? "Boost is live" : r.message);
    location.hash = "#/orders";
  } catch (err) {
    $("#boostErr").textContent = err.message;
    boostKey = uid(); // a declined payment is final, the next try is a new payment
    btn.disabled = false;
  }
});

$("#chatForm").addEventListener("submit", async e => {
  e.preventDefault();
  const input = e.target.elements.text, text = input.value.trim(), id = e.target.dataset.id;
  if (!text) return;
  input.value = "";
  try { await call("POST", `/api/conversations/${id}/messages`, { body: text }); } catch (err) { toast(err.message || "Message not sent"); input.value = text; return; }
  renderMsgs(id);
});

async function download(path, name) {
  const r = await fetch(path, { headers: { Authorization: "Bearer " + token } });
  if (!r.ok) { toast("Download failed"); return; }
  const a = document.createElement("a");
  a.href = URL.createObjectURL(await r.blob()); a.download = name; a.click();
  URL.revokeObjectURL(a.href);
}

document.addEventListener("click", async e => {
  const t = e.target.closest("[data-fav],[data-chat],[data-report],[data-thumb],[data-cat],[data-mark],[data-del],[data-rm],[data-logout],[data-back],[data-clear],[data-filterbtn],[data-resolve],[data-export]");
  if (!t) return;

  if (t.dataset.logout !== undefined) { api.logout(); route(); return; }
  if (t.dataset.thumb !== undefined) { $("#mainImg").src = t.src; $(".main-img").style.setProperty("--bg", `url('${t.src}')`); return; }
  if (t.dataset.cat !== undefined) { activeCat = t.dataset.cat; renderHome(); return; }
  if (t.dataset.back !== undefined) { history.length > 1 ? history.back() : (location.hash = "#/"); return; }
  if (t.dataset.clear !== undefined) { resetFilters(); renderHome(); return; }
  if (t.dataset.filterbtn !== undefined) { $("#filterPanel").hidden = !$("#filterPanel").hidden; return; }
  if (t.dataset.rm !== undefined) { postImgs.splice(+t.dataset.rm, 1); renderPreview(); return; }

  if (t.dataset.fav) {
    e.preventDefault();
    if (!me) { location.hash = "#/login"; return; }
    const id = t.dataset.fav;
    try { await api.toggleFav(id); } catch (err) { toast(err.message); return; }
    if (!$("#view-home").hidden && $("#filters").elements.favs.checked) scheduleFetch(0);
    $$(`[data-fav="${id}"]`).forEach(b => {
      const on = favIds.has(id);
      if (b.classList.contains("heart")) { b.classList.toggle("on", on); b.textContent = on ? "♥" : "♡"; }
      else b.textContent = on ? "♥ Saved" : "♡ Save";
    });
    return;
  }
  if (t.dataset.chat) {
    if (!me) { location.hash = "#/login"; return; }
    const id = await api.startConvo(t.dataset.chat);
    if (id) location.hash = "#/chat/" + id;
    return;
  }
  if (t.dataset.report) {
    if (!me) { location.hash = "#/login"; return; }
    const reason = prompt("Why are you reporting this listing?");
    if (reason) toast((await api.report(t.dataset.report, reason)) ? "Thanks, we'll review it" : "Couldn't send the report");
    return;
  }
  if (t.dataset.mark) {
    try { await call("POST", `/api/listings/${t.dataset.mark}/status`, { status: t.dataset.status === "sold" ? "available" : "sold" }); } catch (err) { toast(err.message); }
    renderMine(); return;
  }
  if (t.dataset.del) {
    if (!confirm("Delete this listing?")) return;
    try { await call("DELETE", "/api/listings/" + t.dataset.del); } catch (err) { toast(err.message || "Couldn't delete the listing"); }
    renderMine(); return;
  }
  if (t.dataset.resolve) {
    try { await call("POST", `/api/admin/reports/${t.dataset.resolve}/${t.dataset.action}`); } catch (err) { toast(err.message); }
    renderAdmin(); return;
  }
  if (t.dataset.export) {
    if (t.dataset.export === "csv") await download("/api/admin/export.csv", "navya-listings.csv");
    else try { toast("Saved " + (await call("POST", "/api/admin/export")).key); } catch (err) { toast(err.message); }
  }
});

// Logo / Home always goes back to the fresh home page (clears search + filters)
document.addEventListener("click", e => {
  const a = e.target.closest('a[href="#/"]');
  if (!a || a.hasAttribute("data-logout")) return;
  resetFilters();
  if (location.hash === "#/") { route(); window.scrollTo(0, 0); } // same hash = no hashchange event
});

window.addEventListener("hashchange", route);

(async () => { await api.loadMe(); route(); })();
