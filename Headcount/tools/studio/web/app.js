// Headcount Studio: the frame (navigation, routing, header, search, review and save, undo and redo, keyboard
// shortcuts, live polling and the texture download).
import { el, btn, icon, iconBtn, toast, closeDrawer, closePop, confirmDialog, highlight, drawer, sprite, face, ICONS } from "./ui.js";
import * as S from "./store.js";
import { loadSprites } from "./sprites.js";
import { setupPages } from "./pages-setup.js";
import { setupPages2 } from "./pages-setup2.js";
import { livePages, liveAliases, rcon, rconReady, showReply } from "./pages-live.js";
import { helpPages, helpAliases, GLOSSARY, REF_TABS, WALK_TITLES, openWalk, openRecipe } from "./pages-help.js";
import { filesPages, filesAliases, runChecks, diffView } from "./pages-files.js";

const PAGES = [...setupPages, ...setupPages2, ...livePages, ...helpPages, ...filesPages];
const GROUPS = ["Home", "Set up", "Live server", "Help", "Files"];
/** Old addresses that still work: bookmarks and links in the docs. */
const ALIASES = { ...liveAliases, ...helpAliases, ...filesAliases };
const byId = Object.fromEntries(PAGES.map((p) => [p.id, p]));
let cleanup = null, pendingKey = null, currentId = null, problemsCache = null;

// ---------------------------------------------------------------- frame
const nav = el("nav", { class: "side", "aria-label": "Pages" });
const navList = el("div");
const navFoot = el("div", { class: "navfoot" });
nav.append(navList, navFoot);
const main = el("main", { id: "main" });
const undoBtn = iconBtn("undo", "Undo (Ctrl+Z)", () => doUndo());
const redoBtn = iconBtn("redo", "Redo (Ctrl+Shift+Z)", () => doRedo());
const saveBtn = el("button", { class: "savebtn", title: "Review and save (Ctrl+S)", onclick: () => { if (S.dirtyNames().length) openReview(); } });
const menuBtn = iconBtn("menu", "Pages", () => document.body.classList.toggle("navopen"), "menubtn");
const brandMark = el("span", { style: { display: "contents" } }, sprite("player_head", 26));
document.body.append(el("div", { class: "app" },
  el("header", { class: "topbar" }, menuBtn,
    el("a", { class: "brand", href: "#/overview" }, brandMark, el("span", { class: "nm" }, "Headcount Studio")),
    el("button", { class: "search", title: "Search (Ctrl+K)", "aria-label": "Search", onclick: () => palette() }, icon("search"), el("span", {}, "Search settings, pages, players"), el("kbd", {}, "Ctrl K")),
    el("span", { class: "spacer" }), undoBtn, redoBtn, saveBtn),
  nav, main), el("div", { class: "navscrim", onclick: () => document.body.classList.remove("navopen") }));

function pageDirty(p) {
  for (const o of p.owns || []) {
    const [f, k] = o.split(":");
    const d = S.doc(f);
    if (!d || d.text === d.saved) continue;
    if (!k) return true;
    if (JSON.stringify(S.get(f, k)) !== JSON.stringify(S.savedValue(f, k))) return true;
  }
  return false;
}
function drawNav() {
  navList.innerHTML = "";
  const probs = problems();
  for (const g of GROUPS) {
    if (g === "Live server" && !S.store.info.live) continue;
    const items = PAGES.filter((p) => p.group === g && !p.hidden);
    if (!items.length) continue;
    navList.append(el("div", { class: "navgroup" }, g === "Home" ? null : el("div", { class: "gl" }, g), items.map((p) => {
      const errs = p.id === "problems" ? probs.length : 0;
      return el("a", { class: "navitem" + (p.id === currentId ? " active" : ""), href: "#/" + p.id, "aria-current": p.id === currentId ? "page" : null, onclick: () => document.body.classList.remove("navopen") },
        sprite(p.icon, 18), p.title,
        errs ? el("span", { class: "num" + (probs.some((x) => x.level === "error") ? " bad" : "") }, errs) : null,
        p.group === "Set up" && pageDirty(p) ? el("span", { class: "dot", title: "Unsaved changes" }) : null);
    })));
  }
  const n = S.dirtyNames().length;
  saveBtn.className = "savebtn" + (n ? " dirty" : "");
  saveBtn.innerHTML = "";
  saveBtn.append(n ? "Review and save" : "All saved");
  if (n) saveBtn.append(el("span", { class: "n" }, n));
  undoBtn.disabled = !S.store.history.length;
  redoBtn.disabled = !S.store.future.length;
  undoBtn.title = S.store.history.length ? "Undo a change to " + S.store.history[S.store.history.length - 1].name + " (Ctrl+Z)" : "Nothing to undo";
  redoBtn.title = S.store.future.length ? "Redo a change to " + S.store.future[S.store.future.length - 1].name + " (Ctrl+Shift+Z)" : "Nothing to redo";
}

// The server folder, the live and RCON state, textures, theme and quit, at the foot of the navigation.
let texState = null;
function drawFoot() {
  navFoot.innerHTML = "";
  const dir = S.store.dir.split(/[\\/]/).slice(-3, -2)[0] || S.store.dir;
  const live = S.store.info.live, r = rconReady();
  navFoot.append(el("div", { class: "srv", title: S.store.dir }, el("span", { class: "led " + (live ? (S.store.live ? "on" : "off") : "") }), el("b", {}, dir),
    el("span", { class: "faint" }, live ? (r ? "RCON on" : "no RCON") : "configs only")));
  const t = texState;
  if (t && t.state === "running") navFoot.append(el("div", { class: "tex", title: t.step || "" }, el("span", { class: "faint" }, "Textures"), el("span", { class: "bar" }, el("i", { style: { width: (t.progress || 2) + "%" } }))));
  else if (t && !t.ready) navFoot.append(el("div", { class: "tex" }, el("span", { class: "faint" }, t.state === "error" ? "Textures failed." : "No textures."),
    el("button", { class: "linkbtn", onclick: downloadTextures, title: t.error || "Download Minecraft " + (t.wanted || "") + " textures from Mojang" }, t.state === "error" ? "Retry" : "Download")));
  navFoot.append(el("div", { class: "tools" }, themeBtn, iconBtn("power", "Quit Studio", quit, "sm")));
}
async function downloadTextures() {
  try { texState = await S.post("/api/textures/download", {}); } catch (e) { toast("Couldn't start the download: " + e.message, "err"); }
  drawFoot();
  watchTextures();
}
async function watchTextures() {
  while (texState && texState.state === "running") {
    await new Promise((r) => setTimeout(r, 1200));
    try { texState = await S.api("/api/textures"); } catch (e) { break; }
    drawFoot();
  }
  if (texState?.ready && texState.state === "done") {
    await loadSprites(texState);
    document.documentElement.classList.add("tex");
    brandMark.replaceChildren(sprite("player_head", 26));
    texState.state = "idle";
    drawFoot();
    route();
  }
}

// ---------------------------------------------------------------- routing
export function go(hash, key) {
  pendingKey = key || null;
  if (location.hash === hash) route(); else location.hash = hash;
}
async function route() {
  closeDrawer(true);
  closePop();
  let parts = (location.hash.replace(/^#\/?/, "") || "overview").split("/").map(decodeURIComponent);
  if (ALIASES[parts[0]]) parts = [...ALIASES[parts[0]].split("/"), ...parts.slice(1)];
  if (parts[0] === "review") { history.replaceState(null, "", "#/overview"); parts = ["overview"]; setTimeout(openReview, 60); }
  const page = byId[parts[0]] || byId.overview;
  const same = currentId === page.id;
  currentId = page.id;
  if (cleanup) { try { cleanup(); } catch (e) { /* ignore */ } cleanup = null; }
  const scroll = same ? main.scrollTop : 0;
  main.innerHTML = "";
  const root = el("div", { class: "page" });
  main.append(root);
  drawNav();
  document.title = page.title + " - Headcount Studio";
  try {
    const c = await page.render(root, parts.slice(1));
    if (typeof c === "function") cleanup = c;
  } catch (e) {
    console.error(e);
    root.append(el("div", { class: "note bad" }, "This page hit an error: " + e.message));
  }
  main.scrollTop = scroll;
  if (pendingKey) {
    const k = pendingKey; pendingKey = null;
    if (!k.includes("#")) setTimeout(() => { const t = document.getElementById(k); if (t) { t.scrollIntoView({ block: "center" }); t.classList.remove("flash"); void t.offsetWidth; t.classList.add("flash"); } }, 80);
    else setTimeout(() => { if (!highlight(k)) { const [file] = k.split("#"); if (page.id !== "files") go("#/files/" + file, k); } }, 80);
  }
}
window.addEventListener("hashchange", () => { main.scrollTop = 0; currentId = null; route(); });

/** The page that edits an option, from each page's `owns` list ("file", "file:top-key" or "file:a.b"). */
function pageFor(file, path) {
  let best = null, len = -1;
  for (const p of PAGES) for (const o of p.owns || []) {
    const [f, k] = o.split(":");
    if (f !== file) continue;
    if (!k && len < 0) { best = p.id; len = 0; }
    if (k && (path === k || path.startsWith(k + ".")) && k.length > len) { best = p.id; len = k.length; }
  }
  return best || "files";
}
export function goToOption(file, path) {
  const id = pageFor(file, path);
  go("#/" + id + (id === "files" ? "/" + file : ""), file + "#" + path);
}

// ---------------------------------------------------------------- search
const KIND = { page: "Page", action: "Do", option: "Setting", command: "Command", placeholder: "Placeholder", status: "Status", list: "Action list", shop: "Shop item", doc: "Doc", player: "Player", file: "File", title: "Title", word: "Word", recipe: "Recipe", ref: "Reference", outcome: "Outcome" };
const firstSentence = (t) => String(t || "").replace(/`/g, "").split(/(?<=\.)\s/)[0];
function paletteItems() {
  const items = [];
  const cat = S.store.catalog || {};
  for (const p of PAGES) if (!p.hidden && (p.group !== "Live server" || S.store.info.live)) items.push({ kind: "page", title: p.title, desc: p.group === "Home" ? "" : p.group, icon: p.icon, run: () => go("#/" + p.id) });
  for (const [k, n] of REF_TABS) items.push({ kind: "ref", title: n, desc: "Reference", icon: ICONS.reference, run: () => go("#/reference/" + k) });
  items.push({ kind: "action", title: "Review and save", desc: "Ctrl+S", run: () => openReview() },
    { kind: "action", title: "Undo", desc: "Ctrl+Z", run: doUndo }, { kind: "action", title: "Redo", desc: "Ctrl+Shift+Z", run: doRedo });
  if (rconReady()) items.push({ kind: "action", title: "Reload Headcount", desc: "/hca reload", run: () => rcon("hca reload", { confirm: "Headcount reads its files again. Open trades are cancelled and menus close first." }) },
    { kind: "action", title: "Run selftest", desc: "/hca selftest", run: async () => { const r = await rcon("hca selftest", { quiet: true }); if (r !== null) showReply("Selftest", r); } });
  for (const o of cat.options || []) if (o.default !== "(section)") items.push({ kind: "option", title: o.path, desc: o.file + "  " + firstSentence(o.help), run: () => goToOption(o.file, o.path) });
  for (const c of cat.commands || []) items.push({ kind: "command", title: c.syntax[0], desc: firstSentence(c.does), run: () => go("#/reference/commands") });
  for (const ph of cat.placeholders || []) items.push({ kind: "placeholder", title: "%headcount_" + ph.name + "%", desc: ph.description, run: () => go("#/reference/placeholders") });
  for (const [t, d] of GLOSSARY) items.push({ kind: "word", title: t, desc: d, icon: ICONS.glossary, run: () => go("#/learn", "term-" + t.toLowerCase().replace(/[^a-z0-9 -]/g, "").replace(/ /g, "-")) });
  (cat.recipes || []).forEach((r, i) => items.push({ kind: "recipe", title: r.title, desc: "Ready to apply", icon: ICONS.recipe, run: () => go("#/recipes/" + i) }));
  WALK_TITLES.forEach((t, i) => items.push({ kind: "recipe", title: t, desc: "Step by step", icon: ICONS.guide, run: () => { go("#/recipes"); setTimeout(() => openWalk(i), 150); } }));
  for (const n of Object.keys(S.get("statuses.yml", "statuses") || {})) items.push({ kind: "status", title: n, desc: "Statuses", icon: ICONS.status, run: () => go("#/statuses/" + n) });
  const plain = (v) => String(v || "").replace(/<[^>]*>/g, "");
  for (const n of Object.keys(S.get("titles.yml", "titles") || {})) items.push({ kind: "title", title: n, desc: plain(S.get("titles.yml", "titles." + n + ".display")), icon: S.get("titles.yml", "titles." + n + ".icon.material") || "name_tag", run: () => go("#/titles/" + n) });
  for (const [n, r] of Object.entries(S.get("outcomes.yml", "rules") || {})) items.push({ kind: "outcome", title: n, desc: plain(r?.display), icon: ICONS.outcome, run: () => go("#/outcomes/" + n) });
  for (const n of Object.keys(S.get("actions.yml", "lists") || {})) items.push({ kind: "list", title: n, desc: "Hooks", icon: "repeater", run: () => go("#/hooks/list/" + n) });
  for (const [t, T] of Object.entries(S.get("shop.yml", "tiers") || {})) for (const [e, E] of Object.entries(T?.entries || {})) items.push({ kind: "shop", title: e, desc: plain(E?.display?.name), icon: E?.display?.material || "paper", run: () => go("#/shop/" + t + "/" + e) });
  for (const d of cat.docs || []) items.push({ kind: "doc", title: d.title, desc: d.name, icon: ICONS.docs, run: () => go("#/docs/" + d.name) });
  for (const p of paletteItems.players || []) items.push({ kind: "player", title: p.name, desc: p.online ? "Online" : "", player: p.uuid, run: () => go("#/live-player/" + p.uuid) });
  for (const n of S.store.names) items.push({ kind: "file", title: n, desc: "Raw files", icon: ICONS.raw, run: () => go("#/files/" + n) });
  return items;
}
function palette() {
  closePop();
  const scrim = el("div", { class: "scrim" });
  const input = el("input", { type: "text", placeholder: "Search", "aria-label": "Search", spellcheck: "false" });
  const res = el("div", { class: "res", role: "listbox" });
  const box = el("div", { class: "palette", role: "dialog", "aria-label": "Search" }, input, res);
  const all = paletteItems();
  let sel = 0, shown = [];
  const close = () => { scrim.remove(); box.remove(); };
  const score = (it, q) => {
    const t = it.title.toLowerCase(), d = (it.desc || "").toLowerCase();
    if (t === q) return 100; if (t.startsWith(q)) return 80; if (t.includes(q)) return 60;
    if (q.split(/\s+/).every((w) => (t + " " + d).includes(w))) return 30 + (it.kind === "page" ? 5 : 0);
    return 0;
  };
  const draw = () => {
    const q = input.value.trim().toLowerCase();
    shown = q ? all.map((it) => [it, score(it, q)]).filter(([, s]) => s > 0).sort((a, b) => b[1] - a[1]).slice(0, 60).map(([it]) => it) : all.filter((x) => x.kind === "page" || x.kind === "action");
    sel = Math.min(sel, Math.max(0, shown.length - 1));
    res.innerHTML = "";
    shown.forEach((it, i) => res.append(el("div", { class: "it" + (i === sel ? " on" : ""), role: "option", onmousemove: () => { if (sel !== i) { sel = i; draw(); } }, onclick: () => { close(); it.run(); } },
      it.player ? face(it.player, 20) : it.icon ? sprite(it.icon, 20) : el("span", { class: "sprite", style: { "--s": "20px" } }), el("span", { class: "tt" }, it.title), el("span", { class: "dd" }, it.desc), el("span", { class: "k" }, KIND[it.kind]))));
    if (!shown.length) res.append(el("div", { class: "empty" }, "No match. Try a setting's name, like vault size."));
    res.querySelector(".on")?.scrollIntoView({ block: "nearest" });
  };
  input.addEventListener("input", () => { sel = 0; draw(); });
  input.addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown") { sel = Math.min(shown.length - 1, sel + 1); draw(); e.preventDefault(); }
    if (e.key === "ArrowUp") { sel = Math.max(0, sel - 1); draw(); e.preventDefault(); }
    if (e.key === "Enter" && shown[sel]) { close(); shown[sel].run(); }
    if (e.key === "Escape") { e.stopPropagation(); close(); }
  });
  scrim.addEventListener("click", close);
  document.body.append(scrim, box);
  draw();
  input.focus();
}

// ---------------------------------------------------------------- review and save
function openReview() {
  const dirty = S.dirtyNames();
  if (!dirty.length) { toast("Nothing to save."); return; }
  const body = el("div");
  const errs = runChecks().filter((x) => x.level === "error");
  if (errs.length) body.append(el("div", { class: "note warn", style: { marginBottom: "16px" } }, errs.length === 1 ? "Studio found a problem. You can still save. " : `Studio found ${errs.length} problems. You can still save. `,
    el("a", { href: "#/problems", onclick: () => closeDrawer() }, "See the problems")));
  for (const n of dirty) {
    const d = S.doc(n);
    body.append(el("section", { class: "filediff" }, el("header", {}, el("h3", { class: "grow" }, n), d.error ? el("span", { class: "badge bad" }, "Not valid YAML") : null,
      btn("Revert", async () => {
        if (await confirmDialog("Revert " + n + "?", "Your unsaved changes to it are dropped.", { ok: "Revert", danger: true })) { S.revert(n); closeDrawer(); route(); if (S.dirtyNames().length) openReview(); }
      }, "small ghost"), btn("Save", () => saveFiles([n]), "small")), diffView(d.saved, d.text)));
  }
  drawer("Review and save", body, { wide: true,
    footer: el("div", { class: "row", style: { width: "100%" } }, el("span", { class: "grow faint small" }, dirty.length + " file" + (dirty.length === 1 ? "" : "s") + " changed"),
      btn("Keep editing", () => closeDrawer(), "ghost"), btn(dirty.length > 1 ? "Save all " + dirty.length : "Save", () => saveFiles(dirty), "primary")) });
}
async function saveFiles(names) {
  const done = [];
  for (const n of names) {
    try { await S.save(n); done.push(n); }
    catch (e) {
      if (e.status === 409 && (await confirmDialog(n + " changed on disk", "Something else changed it after Studio loaded it. Overwrite it with your version?", { ok: "Overwrite", danger: true }))) { await S.save(n, true); done.push(n); }
      else if (e.status !== 409) toast("Couldn't save " + n + ": " + e.message, "err");
    }
  }
  closeDrawer();
  route();
  if (S.dirtyNames().length) setTimeout(openReview, 50);
  if (!done.length) return;
  const msg = "Saved " + done.join(", ") + ". The old version is under Backups.";
  if (rconReady()) toast(msg, "", { label: "Reload Headcount", run: reloadServer });
  else toast(msg);
}
async function reloadServer() {
  const r1 = await rcon("hca reload", { quiet: true });
  if (r1 === null) return;
  const r = await rcon("hca selftest", { quiet: true });
  if (r !== null) import("./pages-live.js").then((m) => m.showReply("Selftest after reload", r));
}

// ---------------------------------------------------------------- undo, problems, keys
function doUndo() { const n = S.undo(); if (n) { toast("Undid a change to " + n); route(); } }
function doRedo() { const n = S.redo(); if (n) { toast("Redid a change to " + n); route(); } }
function problems() { if (!problemsCache) { try { problemsCache = runChecks(); } catch (e) { console.error(e); problemsCache = []; } } return problemsCache; }
let navTimer = null;
S.on((ev) => {
  if (ev.type === "change" || ev.type === "saved" || ev.type === "load") {
    problemsCache = null;
    clearTimeout(navTimer);
    navTimer = setTimeout(drawNav, 200);
  }
});
document.addEventListener("keydown", (e) => {
  const typing = ["INPUT", "TEXTAREA", "SELECT"].includes(document.activeElement?.tagName);
  const mod = e.ctrlKey || e.metaKey;
  if (mod && e.key.toLowerCase() === "k") { e.preventDefault(); palette(); return; }
  if (mod && e.key.toLowerCase() === "s") { e.preventDefault(); openReview(); return; }
  if (mod && e.key.toLowerCase() === "z" && !typing) { e.preventDefault(); e.shiftKey ? doRedo() : doUndo(); return; }
  if (mod && e.key.toLowerCase() === "y" && !typing) { e.preventDefault(); doRedo(); return; }
  if (e.key === "Escape") { if (!closePop()) closeDrawer(); document.body.classList.remove("navopen"); return; }
  if (e.key === "/" && !typing) { e.preventDefault(); palette(); }
});
window.addEventListener("beforeunload", (e) => { if (S.dirtyNames().length) { e.preventDefault(); e.returnValue = ""; } });

// ---------------------------------------------------------------- theme, quit, heartbeat, live
const themeBtn = el("button", { class: "iconbtn sm" });
(function theme() {
  const order = ["auto", "light", "dark"];
  const names = { auto: "Theme: follows the system", light: "Theme: light", dark: "Theme: dark" };
  let t = "auto";
  try { t = localStorage.getItem("hc-theme") || "auto"; } catch (e) { /* private window */ }
  const apply = () => {
    if (t === "auto") document.documentElement.removeAttribute("data-theme"); else document.documentElement.setAttribute("data-theme", t);
    themeBtn.title = names[t]; themeBtn.setAttribute("aria-label", names[t]);
    themeBtn.innerHTML = ""; themeBtn.append(icon(t === "auto" ? "auto" : t === "light" ? "sun" : "moon"));
  };
  themeBtn.addEventListener("click", () => { t = order[(order.indexOf(t) + 1) % 3]; try { localStorage.setItem("hc-theme", t); } catch (e) { /* ignore */ } apply(); });
  apply();
})();
async function quit() {
  const dirty = S.dirtyNames().length;
  if (!(await confirmDialog(dirty ? "Quit with unsaved changes?" : "Quit Headcount Studio?", dirty ? "They will be lost." : "Open it again with the Headcount Studio launcher.", { ok: "Quit", danger: !!dirty }))) return;
  try { await S.post("/api/quit", {}); } catch (e) { /* it's gone */ }
  document.body.innerHTML = "<div class='empty' style='padding-top:30vh'>Headcount Studio has stopped. You can close this tab.</div>";
}
setInterval(() => fetch("/api/ping").catch(() => {}), 15000);
fetch("/api/ping").catch(() => {});
async function pollLive() {
  if (!S.store.info.live) return;
  try { S.emitLive(await S.api("/api/live/summary")); } catch (e) { S.store.live = null; }
  drawFoot();
  try { paletteItems.players = await S.api("/api/live/players"); } catch (e) { /* ignore */ }
}

// ---------------------------------------------------------------- start
window.studio = { go, goToOption, problems, refresh: () => route(), review: openReview };
(async () => {
  main.append(el("div", { class: "empty", style: { paddingTop: "30vh" } }, "Opening your Headcount configs"));
  try { await S.loadAll(); } catch (e) { main.innerHTML = ""; main.append(el("div", { class: "page" }, el("div", { class: "note bad" }, "Couldn't load the configs: " + e.message))); return; }
  texState = S.store.info.textures || null;
  if (await loadSprites(texState)) document.documentElement.classList.add("tex");
  brandMark.replaceChildren(sprite("player_head", 26));
  drawFoot();
  if (texState?.state === "running") watchTextures();
  await pollLive();
  setInterval(pollLive, 5000);
  route();
})();
