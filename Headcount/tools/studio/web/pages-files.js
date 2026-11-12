// Files pages: problems (checked as you edit), the raw files, and backups with import and export. Also the diff view
// that Review and save, recipes and backups share.
import { el, btn, icon, sprite, field, allFields, select, seg, drawer, closeDrawer, toast, confirmDialog, emptyBox, ICONS } from "./ui.js";
import { isObj, parseRange, parseDuration, compileFormula, diffHunks, formatDuration } from "./core.js";
import * as S from "./store.js";
import { pageHead, zoneInfo } from "./pages-setup.js";
import { types as actionTypes, listNames, normalize } from "./actions.js";
import { menuSlots } from "./pages-setup2.js";
import { runCommand, showReply } from "./pages-live.js";

const go = (h, key) => window.studio.go(h, key);
export const FILE_SPRITE = { "config.yml": "crafting_table", "integrations.yml": "comparator", "statuses.yml": "redstone_torch", "trade.yml": "bundle", "scoring.yml": "experience_bottle",
  "outcomes.yml": "totem_of_undying", "shop.yml": "emerald", "titles.yml": "name_tag", "actions.yml": "firework_rocket", "lang/en_US.yml": "writable_book" };

/** A diff: each change shows its removed lines, then its added lines, with a few lines around it. */
export function diffView(a, b) {
  const { hunks, added, removed } = diffHunks(a, b, 3);
  const box = el("div", { class: "diff" }, el("div", { class: "gap" }, added + removed === 0 ? "No differences" : `${removed} line${removed === 1 ? "" : "s"} removed, ${added} added`));
  hunks.forEach((h, i) => {
    if (i) box.append(el("div", { class: "gap" }, "..."));
    const out = [];
    for (let k = 0; k < h.length;) {
      if (h[k].op !== "+" && h[k].op !== "-") { out.push(h[k++]); continue; }
      const run = [];
      while (k < h.length && (h[k].op === "+" || h[k].op === "-")) run.push(h[k++]);
      out.push(...run.filter((d) => d.op === "-"), ...run.filter((d) => d.op === "+"));
    }
    for (const d of out) box.append(el("div", { class: "dl " + (d.op === "+" ? "add" : d.op === "-" ? "del" : "") }, el("span", { class: "n" }, d.op === "+" ? "+" : d.op === "-" ? "-" : d.b), el("span", {}, d.text || " ")));
  });
  return box;
}

// ============================================================================================
//  Problems
// ============================================================================================
const TRACKED_ONLY = ["head-protection", "extra-heads", "bounty", "shield", "deny", "force-title"];
const DURATION_KEYS = new Set(["min-playtime", "check-interval", "timeout", "countdown", "cooldown", "update-interval", "refresh-interval", "join-delay", "missed-warning-grace",
  "message-cooldown", "click-cooldown", "confirm-timeout", "before", "after", "duration", "killer-pickup-only"]);
export function runChecks() {
  const out = [];
  const cat = S.store.catalog || {};
  const add = (level, text, goHash, key) => out.push({ level, text, go: goHash, key });
  for (const n of S.store.names) if (S.doc(n).error) add("error", `${n} isn't valid YAML: ${S.doc(n).error}`, "#/files/" + n);
  const statuses = S.get("statuses.yml", "statuses") || {};
  const stNames = new Set(Object.keys(statuses));
  const checkStatus = (name, where, hash, key) => { const n = String(name).replace(/^!/, ""); if (n && !stNames.has(n) && !["always", "never"].includes(n)) add("error", `${where} uses the status ${n}, which isn't in statuses.yml.`, hash, key); };
  for (const s of S.get("config.yml", "vault.block-movement.statuses") || []) checkStatus(s, "The vault", "#/vault", "config.yml#vault.block-movement.statuses");
  for (const [k, w] of [["block-request", "Trade requests"], ["block-accept", "Accepting trades"], ["cancel-open", "Open trades"]]) for (const s of S.get("trade.yml", "statuses." + k) || []) checkStatus(s, w, "#/trading", "trade.yml#statuses." + k);
  const srcs = new Set((cat.statuses || []).map((x) => x.source));
  for (const [n, s] of Object.entries(statuses)) {
    if (!isObj(s)) continue;
    if (s.source && srcs.size && !srcs.has(s.source) && !["all-of", "any-of", "not", "always", "never"].includes(s.source)) add("error", `The status ${n} reads from "${s.source}", which Headcount doesn't have.`, "#/statuses/" + n);
    for (const x of [...(s.statuses || []), s.status, s.fallback].filter(Boolean)) checkStatus(x, "The status " + n, "#/statuses/" + n);
  }
  const deps = (n, seen = new Set()) => { if (seen.has(n)) return true; seen.add(n); const s = statuses[n]; if (!isObj(s)) return false; return [...(s.statuses || []), s.status].filter(Boolean).some((x) => deps(String(x).replace(/^!/, ""), new Set(seen))); };
  for (const n of stNames) if (deps(n)) { add("error", `The status ${n} depends on itself through other statuses.`, "#/statuses/" + n); break; }
  const T = actionTypes(), lists = new Set(listNames());
  const titles = new Set(Object.keys(S.get("titles.yml", "titles") || {}));
  const checkList = (v, where, hash) => {
    const arr = Array.isArray(v) ? v : v === undefined || v === null ? [] : [v];
    if (typeof v === "string" && !lists.has(v) && !T[v]) { add("error", `${where} runs the list ${v}, which isn't in actions.yml.`, hash); return; }
    for (const item of arr) {
      if (typeof item === "string" && !item.includes(":") && !lists.has(item) && !T[item]) add("error", `${where} runs ${item}, which is neither an action list nor an action.`, hash);
      const c = normalize(item);
      if (c.type === "?" || (c.form !== "ref" && !T[c.type])) add("error", `${where} has an action Headcount doesn't know: ${c.type}.`, hash);
      if (TRACKED_ONLY.includes(c.type) && !c.data.duration) add("warn", `${where}: ${c.type} does nothing without a duration.`, hash);
      if (["grant-title", "force-title", "revoke-title"].includes(c.type) && c.data.title && !titles.has(String(c.data.title))) add("warn", `${where} uses the title ${c.data.title}, which isn't in titles.yml.`, hash);
      if (c.type === "run" && c.data.list && !lists.has(c.data.list)) add("error", `${where} runs the list ${c.data.list}, which isn't in actions.yml.`, hash);
    }
  };
  for (const [id, r] of Object.entries(S.get("outcomes.yml", "rules") || {})) { if (!isObj(r)) continue; checkList(r.actions, "The outcome " + id, "#/outcomes/" + id); if (!parseRange(r.score).ok) add("error", `The outcome ${id} has a score range Studio can't read: ${r.score}.`, "#/outcomes/" + id); }
  for (const [n, v] of Object.entries(S.get("actions.yml", "lists") || {})) checkList(v, "The list " + n, "#/lists/" + n);
  const hooks = S.get("config.yml", "hooks") || {};
  for (const [h, v] of Object.entries(hooks.player || {})) checkList(v, "The " + h + " hook", "#/hooks");
  for (const h of ["cycle-start", "cycle-end"]) { checkList(hooks[h]?.global, "The " + h + " hook", "#/hooks"); checkList(hooks[h]?.["per-player"]?.actions, "The " + h + " hook (each player)", "#/hooks"); }
  for (const [w, v] of Object.entries(hooks.warnings || {})) { if (isNaN(parseDuration(v?.before))) add("error", `The warning ${w} has no time before the end.`, "#/hooks"); checkList(v?.global, "The warning " + w, "#/hooks"); }
  const { size: shopSize, buttons } = menuSlots("shop.yml", "gui");
  for (const [t, T2] of Object.entries(S.get("shop.yml", "tiers") || {})) {
    const used = {};
    for (const [id, e] of Object.entries(T2?.entries || {})) {
      if (!isObj(e)) continue;
      const where = "#/shop/" + t + "/" + id;
      checkList(e.actions, `The shop item ${id}`, where);
      const k = (e.page || 1) + ":" + (e.slot ?? 0);
      if (used[k]) add("error", `The shop items ${used[k]} and ${id} share slot ${e.slot ?? 0} on page ${e.page || 1} of ${t}.`, where);
      used[k] = id;
      if ((e.slot ?? 0) >= shopSize) add("error", `The shop item ${id} is in slot ${e.slot}, past the end of the ${shopSize}-slot menu.`, where);
      else if (buttons[e.slot ?? 0]) add("warn", `The shop item ${id} sits under the ${buttons[e.slot].id} button.`, where);
      for (const ti of e.requires?.titles || []) if (!titles.has(String(ti))) add("warn", `The shop item ${id} needs the title ${ti}, which doesn't exist.`, where);
    }
  }
  for (const k of ["pvp", "placeholders", "economy", "permissions", "moderation", "discord", "bedrock", "player-state"]) if ((S.get("integrations.yml", k + ".provider") ?? "") === "auto") add("warn", `${k} is set to auto, which isn't an option any more. Pick who handles it.`, "#/integrations");
  if (!zoneInfo(S.get("config.yml", "cycle.time-zone") ?? "UTC")) add("error", `Headcount doesn't know the time zone ${S.get("config.yml", "cycle.time-zone")} and would use UTC.`, "#/cycle", "config.yml#cycle.time-zone");
  const f = compileFormula(String(S.get("scoring.yml", "formula") ?? ""));
  if (!f.ok) add("error", "The score formula has an error: " + f.error + ".", "#/scoring");
  const walk = (file, v, p) => {
    if (isObj(v)) { for (const [k, x] of Object.entries(v)) walk(file, x, p ? p + "." + k : k); return; }
    const k = p.split(".").pop();
    if (p === "pvp.included.messages.countdown") return; // a display mode, not a duration
    if (DURATION_KEYS.has(k) && typeof v === "string" && !["cycle", "death", "permanent", "manual", "instant"].includes(v) && !v.startsWith("cycles:") && isNaN(parseDuration(v))) add("error", `${p} in ${file} should be a length of time like 30s, 10m or 2h, not "${v}".`, null, file + "#" + p);
  };
  for (const file of ["config.yml", "trade.yml", "integrations.yml"]) walk(file, S.doc(file)?.data, "");
  for (const [file, base, name] of [["config.yml", "vault.gui", "vault"], ["config.yml", "history.gui", "history"], ["trade.yml", "gui", "trade"], ["shop.yml", "gui", "shop"], ["shop.yml", "confirm-gui", "purchase"], ["titles.yml", "gui", "titles"]]) {
    const L = S.get(file, base);
    if (!isObj(L)) continue;
    const size = Math.max(1, Math.min(6, +L.rows || 6)) * 9;
    for (const [id, b] of Object.entries(L.buttons || {})) if (isObj(b) && b.slot !== undefined && +b.slot >= size) add("error", `The ${name} menu's ${id} button is in slot ${b.slot}, below its last row.`, "#/menus/" + ({ purchase: "confirm" }[name] || name));
  }
  return out;
}
function problemsPage(root) {
  const list = runChecks();
  root.append(pageHead("Problems", null, btn("Run selftest", async () => { const r = await runCommand("hca selftest", { quiet: true }); if (r !== null) showReply("Selftest", r); }, "ghost")));
  if (!list.length) { root.append(emptyBox("lime_dye", "Nothing wrong that Studio can see", "Studio checks the files as you edit them. The server's own /hca selftest also checks items, menus and messages against the game.")); return; }
  const errs = list.filter((p) => p.level === "error"), warns = list.filter((p) => p.level !== "error");
  const rows = (ps) => el("div", { class: "items" }, ps.map((p) => el("div", { class: "item" + (p.go || p.key ? "" : " static"), onclick: () => (p.go ? go(p.go, p.key) : p.key && window.studio.goToOption(...p.key.split("#"))) },
    el("span", { class: "led", style: { background: p.level === "error" ? "var(--bad)" : "var(--warn)" } }), el("div", { class: "main" }, p.text), p.go || p.key ? icon("right") : null)));
  if (errs.length) root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, errs.length === 1 ? "1 error" : errs.length + " errors")), rows(errs)));
  if (warns.length) root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Warnings")), rows(warns)));
}

// ============================================================================================
//  Raw files
// ============================================================================================
function filesPage(root, params) {
  const name = params.join("/") || "config.yml";
  const d = S.doc(name);
  if (!d) { root.append(pageHead("Raw files"), emptyBox("paper", "No file called " + name, null, btn("Open config.yml", () => go("#/files/config.yml")))); return; }
  let mode = d.parsed.entries ? "settings" : "text";
  const modes = seg([["settings", "Settings"], ["text", "Text"], ["default", "Changes from the default"]], mode, (m) => { mode = m; draw(); });
  root.append(pageHead("Raw files", null, select(S.store.names, name, (n) => go("#/files/" + n)), modes));
  const body = el("div");
  root.append(body);
  const draw = () => {
    body.innerHTML = "";
    if (d.parsed.header?.trim()) body.append(el("details", { class: "more", style: { marginBottom: "12px" } }, el("summary", {}, "What " + name + " is for"), el("pre", { class: "small muted", style: { whiteSpace: "pre-wrap", fontFamily: "var(--sans)" } }, d.parsed.header)));
    if (mode === "default") { body.append(S.doc(name).defaultText ? diffView(S.doc(name).defaultText, S.doc(name).text) : emptyBox("paper", "No bundled default", "Headcount doesn't ship a default " + name + ".")); return; }
    if (mode === "text" || !d.parsed.entries) {
      const ta = el("textarea", { spellcheck: "false", "aria-label": name, style: { minHeight: "72vh", fontSize: "13px" } });
      ta.value = S.doc(name).text;
      const err = el("div");
      const check = () => { err.innerHTML = ""; if (S.doc(name).error) err.append(el("div", { class: "note bad", style: { marginTop: "8px" } }, "Not valid YAML: " + S.doc(name).error)); };
      ta.addEventListener("input", () => { S.setText(name, ta.value, name + "#raw"); check(); });
      body.append(ta, err);
      check();
      return;
    }
    for (const top of Object.keys({ ...(S.doc(name).defaults || {}), ...(S.doc(name).data || {}) })) {
      if (top === "config-version") continue;
      const v = S.get(name, top) ?? S.def(name, top);
      body.append(el("details", { class: "more", open: Object.keys(S.doc(name).data || {}).length < 6 }, el("summary", {}, top), isObj(v) ? allFields(name, top) : el("div", { class: "sheet" }, field(name, top))));
    }
  };
  draw();
}

// ============================================================================================
//  Backups, import and export
// ============================================================================================
async function backupsPage(root) {
  root.append(pageHead("Backups"));
  const file = el("input", { type: "file", accept: ".zip", hidden: true, "aria-label": "A .zip of config files" });
  const doImport = async () => {
    if (!file.files[0]) return;
    try {
      const r = await fetch("/api/import", { method: "POST", headers: { "X-Studio": "1" }, body: file.files[0] }).then((x) => x.json());
      if (r.error) throw new Error(r.error);
      const names = Object.keys(r.files || {});
      if (!names.length) { toast("That .zip has no Headcount config files in it.", "err"); return; }
      for (const n of names) S.setText(n, r.files[n], null);
      toast("Loaded " + names.join(", ") + " as unsaved changes.");
      window.studio.review();
    } catch (e) { toast("Couldn't import it: " + e.message, "err"); }
    file.value = "";
  };
  file.addEventListener("change", doImport);
  const row = (label, hint, ...ctl) => el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, label), hint ? el("div", { class: "hint" }, hint) : null), el("div", { class: "ctl" }, el("div", { class: "row" }, ...ctl)));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Export and import")), el("div", { class: "sheet" },
    row("Export", "Every config file as it is saved now, in one .zip.", el("a", { class: "btn", href: "/api/export", download: "" }, "Download the .zip")),
    row("Import", "The files land as unsaved changes, so you see the difference first.", file, btn("Choose a .zip", () => file.click())))));
  let list = [];
  try { list = await S.api("/api/backups"); } catch (e) { root.append(el("div", { class: "note bad" }, e.message)); return; }
  const files = list.filter((b) => b.file);
  if (!files.length) { root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Earlier versions")), emptyBox("barrel", "No backups yet", "Each time Studio saves a file, it keeps the version it replaced in backups/studio/ in the server folder."))); return; }
  const filter = select([["", "Every file"], ...[...new Set(files.map((b) => b.file))].map((f) => [f, f])], "", () => draw());
  const box = el("div");
  const draw = () => {
    box.innerHTML = "";
    const shown = files.filter((b) => !filter.value || b.file === filter.value).slice(0, 200);
    box.append(el("div", { class: "items" }, shown.map((b) => el("div", { class: "item", onclick: () => compare(b) }, sprite(FILE_SPRITE[b.file] || "paper", 26),
      el("div", { class: "main" }, el("div", { class: "t1" }, b.file), el("div", { class: "t2" }, b.path)),
      el("div", { class: "end", title: new Date(b.mtime * 1000).toLocaleString() }, formatDuration(Date.now() - b.mtime * 1000) + " ago")))));
  };
  const compare = async (b) => {
    const r = await S.api("/api/backup?path=" + encodeURIComponent(b.path));
    drawer(b.file + " from " + new Date(b.mtime * 1000).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }), el("div", {}, el("p", { class: "muted" }, "Red lines are in the file now, green lines are in this backup."), diffView(S.doc(b.file).text, r.text)),
      { wide: true, footer: el("div", { class: "row", style: { width: "100%" } }, el("span", { class: "grow" }), btn("Close", () => closeDrawer(), "ghost"),
        btn("Restore this version", () => { S.setText(b.file, r.text, null); closeDrawer(); toast("Restored " + b.file + " as an unsaved change."); window.studio.refresh(); }, "primary")) });
  };
  draw();
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Earlier versions"), el("div", { class: "side" }, filter)), box));
}

export const filesPages = [
  { id: "problems", group: "Files", title: "Problems", icon: ICONS.problems, render: problemsPage },
  { id: "files", group: "Files", title: "Raw files", icon: ICONS.raw, render: filesPage },
  { id: "backups", group: "Files", title: "Backups", icon: ICONS.backups, render: backupsPage },
];
export const filesAliases = { io: "backups" };
