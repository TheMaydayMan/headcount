// Headcount Studio: the shell (navigation, routing, top bar, command palette, keyboard shortcuts, live polling).
import { el, btn, badge, toast, closeDrawer, confirmDialog, highlight, ICONS } from "./ui.js";
import * as S from "./store.js";
import { setupPages } from "./pages-setup.js";
import { setupPages2 } from "./pages-setup2.js";
import { livePages, rcon, rconReady } from "./pages-live.js";
import { learnPages, runChecks } from "./pages-learn.js";

const PAGES = [...setupPages, ...livePages, ...setupPages2, ...learnPages];
const GROUPS = ["Home", "Live server", "Set up", "Learn", "Reference", "Tools"];
const byId = Object.fromEntries(PAGES.map((p) => [p.id, p]));
let cleanup = null, pendingKey = null, currentId = null, problemsCache = null;

// ---------------------------------------------------------------- shell
const nav = el("nav", { class: "side" });
const main = el("main");
const saveCount = el("span", { class: "count hidden" });
const livePill = el("span", { class: "pill", title: "The server's database" }, el("span", { class: "led" }), "live");
const rconPill = el("span", { class: "pill", title: "RCON: running commands on the server" }, el("span", { class: "led" }), "RCON");
const themeBtn = el("button", { class: "iconbtn", title: "Theme" }, "◐");
document.body.append(el("div", { class: "app" },
  el("header", { class: "topbar" },
    el("div", { class: "logo" }, el("div", { class: "mark" }, "☻"), el("div", {}, "Headcount Studio", el("small", {}, "set up · run · learn"))),
    el("button", { class: "searchbtn", title: "Search (Ctrl+K)", onclick: () => palette() }, el("b", { class: "sicon" }, "⌕"), el("span", { class: "slong" }, "Search pages, options, commands…"), el("span", { class: "sshort" }, "Search"), el("kbd", {}, "Ctrl K")),
    el("span", { class: "grow" }), livePill, rconPill,
    el("button", { class: "iconbtn", title: "Undo (Ctrl+Z)", onclick: () => doUndo() }, "↶"), el("button", { class: "iconbtn", title: "Redo (Ctrl+Shift+Z)", onclick: () => doRedo() }, "↷"),
    el("button", { class: "btn primary savebtn", title: "Review & save (Ctrl+S)", onclick: () => go("#/review") }, el("span", { class: "savelong" }, "Review & save"), el("span", { class: "saveshort" }, "Save"), saveCount), themeBtn,
    el("button", { class: "iconbtn", title: "Quit Studio", onclick: quit }, "⏻")),
  nav, main));

function drawNav() {
  nav.innerHTML = "";
  const probs = problems();
  for (const g of GROUPS) {
    if (g === "Live server" && !S.store.info.live) continue;
    const items = PAGES.filter((p) => p.group === g && !p.hidden);
    if (!items.length) continue;
    nav.append(el("div", { class: "navgroup" }, el("div", { class: "gl" }, g), items.map((p) => el("a", { class: "navitem" + (p.id === currentId || (currentId?.startsWith(p.id + "-") && false) ? " active" : ""), href: "#/" + p.id },
      el("span", { class: "ic" }, p.icon), p.title,
      p.id === "problems" && probs.length ? badge(probs.length, probs.some((x) => x.level === "error") ? "bad" : "warn") : null,
      p.id === "review" && S.dirtyNames().length ? badge(S.dirtyNames().length, "warn") : null))));
  }
  const n = S.dirtyNames().length;
  saveCount.textContent = n;
  saveCount.classList.toggle("hidden", !n);
}

// ---------------------------------------------------------------- routing
export function go(hash, key) {
  pendingKey = key || null;
  if (location.hash === hash) route(); else location.hash = hash;
}
async function route() {
  closeDrawer();
  const parts = (location.hash.replace(/^#\/?/, "") || "overview").split("/").map(decodeURIComponent);
  const page = byId[parts[0]] || byId.overview;
  currentId = page.id;
  if (cleanup) { try { cleanup(); } catch (e) { /* ignore */ } cleanup = null; }
  main.innerHTML = "";
  main.scrollTop = 0;
  const root = el("div", { class: "page" });
  main.append(root);
  drawNav();
  try {
    const c = await page.render(root, parts.slice(1));
    if (typeof c === "function") cleanup = c;
  } catch (e) {
    console.error(e);
    root.append(el("div", { class: "note bad" }, "This page hit an error: " + e.message));
  }
  if (pendingKey) {
    const k = pendingKey; pendingKey = null;
    setTimeout(() => { if (!highlight(k)) { const [file, path] = k.split("#"); if (page.id !== "files") go("#/files/" + file, k); } }, 60);
  }
}
window.addEventListener("hashchange", route);

/** The page that edits an option, from each page's `owns` list ("file" or "file:top-key"). */
function pageFor(file, path) {
  const top = path.split(".")[0];
  for (const p of PAGES) for (const o of p.owns || []) {
    const [f, k] = o.split(":");
    if (f === file && (!k || k === top)) return p.id;
  }
  return "files";
}
export function goToOption(file, path) {
  const id = pageFor(file, path);
  go("#/" + id + (id === "files" ? "/" + file : ""), file + "#" + path);
}

// ---------------------------------------------------------------- palette
function paletteItems() {
  const items = [];
  for (const p of PAGES) if (!p.hidden) items.push({ kind: "page", title: p.title, desc: p.group, run: () => go("#/" + p.id) });
  items.push({ kind: "action", title: "Save all", desc: "review and save every change", run: () => go("#/review") },
    { kind: "action", title: "Undo", desc: "Ctrl+Z", run: doUndo }, { kind: "action", title: "Redo", desc: "Ctrl+Shift+Z", run: doRedo },
    { kind: "action", title: "Reload the server's configs", desc: "/hca reload over RCON", run: () => rcon("hca reload", { confirm: "Reloads Headcount on the running server." }) },
    { kind: "action", title: "Run selftest", desc: "/hca selftest over RCON", run: () => rcon("hca selftest") });
  for (const o of S.store.catalog?.options || []) items.push({ kind: "option", title: o.path, desc: o.file + " · " + o.help, run: () => goToOption(o.file, o.path) });
  for (const c of S.store.catalog?.commands || []) items.push({ kind: "command", title: c.syntax[0], desc: c.does, run: () => go("#/ref-commands") });
  for (const ph of S.store.catalog?.placeholders || []) items.push({ kind: "placeholder", title: "%headcount_" + ph.name + "%", desc: ph.description, run: () => go("#/ref-placeholders") });
  for (const n of Object.keys(S.get("statuses.yml", "statuses") || {})) items.push({ kind: "status", title: n, desc: "statuses.yml", run: () => go("#/statuses") });
  for (const n of Object.keys(S.get("actions.yml", "lists") || {})) items.push({ kind: "list", title: n, desc: "action list", run: () => go("#/hooks") });
  for (const [t, T] of Object.entries(S.get("shop.yml", "tiers") || {})) for (const e of Object.keys(T?.entries || {})) items.push({ kind: "shop", title: e, desc: "shop tier " + t, run: () => go("#/shop/" + t) });
  for (const d of S.store.catalog?.docs || []) items.push({ kind: "doc", title: d.title, desc: d.name, run: () => go("#/docs/" + d.name) });
  for (const p of paletteItems.players || []) items.push({ kind: "player", title: p.name, desc: p.online ? "online" : "player", run: () => go("#/live-player/" + p.uuid) });
  for (const n of S.store.names) items.push({ kind: "file", title: n, desc: "raw file", run: () => go("#/files/" + n) });
  return items;
}
function palette() {
  const scrim = el("div", { class: "scrim" });
  const input = el("input", { type: "text", placeholder: "Type to search… (Enter opens, Esc closes)" });
  const res = el("div", { class: "res" });
  const box = el("div", { class: "palette" }, input, res);
  const all = paletteItems();
  let sel = 0, shown = [];
  const close = () => { scrim.remove(); box.remove(); };
  const score = (it, q) => {
    const t = it.title.toLowerCase(), d = it.desc.toLowerCase();
    if (t === q) return 100; if (t.startsWith(q)) return 80; if (t.includes(q)) return 60;
    if (q.split(/\s+/).every((w) => (t + " " + d).includes(w))) return 30 + (it.kind === "page" ? 5 : 0);
    return 0;
  };
  const draw = () => {
    const q = input.value.trim().toLowerCase();
    shown = q ? all.map((it) => [it, score(it, q)]).filter(([, s]) => s > 0).sort((a, b) => b[1] - a[1]).slice(0, 60).map(([it]) => it) : all.filter((x) => x.kind === "page" || x.kind === "action");
    sel = Math.min(sel, Math.max(0, shown.length - 1));
    res.innerHTML = "";
    shown.forEach((it, i) => res.append(el("div", { class: "it" + (i === sel ? " on" : ""), onmouseenter: () => { sel = i; draw(); }, onclick: () => { close(); it.run(); } },
      el("span", { class: "k" }, it.kind), el("span", { class: "tt" }, it.title), el("span", { class: "dd" }, it.desc))));
    if (!shown.length) res.append(el("div", { class: "empty" }, "Nothing found."));
    res.querySelector(".on")?.scrollIntoView({ block: "nearest" });
  };
  input.addEventListener("input", () => { sel = 0; draw(); });
  input.addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown") { sel = Math.min(shown.length - 1, sel + 1); draw(); e.preventDefault(); }
    if (e.key === "ArrowUp") { sel = Math.max(0, sel - 1); draw(); e.preventDefault(); }
    if (e.key === "Enter" && shown[sel]) { close(); shown[sel].run(); }
    if (e.key === "Escape") close();
  });
  scrim.addEventListener("click", close);
  document.body.append(scrim, box);
  draw();
  input.focus();
}

// ---------------------------------------------------------------- undo, problems, keys
function doUndo() { const n = S.undo(); if (n) { toast("Undid a change to " + n, "info"); route(); } }
function doRedo() { const n = S.redo(); if (n) { toast("Redid a change to " + n, "info"); route(); } }
function problems() { if (!problemsCache) { try { problemsCache = runChecks(); } catch (e) { console.error(e); problemsCache = []; } } return problemsCache; }
let navTimer = null;
S.on((ev) => {
  if (ev.type === "change" || ev.type === "saved" || ev.type === "load") {
    problemsCache = null;
    clearTimeout(navTimer);
    navTimer = setTimeout(drawNav, 250);
  }
});
document.addEventListener("keydown", (e) => {
  const typing = ["INPUT", "TEXTAREA", "SELECT"].includes(document.activeElement?.tagName);
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); palette(); return; }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") { e.preventDefault(); go("#/review"); return; }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z" && !typing) { e.preventDefault(); e.shiftKey ? doRedo() : doUndo(); return; }
  if (e.key === "Escape") { closeDrawer(); return; }
  if (e.key === "/" && !typing) { e.preventDefault(); palette(); }
});
window.addEventListener("beforeunload", (e) => { if (S.dirtyNames().length) { e.preventDefault(); e.returnValue = ""; } });

// ---------------------------------------------------------------- theme, quit, heartbeat, live
(function theme() {
  const order = ["auto", "light", "dark"];
  let t = "auto";
  try { t = localStorage.getItem("hc-theme") || "auto"; } catch (e) { /* private window */ }
  const apply = () => { if (t === "auto") document.documentElement.removeAttribute("data-theme"); else document.documentElement.setAttribute("data-theme", t); themeBtn.title = "Theme: " + t; };
  themeBtn.addEventListener("click", () => { t = order[(order.indexOf(t) + 1) % 3]; try { localStorage.setItem("hc-theme", t); } catch (e) { /* ignore */ } apply(); });
  apply();
})();
async function quit() {
  if (S.dirtyNames().length && !(await confirmDialog("Quit with unsaved changes?", "They'll be lost.", { ok: "Quit", danger: true }))) return;
  if (!S.dirtyNames().length && !(await confirmDialog("Quit Headcount Studio?", "This stops Studio. Open it again with the Headcount Studio file.", { ok: "Quit" }))) return;
  try { await S.post("/api/quit", {}); } catch (e) { /* it's gone */ }
  document.body.innerHTML = "<div class='empty' style='padding-top:30vh'>Headcount Studio has stopped. You can close this tab.</div>";
}
setInterval(() => fetch("/api/ping").catch(() => {}), 15000);
fetch("/api/ping").catch(() => {});
async function pollLive() {
  if (!S.store.info.live) return;
  try {
    const L = await S.api("/api/live/summary");
    S.emitLive(L);
    livePill.querySelector(".led").className = "led on";
    livePill.title = "Live data from " + S.store.dir + " (cycle " + (L.cycle?.id ?? "none") + ")";
  } catch (e) { livePill.querySelector(".led").className = "led off"; }
  try { paletteItems.players = await S.api("/api/live/players"); } catch (e) { /* ignore */ }
}

// ---------------------------------------------------------------- start
window.studio = { go, goToOption, problems, refresh: () => route() };
(async () => {
  main.append(el("div", { class: "empty", style: { paddingTop: "30vh" } }, "Opening your Headcount configs…"));
  try { await S.loadAll(); } catch (e) { main.innerHTML = ""; main.append(el("div", { class: "page" }, el("div", { class: "note bad" }, "Couldn't load: " + e.message))); return; }
  if (!S.store.info.live) livePill.classList.add("hidden");
  rconPill.querySelector(".led").className = "led " + (rconReady() ? "on" : "off");
  rconPill.title = rconReady() ? "RCON is set up: Studio can run commands" : "RCON is off (enable-rcon and rcon.password in server.properties)";
  await pollLive();
  setInterval(pollLive, 5000);
  route();
})();
