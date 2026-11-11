// Set up pages, part 1: Overview, Cycle, Vault and heads, Scoring, Outcomes.
import { el, btn, icon, iconBtn, field, group, subhead, mcText, miniInline, tooltip, chest, seg, drawer, closeDrawer, promptDialog, confirmDialog,
  sprite, face, SAMPLE, ICONS, humanize, select, numberCtl, miniCtl, switchCtl } from "./ui.js";
import { compileFormula, roundScore, parseRange, describeRange, parseDuration, formatDuration, isObj, renderMini, stripMini } from "./core.js";
import * as S from "./store.js";
import { actionList, conditionsCtl, rangeCtl, summarize } from "./actions.js";

export const C = (p) => S.get("config.yml", p) ?? S.def("config.yml", p);
const go = (h, key) => window.studio.go(h, key);
const link = (text, hash, key, cls) => el("a", { href: hash, class: cls || null, onclick: (e) => { e.preventDefault(); go(hash, key); } }, text);
export const pageHead = (title, sub, ...actions) => el("div", { class: "pagehead" }, el("div", { class: "grow" }, el("h1", {}, title), sub ? el("div", { class: "sub" }, sub) : null),
  actions.length ? el("div", { class: "acts" }, actions) : null);
export const layout = (main, aside, wide) => el("div", { class: "layout" + (wide ? " wide-preview" : "") }, el("div", {}, main), el("div", { class: "aside" }, aside));
/** A preview block in the aside: a quiet heading over the in-game view. */
export const preview = (title, ...body) => el("section", {}, title ? el("div", { class: "row", style: { marginBottom: "10px" } }, el("h2", { style: { fontSize: "16px" } }, title)) : null, ...body);
export const mcName = (src) => el("span", { class: "mcbox" }, el("span", { class: "mcline", html: renderMini(src ?? "", { vars: SAMPLE }) }));

// ============================================================================================
//  Overview
// ============================================================================================
function overview(root) {
  const live = S.store.live;
  const formula = String(S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula"));
  const f = compileFormula(formula);
  const ex = (u, a) => roundScore(f.eval({ unique: u, active: a, total: u * 3, own: 9, rank: 3, percentile: 60, players: a + 4, kills: 0, deaths: 0, trades: 0, streak: 0, points: 0, bonus: 0 }), S.get("scoring.yml", "rounding"));
  const rules = Object.entries(S.get("outcomes.yml", "rules") || {}).filter(([, r]) => r && r.enabled !== false);
  const worst = rules.filter(([, r]) => parseRange(r.score).max < 0).sort((a, b) => parseRange(b[1].score).max - parseRange(a[1].score).max)[0];
  const tiers = Object.entries(S.get("shop.yml", "tiers") || {});
  const entries = tiers.reduce((n, [, t]) => n + Object.keys(t?.entries || {}).length, 0);
  const V = (v, hash, key) => link(String(v), hash, key, "v");
  const pvp = S.get("integrations.yml", "pvp.provider") ?? "included";
  root.append(pageHead("Overview"));
  const lede = el("p", { class: "lede" },
    "Cycles last ", V(C("cycle.length-days") + " days", "#/cycle", "config.yml#cycle.length-days"), " and roll over at ", V(C("cycle.end-time") + " " + C("cycle.time-zone"), "#/cycle", "config.yml#cycle.end-time"),
    ". Each player starts a cycle with ", V(C("vault.starting-heads") + " of their own heads", "#/vault", "config.yml#vault.starting-heads"), " in a ", V(C("vault.size") + "-slot vault", "#/vault", "config.yml#vault.size"), ". ",
    f.ok ? ["With 12 active players, 4 unique heads score ", el("b", {}, ex(4, 12)), " and 10 score ", el("b", {}, ex(10, 12)), " under ", V("the formula", "#/scoring"), ". "] : ["The ", V("score formula", "#/scoring"), " has an error. "],
    worst ? ["A score of ", el("b", {}, describeRange(worst[1].score)), " brings ", V(stripMini(worst[1].display || worst[0]), "#/outcomes"), ". "] : "",
    "The shop has ", V(tiers.length + " tiers", "#/shop"), " and ", entries, " items, and PvP is handled by ", V(pvp === "included" ? "Headcount" : pvp === "none" ? "nobody" : pvp, "#/integrations"), ".");
  const mainCol = el("div", {}, lede);
  const problems = window.studio.problems();
  if (problems.length) {
    const box = el("div", { class: "items" });
    for (const pr of problems.slice(0, 6)) box.append(el("div", { class: "item", onclick: () => pr.go && go(pr.go, pr.key) },
      el("span", { class: "led " + (pr.level === "error" ? "off" : ""), style: pr.level === "error" ? null : { background: "var(--warn)" } }), el("div", { class: "main" }, pr.text), pr.go ? icon("right") : null));
    if (problems.length > 6) box.append(el("button", { class: "addrow", onclick: () => go("#/problems") }, "All " + problems.length + " problems"));
    mainCol.append(el("section", { class: "group", style: { marginTop: "28px" } }, el("header", {}, el("h2", {}, "Needs a look")), box));
  }
  const dirty = S.dirtyNames();
  if (dirty.length) mainCol.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Not saved yet")),
    el("div", { class: "sheet pad", style: { padding: "14px 18px" } }, el("div", { class: "row" }, el("span", { class: "grow" }, dirty.join(", ")), btn("Review and save", () => window.studio.review(), "primary")))));
  const steps = checklist();
  if (steps) mainCol.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "First steps")), steps));
  const aside = [];
  if (live?.cycle) {
    const c = live.cycle;
    const pct = Math.max(0, Math.min(100, (Date.now() - c.start_ms) / (c.end_ms - c.start_ms) * 100));
    aside.push(el("section", { class: "sheet", style: { padding: "16px 18px" } },
      el("div", { class: "row" }, el("h2", { class: "grow", style: { fontSize: "18px" } }, link("Cycle " + c.id, "#/live", null, "plainlink"))),
      el("div", { class: "livecycle", style: { display: "block" } }, el("div", { class: "prog" }, el("i", { style: { width: pct.toFixed(1) + "%" } })),
        el("div", { class: "row small muted" }, el("span", { class: "grow" }, formatDuration(c.end_ms - Date.now()) + " left"), el("span", {}, "ends " + new Date(c.end_ms).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })))),
      el("div", { style: { marginTop: "14px" } }, el("div", { class: "small muted", style: { marginBottom: "6px" } }, live.playerCount ? `${live.online.length ? live.online.length + " online, " : "Nobody online, "}${live.activeCount} of ${live.playerCount} active this cycle` : "Nobody has joined yet."),
        el("div", { class: "heads-row" }, live.online.slice(0, 24).map((p) => el("a", { href: "#/live-player/" + p.uuid, title: p.name }, face(p.uuid, 28))))),
      live.top?.[0] ? el("div", { class: "row", style: { marginTop: "14px" } }, face(live.top[0].uuid, 24), el("span", { class: "grow" }, el("b", {}, live.top[0].name), " leads with ", live.top[0].unique, " unique heads")) : null));
  }
  root.append(layout(mainCol, aside));
}
export function countChanged(file) {
  const d = S.doc(file); let n = 0;
  const walk = (a, b) => { if (isObj(a) && isObj(b)) { for (const k of Object.keys(a)) walk(a[k], b[k]); } else if (JSON.stringify(a) !== JSON.stringify(b)) n++; };
  walk(d?.data, d?.defaults);
  return n;
}
function checklist() {
  const items = [["cycle", "Pick a cycle length and check the time zone", "#/cycle"], ["vault", "Choose the vault size and starting heads", "#/vault"],
    ["scoring", "Try a few numbers on the formula", "#/scoring"], ["shop", "Look through the shop", "#/shop"], ["integrations", "Check who handles PvP and permissions", "#/integrations"],
    ["learn", "Read how a cycle works", "#/learn"], ["review", "Save your changes", "#/review"], ["reload", "Reload the server and run selftest", S.store.info.live ? "#/live" : "#/problems"]];
  let done = {};
  try { done = JSON.parse(localStorage.getItem("hc-checklist") || "{}"); } catch (e) { /* private window */ }
  if (items.every(([id]) => done[id])) return null;
  const box = el("div", { class: "sheet" });
  for (const [id, text, hash] of items) {
    const cb = el("input", { type: "checkbox", "aria-label": "Done: " + text }); cb.checked = !!done[id];
    const row = el("div", { class: "check" + (cb.checked ? " done" : "") }, cb, el("span", { class: "grow" }, link(text, hash)));
    cb.addEventListener("change", () => { done[id] = cb.checked; row.classList.toggle("done", cb.checked); try { localStorage.setItem("hc-checklist", JSON.stringify(done)); } catch (e) { /* ignore */ } });
    box.append(row);
  }
  return box;
}

// ============================================================================================
//  Cycle
// ============================================================================================
const ABBR = { UTC: 0, GMT: 0, Z: 0, WET: 0, WEST: 60, BST: 60, CET: 60, CEST: 120, EET: 120, EEST: 180, MSK: 180, IST: 330, SGT: 480, AWST: 480, JST: 540, KST: 540,
  ACST: 570, AEST: 600, AEDT: 660, NZST: 720, NZDT: 780, HST: -600, AKST: -540, AKDT: -480, PST: -480, PDT: -420, MST: -420, MDT: -360, CST: -360, CDT: -300, EST: -300, EDT: -240,
  AST: -240, ADT: -180, NST: -210, NDT: -150 };
/** {iana} or {offset minutes}, or null, for the zone forms Headcount accepts. */
export function zoneInfo(z) {
  const s = String(z || "").trim();
  if (s.toLowerCase() === "system") return { iana: Intl.DateTimeFormat().resolvedOptions().timeZone, label: "this computer's zone" };
  if (ABBR[s.toUpperCase()] !== undefined) return { offset: ABBR[s.toUpperCase()], label: "the same offset all year" };
  const m = /^(?:utc|gmt|ut)?\s*([+-])\s*(\d{1,2})(?::?(\d{2}))?$/i.exec(s);
  if (m) return { offset: (m[1] === "-" ? -1 : 1) * (+m[2] * 60 + +(m[3] || 0)), label: "the same offset all year" };
  try { new Intl.DateTimeFormat("en", { timeZone: s }).format(); return { iana: s, label: "follows daylight saving" }; } catch (e) { return null; }
}
function wallClock(zi, t = Date.now(), opts = { weekday: "short", hour: "2-digit", minute: "2-digit" }) {
  if (!zi) return "";
  if (zi.iana) return new Intl.DateTimeFormat(undefined, { timeZone: zi.iana, ...opts }).format(new Date(t));
  return new Intl.DateTimeFormat(undefined, { timeZone: "UTC", ...opts }).format(new Date(t + zi.offset * 60000));
}
/** Milliseconds since the epoch for a wall-clock date and time in a zone. */
function zoned(y, mo, d, hh, mm, zi) {
  const guess = Date.UTC(y, mo, d, hh, mm);
  if (!zi || zi.offset !== undefined) return guess - (zi?.offset || 0) * 60000;
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: zi.iana, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).formatToParts(new Date(guess));
  const g = Object.fromEntries(parts.map((p) => [p.type, p.value]));
  const asUtc = Date.UTC(+g.year, +g.month - 1, +g.day, +g.hour % 24, +g.minute);
  return guess - (asUtc - guess);
}
function cyclePage(root) {
  const liveBtn = S.store.info.live ? btn([sprite("recovery_compass", 18), "Cycle tools"], () => go("#/live-cycle")) : null;
  root.append(pageHead("Cycle", null, liveBtn));
  const aside = el("div", { style: { display: "grid", gap: "22px" } });
  const draw = () => drawSchedule(aside);
  const lenRow = field("config.yml", "cycle.length-days", { hint: "Applies from the next cycle.", onChange: draw });
  const presets = seg([[1, "Day"], [7, "Week"], [14, "2 weeks"], [30, "30 days"]], C("cycle.length-days"), (v) => { S.set("config.yml", "cycle.length-days", +v); lenRow.querySelector("input[type=number]").value = v; draw(); refreshUnsaved(lenRow, "cycle.length-days"); });
  lenRow.querySelector(".ctl").prepend(presets);
  lenRow.querySelector("input[type=number]").addEventListener("input", (e) => presets.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b.textContent === ({ 1: "Day", 7: "Week", 14: "2 weeks", 30: "30 days" })[e.target.value])));
  // start-date: "auto" or a date
  const sd = String(C("cycle.start-date") ?? "auto");
  const startRow = el("div", { class: "field", "data-key": "config.yml#cycle.start-date" });
  const dateInp = el("input", { type: "date", value: sd === "auto" ? "" : sd, oninput: (e) => { if (e.target.value) { S.set("config.yml", "cycle.start-date", e.target.value); draw(); refreshUnsaved(startRow, "cycle.start-date"); } } });
  dateInp.hidden = sd === "auto";
  startRow.append(el("div", { class: "lab" }, el("label", { class: "name" }, "First cycle starts")), el("div", { class: "ctl" },
    seg([["auto", "Right away"], ["date", "On a date"]], sd === "auto" ? "auto" : "date", (m) => {
      dateInp.hidden = m !== "date";
      if (m === "auto") S.set("config.yml", "cycle.start-date", "auto");
      else { if (!dateInp.value) dateInp.value = new Date(Date.now() + 86400000).toISOString().slice(0, 10); S.set("config.yml", "cycle.start-date", dateInp.value); }
      draw(); refreshUnsaved(startRow, "cycle.start-date");
    }), dateInp));
  refreshUnsaved(startRow, "cycle.start-date");
  const clockNote = el("div", { class: "note-inline" });
  const clock = () => { const zi = zoneInfo(S.get("config.yml", "cycle.time-zone") ?? "UTC"); clockNote.textContent = zi ? `${wallClock(zi)} there now, ${zi.label}` : "Headcount doesn't know this zone and would use UTC."; clockNote.style.color = zi ? "" : "var(--bad)"; };
  const tz = field("config.yml", "cycle.time-zone", { onChange: () => { clock(); draw(); }, render: () => clockNote });
  clock();
  root.append(layout([
    group("Schedule", lenRow, startRow, field("config.yml", "cycle.end-time", { onChange: draw }), tz),
    group("Activity", field("config.yml", "activity.min-playtime", { hint: "Only active players get a score." }), field("config.yml", "activity.check-interval")),
  ], aside));
  draw();
}
function refreshUnsaved(row, path) { row.classList.toggle("unsaved", JSON.stringify(S.get("config.yml", path)) !== JSON.stringify(S.savedValue("config.yml", path))); }
function drawSchedule(box) {
  box.innerHTML = "";
  const days = +C("cycle.length-days") || 30;
  const [hh, mm] = String(C("cycle.end-time") || "00:00").split(":").map(Number);
  const zone = S.get("config.yml", "cycle.time-zone") ?? "UTC";
  const zi = zoneInfo(zone) || { offset: 0 };
  const sdv = String(S.get("config.yml", "cycle.start-date") ?? "auto");
  const live = S.store.live?.cycle;
  let start;
  if (live) start = live.end_ms;
  else if (sdv === "auto") { const t = new Date(); start = zoned(t.getUTCFullYear(), t.getUTCMonth(), t.getUTCDate(), hh || 0, mm || 0, zi); }
  else { const [y, mo, d] = sdv.split("-").map(Number); start = zoned(y, mo - 1, d, hh || 0, mm || 0, zi); }
  const fmt = (t) => wallClock(zi, t, { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
  const rows = el("div", { class: "items" });
  if (live) rows.append(el("div", { class: "item", style: { cursor: "default" } }, el("div", { class: "main" }, el("div", { class: "t1" }, "Cycle " + live.id + ", running"), el("div", { class: "t2" }, "Ends " + fmt(live.end_ms)))));
  for (let k = 0; k < 3; k++) {
    const end = start + days * 86400000;
    rows.append(el("div", { class: "item", style: { cursor: "default" } }, el("div", { class: "main" }, el("div", { class: "t1" }, live ? "Cycle " + (live.id + k + 1) : k ? "Then" : sdv === "auto" ? "If it started today" : "First cycle"),
      el("div", { class: "t2" }, fmt(start) + " to " + fmt(end)))));
    start = end;
  }
  box.append(preview("Next cycles", rows, el("div", { class: "small faint", style: { marginTop: "6px" } }, "Times in " + zone + ".")));
  const tl = el("div", { class: "timeline" }, el("div", { class: "bar" }));
  const total = days * 86400000;
  let lastX = -100;
  const marks = Object.entries(C("hooks.warnings") || {}).map(([id, w]) => [id, parseDuration(w?.before), w]).filter(([, b, w]) => isFinite(b) && w?.enabled !== false)
    .map(([id, b]) => [id, Math.max(0, Math.min(100, (1 - b / total) * 100))]).sort((a, b) => a[1] - b[1]);
  for (const [id, x] of marks) {
    const show = x - lastX > 7;
    if (show) lastX = x;
    tl.append(el("div", { class: "mk", style: { left: x + "%" }, title: id + " before the end" }, el("span", {}, show ? id : "")));
  }
  tl.append(el("div", { class: "end", style: { left: 0 } }, "Start"), el("div", { class: "end", style: { right: 0 } }, "End"));
  box.append(preview("Warnings in a cycle", el("a", { href: "#/hooks", class: "sheet", style: { display: "block", padding: "8px 12px", color: "inherit", textDecoration: "none" } }, tl)));
}

// ============================================================================================
//  Vault and heads
// ============================================================================================
const HEAD_STATES = [["", "Current", "From this cycle"], ["expired-", "Expired", "From an older cycle"], ["defunct-", "Defunct", "From before a reset"]];
function vaultPage(root) {
  root.append(pageHead("Vault and heads"));
  let state = "";
  const aside = el("div", { style: { display: "grid", gap: "22px" } });
  const drawPrev = () => {
    aside.innerHTML = "";
    const size = Math.max(1, +C("vault.size") || 27), start = Math.min(size, +C("vault.starting-heads") || 0);
    const shown = Math.min(size, 54);
    const rows = Math.min(6, Math.ceil(shown / 9));
    const vars = { owner: "Steve", unique: 1, total: start, size, used: start, score: -2 };
    const tip = () => tooltip(C("heads.item.name"), C("heads.item.lore"), { owner: "Steve", cycle: 3 });
    aside.append(preview("At the start of a cycle", chest(rows, C("vault.gui.title"), (i) => i >= shown ? { item: C("vault.gui.locked-slot.material") || "BLACK_STAINED_GLASS_PANE", cls: "dim" }
      : i < start ? { item: { material: "PLAYER_HEAD", glint: C("heads.item.glint") }, tip } : null, { vars }),
      size > 54 ? el("div", { class: "small faint", style: { marginTop: "6px" } }, `${size} slots, shown in pages of 45.`) : null));
    const [p, title] = HEAD_STATES.find((h) => h[0] === state);
    aside.append(preview(title + " head", el("div", { class: "row", style: { alignItems: "flex-start", flexWrap: "nowrap" } }, sprite({ material: "PLAYER_HEAD", glint: !p && C("heads.item.glint") }, 48),
      tooltip(C("heads.item." + p + "name"), C("heads.item." + p + "lore"), { owner: "Steve", cycle: p === "expired-" ? 2 : 3 }))));
  };
  const headBox = el("div");
  const drawHead = () => {
    headBox.innerHTML = "";
    headBox.append(field("config.yml", "heads.item." + state + "name", { onChange: drawPrev }), field("config.yml", "heads.item." + state + "lore", { onChange: drawPrev }));
  };
  const stateSeg = seg(HEAD_STATES.map(([k, n]) => [k, n]), state, (v) => { state = v; drawHead(); drawPrev(); });
  drawHead();
  const main = [
    group("Vault",
      field("config.yml", "vault.size", { onChange: drawPrev }), field("config.yml", "vault.starting-heads", { onChange: drawPrev, hint: "Up to the vault size. Extra ones arrive as items." }),
      field("config.yml", "vault.max-size", { hint: "For actions that grow a vault." }), field("config.yml", "vault.block-movement.statuses"), field("config.yml", "vault.block-movement.block-opening"),
      field("config.yml", "vault.allow-drop-key"), field("config.yml", "vault.duplicate-insert"), field("config.yml", "vault.save-player-data-after-change")),
    group(["Head item", stateSeg], headBox, field("config.yml", "heads.item.glint", { onChange: drawPrev }), field("config.yml", "heads.item.max-stack-size"), field("config.yml", "heads.item.item-model")),
    group("Kills", field("config.yml", "kills.enabled"), field("config.yml", "kills.once-per-victim-per-cycle"), field("config.yml", "kills.heads-per-kill"),
      field("config.yml", "kills.disabled-worlds"), field("config.yml", "kills.killer-blocked-statuses"), field("config.yml", "kills.killer-pickup-only")),
    group("Heads placed as blocks", field("config.yml", "heads.placed.preserve"), field("config.yml", "heads.placed.creative-break"), field("config.yml", "heads.placed.always-drop"), field("config.yml", "heads.placed.untracked-blocks")),
    group("Copied heads", field("config.yml", "duplicates.notify-admins"), field("config.yml", "duplicates.remove-copies"), field("config.yml", "duplicates.scan-on-inventory-close")),
  ];
  aside.append();
  root.append(layout(main, [aside, el("div", {}, btn([sprite("chest", 18), "Vault menu layout"], () => go("#/menus/vault"), "ghost"))]));
  drawPrev();
}

// ============================================================================================
//  Scoring
// ============================================================================================
const INPUTS = [["unique", 0, 60, 6], ["active", 1, 80, 12], ["total", 0, 120, 20], ["own", 0, 60, 8], ["rank", 1, 80, 3], ["percentile", 0, 100, 70], ["kills", 0, 30, 1],
  ["deaths", 0, 30, 1], ["trades", 0, 40, 3], ["streak", -10, 10, 0], ["points", 0, 200, 10], ["playtime", 0, 3000, 300], ["last_score", -20, 30, 2], ["bonus", -10, 10, 0]];
const INPUT_LABEL = { unique: "Unique heads", active: "Active players", total: "Heads in the vault", own: "Own heads", rank: "Rank", percentile: "Percentile", kills: "Kills", deaths: "Deaths",
  trades: "Trades", streak: "Streak", points: "Points", playtime: "Playtime (min)", last_score: "Last score", bonus: "Score bonus" };
export function outcomesFor(score) {
  const rules = Object.entries(S.get("outcomes.yml", "rules") || {}).filter(([, r]) => r && r.enabled !== false && parseRange(r.score).test(score));
  if ((S.get("outcomes.yml", "mode") ?? "most-extreme") === "all") return rules;
  const groups = {};
  for (const [id, r] of rules) {
    const rg = parseRange(r.score);
    const g = r.group || (rg.max < 0 ? "punishments" : "rewards");
    const pri = r.priority ?? (rg.max < 0 ? Math.abs(rg.max) : rg.min > 0 ? rg.min : 0);
    if (!groups[g] || pri > groups[g].pri) groups[g] = { id, r, pri };
  }
  return Object.values(groups).map((x) => [x.id, x.r]);
}
function scoringPage(root) {
  root.append(pageHead("Scoring"));
  const env = Object.fromEntries(INPUTS.map(([k, , , v]) => [k, v]));
  const fInp = el("textarea", { rows: 2, spellcheck: "false", "aria-label": "Formula", style: { fontSize: "16px", minHeight: "56px" } });
  fInp.value = String(S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula"));
  const fState = el("div", { class: "small", style: { minHeight: "20px" } });
  const fRow = el("div", { class: "field stack", "data-key": "scoring.yml#formula" });
  const setF = (v) => { fInp.value = v; S.set("scoring.yml", "formula", v); fRow.classList.toggle("unsaved", v !== S.savedValue("scoring.yml", "formula")); calc(); };
  fInp.addEventListener("input", () => setF(fInp.value));
  const ideas = S.store.catalog?.formula?.ideas || [];
  const ideaSel = select([["", "Start from an idea"], ...ideas.map((i, n) => [String(n), i.name]), ["default", "The default"]], "", (v) => {
    if (v === "default") setF(S.def("scoring.yml", "formula")); else if (v !== "") setF(ideas[+v].formula);
    ideaSel.value = "";
  });
  const vars = el("details", { class: "more" }, el("summary", {}, "What a formula can use"),
    el("table", { class: "t small" }, (S.store.catalog?.formula?.variables || []).map((v) => el("tr", {}, el("td", {}, el("code", {}, v.name)), el("td", { class: "muted" }, v.description)))),
    el("p", { class: "small muted", style: { marginTop: "8px" } }, "Functions: " + (S.store.catalog?.formula?.functions || []).join(", ") + ". Operators: + - * / % ^, comparisons, && || !, and a ? b : c."));
  fRow.append(el("div", { class: "ctl" }, fInp, el("div", { class: "row", style: { width: "100%" } }, el("div", { class: "grow" }, fState), ideaSel), vars));
  const sliders = el("div", { class: "sheet" });
  INPUTS.forEach(([k, min, max], i) => {
    const out = el("input", { type: "number", value: env[k], min, max, style: { width: "84px" }, "aria-label": INPUT_LABEL[k] });
    const r = el("input", { type: "range", min, max, value: env[k], "aria-label": INPUT_LABEL[k] });
    r.addEventListener("input", () => { env[k] = +r.value; out.value = r.value; calc(); });
    out.addEventListener("input", () => { if (out.value !== "") { env[k] = +out.value; r.value = out.value; calc(); } });
    const row = el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, INPUT_LABEL[k]), el("div", { class: "hint" }, el("code", {}, k))), el("div", { class: "ctl" }, el("div", { class: "slider-row" }, out, r)));
    if (i >= 4) row.dataset.more = "1";
    sliders.append(row);
  });
  sliders.querySelectorAll("[data-more]").forEach((x) => x.classList.add("hidden"));
  const moreBtn = btn("More inputs", () => { const hid = sliders.querySelector("[data-more].hidden"); sliders.querySelectorAll("[data-more]").forEach((x) => x.classList.toggle("hidden", !hid)); moreBtn.textContent = hid ? "Fewer inputs" : "More inputs"; }, "ghost small");
  const result = el("section", { class: "sheet", style: { padding: "18px" } });
  const chartBox = el("section", { class: "sheet", style: { padding: "16px 18px" } });
  const main = [
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Formula")), el("div", { class: "sheet", style: { padding: "4px 18px 8px" } }, fRow)),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Try it"), el("div", { class: "side" }, moreBtn)), sliders),
    group("Rounding and ranks", field("scoring.yml", "rounding", { onChange: () => calc() }), field("scoring.yml", "add-bonus-after-formula", { onChange: () => calc() }), field("scoring.yml", "rank-mode")),
    group("Streaks", field("scoring.yml", "streaks.inactive"), field("scoring.yml", "streaks.zero-score")),
    group("Points", field("config.yml", "points.earn-multiplier", { onChange: () => calc() }), field("config.yml", "points.rollover-percent"), field("config.yml", "points.allow-negative")),
  ];
  root.append(layout(main, [result, chartBox]));
  const scoreFor = (f, e) => {
    const raw = f.eval({ players: e.active + 6, online: 4, duplicates: Math.max(0, e.total - e.unique), vault_size: +C("vault.size") || 27, vault_free: 0, starting_heads: +C("vault.starting-heads") || 27,
      unique_avg: 5, unique_median: 5, unique_max: 15, unique_min: 1, unique_sum: 60, heads_in: 2, heads_out: 2, purchases: 1, points_spent: 3, points_next: 0, playtime_hours: e.playtime / 60,
      streak_positive: Math.max(0, e.streak), streak_negative: Math.max(0, -e.streak), last_unique: 5, has_last: 1, titles: 1, cycle: 3, cycle_length: +C("cycle.length-days") || 30,
      days_elapsed: 10, days_left: 20, is_active: 1, ...e });
    let s = roundScore(raw, S.get("scoring.yml", "rounding") ?? "floor");
    if ((S.get("scoring.yml", "add-bonus-after-formula") ?? true) !== false) s += e.bonus;
    return { raw, s };
  };
  function calc() {
    const f = compileFormula(fInp.value);
    fState.innerHTML = "";
    if (!f.ok) fState.append(el("span", { style: { color: "var(--bad)" } }, f.error + ", at character " + (f.at + 1) + "."));
    else {
      const unknown = [...f.vars].filter((v) => !v.startsWith("%") && !(v in env) && !(S.store.catalog?.formula?.variables || []).some((x) => x.name === v));
      if (unknown.length) fState.append(el("span", { style: { color: "var(--warn)" } }, "Unknown: " + unknown.join(", ")));
    }
    const { raw, s } = scoreFor(f, env);
    const outs = outcomesFor(s);
    const pts = s > 0 ? Math.floor(s * (+C("points.earn-multiplier") || 1)) : 0;
    result.innerHTML = "";
    result.append(el("div", { class: "row", style: { alignItems: "baseline", gap: "14px" } },
      el("div", { style: { fontSize: "56px", fontWeight: 600, lineHeight: 1, letterSpacing: "-.03em", color: s > 0 ? "var(--good)" : s < 0 ? "var(--bad)" : "var(--ink-2)" } }, isFinite(raw) ? s : "?"),
      el("div", {}, el("div", {}, pts ? `${pts} point${pts === 1 ? "" : "s"}` : "No points"), el("div", { class: "small faint" }, isFinite(raw) ? "Before rounding " + Math.round(raw * 1000) / 1000 : "Not a number"))),
      el("div", { style: { marginTop: "14px", display: "grid", gap: "6px", justifyItems: "start" } }, outs.length ? outs.map(([id, r]) => el("a", { href: "#/outcomes/" + id, style: { textDecoration: "none" } }, mcName(r.display || id))) : el("span", { class: "muted" }, "No outcome at this score.")));
    chartBox.innerHTML = "";
    const maxU = Math.max(10, Math.min(40, env.active + 5));
    const pts2 = [];
    for (let u = 0; u <= maxU; u++) pts2.push(scoreFor(f, { ...env, unique: u, total: Math.max(u, env.total) }).s);
    const lo = Math.min(0, ...pts2.filter(isFinite)), hi = Math.max(1, ...pts2.filter(isFinite));
    const W = 340, H = 190, pad = 30;
    const x = (u) => pad + (u / maxU) * (W - pad - 8), y = (v) => 10 + (hi - v) / (hi - lo || 1) * (H - 34);
    const bw = Math.max(2, (W - pad) / (maxU + 1) - 2);
    const svg = [`<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Score for each number of unique heads">`, `<line class="gridl" x1="${pad}" x2="${W - 6}" y1="${y(0)}" y2="${y(0)}"/>`];
    pts2.forEach((v, u) => { if (isFinite(v)) svg.push(`<rect x="${x(u) - bw / 2}" y="${Math.min(y(v), y(0))}" width="${bw}" height="${Math.max(1, Math.abs(y(v) - y(0)))}" rx="1.5" fill="${v > 0 ? "var(--good)" : v < 0 ? "var(--bad)" : "var(--line-2)"}" opacity="${u === env.unique ? 1 : 0.45}"><title>${u} unique: ${v}</title></rect>`); });
    svg.push(`<text x="${pad - 6}" y="${y(hi) + 4}" text-anchor="end">${hi}</text><text x="${pad - 6}" y="${y(lo) + 4}" text-anchor="end">${lo}</text><text x="${pad - 6}" y="${y(0) + 4}" text-anchor="end">0</text>`);
    svg.push(`<text x="${pad}" y="${H - 4}">0 unique</text><text x="${W - 6}" y="${H - 4}" text-anchor="end">${maxU}</text></svg>`);
    chartBox.append(el("h2", { style: { fontSize: "15px", marginBottom: "8px" } }, `Score by unique heads, ${env.active} active`), el("div", { html: svg.join("") }));
  }
  calc();
}

// ============================================================================================
//  Outcomes
// ============================================================================================
const PAL = ["#b8463c", "#c46a1f", "#a0861b", "#7a4fb3", "#1f8a7e", "#3f8a3a", "#2f6fae", "#9a4f84"];
function outcomesPage(root, params) {
  const addRule = async () => {
    const id = await promptDialog("New rule", "A short ID, like double-points:", "my-rule", { check: (v) => !/^[a-z0-9][a-z0-9-]*$/i.test(v) ? "Use letters, numbers and dashes." : S.get("outcomes.yml", "rules." + v) ? "That ID is taken." : null });
    if (!id) return;
    S.set("outcomes.yml", "rules." + id, { display: "<yellow>" + humanize(id), score: "..-1", actions: [{ message: "<yellow>Your score was <score>." }] });
    draw(); edit(id);
  };
  root.append(pageHead("Outcomes", null, btn([icon("plus"), "New rule"], addRule)));
  let score = -3;
  const lineBox = el("div", { class: "sheet", style: { padding: "18px 18px 10px" } });
  const listBox = el("div");
  root.append(group(null, field("outcomes.yml", "mode", { onChange: () => draw() })), el("section", { class: "group" }, el("header", {}, el("h2", {}, "Score line")), lineBox), listBox);
  const MIN = -20, MAX = 20;
  const draw = () => {
    lineBox.innerHTML = "";
    const rules = Object.entries(S.get("outcomes.yml", "rules") || {});
    const pos = (v) => ((Math.max(MIN, Math.min(MAX, v)) - MIN) / (MAX - MIN)) * 100;
    const axis = el("div", { class: "axis" });
    for (let t = MIN; t <= MAX; t += 5) axis.append(el("div", { class: "tick", style: { left: pos(t) + "%" } }, t));
    const bands = el("div", { style: { position: "relative", height: rules.length * 30 + 4 + "px", margin: "30px 12px 0" } });
    rules.forEach(([id, r], i) => {
      const rg = parseRange(r?.score);
      if (!rg.ok) return;
      const a = pos(rg.min === -Infinity ? MIN : rg.min), b = pos(rg.max === Infinity ? MAX : rg.max);
      bands.append(el("div", { class: "band", style: { left: a + "%", width: Math.max(1.5, b - a) + "%", top: i * 30 + "px", background: PAL[i % PAL.length], opacity: r?.enabled === false ? 0.35 : 1 }, title: stripMini(r?.display || id) + ": " + describeRange(r.score), onclick: () => edit(id) }, stripMini(r?.display || id)));
    });
    const marker = el("div", { class: "marker", "data-v": score, style: { left: pos(score) + "%" } });
    const slider = el("input", { type: "range", min: MIN, max: MAX, value: score, "aria-label": "Score", style: { width: "calc(100% - 24px)", margin: "12px 12px 0" } });
    const res = el("div", { class: "row", style: { margin: "12px 0 6px" } });
    const result = () => {
      res.innerHTML = "";
      const outs = outcomesFor(score);
      res.append(el("span", { class: "muted" }, `A score of ${score} gets`), ...(outs.length ? outs.map(([id, r]) => el("a", { href: "#/outcomes/" + id, style: { textDecoration: "none" } }, mcName(r.display || id))) : [el("span", { class: "muted" }, "nothing")]));
    };
    slider.addEventListener("input", () => { score = +slider.value; marker.dataset.v = score; marker.style.left = pos(score) + "%"; result(); });
    lineBox.append(el("div", { class: "scoreline" }, axis, el("div", { style: { position: "relative" } }, bands, el("div", { style: { position: "absolute", inset: "0 12px", pointerEvents: "none" } }, marker))), slider, res);
    result();
    listBox.innerHTML = "";
    const items = el("div", { class: "items" });
    rules.forEach(([id, r], i) => {
      const acts = typeof r?.actions === "string" ? "Runs the list " + r.actions : (Array.isArray(r?.actions) ? r.actions : r?.actions ? [r.actions] : []).map(summarize).join(", ") || "Does nothing";
      items.append(el("div", { class: "item" + (r?.enabled === false ? " off" : ""), onclick: () => edit(id) },
        el("span", { style: { width: "10px", height: "28px", borderRadius: "3px", background: PAL[i % PAL.length], flex: "none" } }),
        el("div", { class: "main" }, el("div", { class: "t1" }, mcName(r?.display || id)), el("div", { class: "t2" }, acts)),
        el("div", { class: "end" }, describeRange(r?.score), r?.conditions ? el("div", {}, "with conditions") : null, r?.enabled === false ? el("div", {}, "Off") : null)));
    });
    items.append(el("button", { class: "addrow", onclick: addRule }, icon("plus"), "New rule"));
    listBox.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Rules")), items));
  };
  const edit = (id) => {
    const base = "rules." + id;
    const groups = [...new Set(Object.values(S.get("outcomes.yml", "rules") || {}).map((r) => r?.group).filter(Boolean))];
    const r = S.get("outcomes.yml", base) || {};
    const pun = r.punishment === undefined ? "auto" : r.punishment ? "yes" : "no";
    const groupRow = el("div", { class: "field", "data-key": "outcomes.yml#" + base + ".group" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Competes in"), el("div", { class: "hint" }, "Only the strongest rule in a group applies.")),
      el("div", { class: "ctl" }, select([["", parseRange(r.score).max < 0 ? "Punishments" : "Rewards"], ...groups.map((g) => [g, humanize(g)]), ["+", "A new group"]], r.group || "", async (v) => {
        if (v === "+") { const g = await promptDialog("New group", "Name:", "", { check: (x) => (/^[a-z0-9-]+$/i.test(x) ? null : "Letters, numbers and dashes.") }); if (g) S.set("outcomes.yml", base + ".group", g); edit(id); return; }
        S.set("outcomes.yml", base + ".group", v || undefined);
      })));
    const priRow = el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Priority"), el("div", { class: "hint" }, "Auto: further from 0 wins.")),
      el("div", { class: "ctl" }, el("div", { class: "row" }, switchCtl(r.priority === undefined, (auto) => { S.set("outcomes.yml", base + ".priority", auto ? undefined : 10); edit(id); }, "Automatic priority"), el("span", { class: "muted" }, "Auto"),
        r.priority !== undefined ? numberCtl(r.priority, (v) => S.set("outcomes.yml", base + ".priority", v), { step: 1 }) : null)));
    const body = el("div", {},
      group(null,
        field("outcomes.yml", base + ".display", { label: "Name", onChange: draw }),
        el("div", { class: "field", "data-key": "outcomes.yml#" + base + ".score" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Score")),
          el("div", { class: "ctl" }, rangeCtl(r.score, (v) => { S.set("outcomes.yml", base + ".score", v); draw(); }))),
        field("outcomes.yml", base + ".enabled", { label: "On", type: "bool", onChange: draw }),
        el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, "A shield blocks it"), el("div", { class: "hint" }, "Auto: when the whole range is below 0.")),
          el("div", { class: "ctl" }, seg([["auto", "Auto"], ["yes", "Yes"], ["no", "No"]], pun, (v) => S.set("outcomes.yml", base + ".punishment", v === "auto" ? undefined : v === "yes")))),
        groupRow, priRow),
      el("section", { class: "group" }, el("header", {}, el("h2", {}, "Only when")), el("div", { class: "sheet pad", style: { padding: "12px 18px" } }, conditionsCtl("outcomes.yml", base + ".conditions"))),
      el("section", { class: "group" }, el("header", {}, el("h2", {}, "What happens")), actionList("outcomes.yml", base + ".actions", { vars: "outcome" })));
    drawer(mcName(r.display || id), body, { subtitle: "For scores of " + describeRange(r.score), wide: true, onClose: draw,
      footer: el("div", { class: "row", style: { width: "100%" } },
        btn("Rename", async () => { const n = await promptDialog("Rename rule", "New ID:", id, { check: (v) => (!/^[a-z0-9][a-z0-9-]*$/i.test(v) ? "Use letters, numbers and dashes." : null) }); if (n && n !== id) { S.rename("outcomes.yml", base, n); closeDrawer(); edit(n); } }, "ghost"),
        btn([icon("trash"), "Delete"], async () => { if (await confirmDialog("Delete " + stripMini(r.display || id) + "?", null, { ok: "Delete", danger: true })) { S.remove("outcomes.yml", base); closeDrawer(); } }, "ghost danger"),
        el("span", { class: "grow" }), btn("Done", () => closeDrawer(), "primary")) });
  };
  draw();
  if (params[0] && S.get("outcomes.yml", "rules." + params[0])) edit(params[0]);
}

export const setupPages = [
  { id: "overview", group: "Home", title: "Overview", icon: ICONS.home, render: overview },
  { id: "cycle", group: "Set up", title: "Cycle", icon: ICONS.cycle, render: cyclePage, owns: ["config.yml:cycle", "config.yml:activity"] },
  { id: "vault", group: "Set up", title: "Vault and heads", icon: ICONS.vault, render: vaultPage, owns: ["config.yml:vault", "config.yml:heads", "config.yml:duplicates", "config.yml:kills"] },
  { id: "scoring", group: "Set up", title: "Scoring", icon: ICONS.score, render: scoringPage, owns: ["scoring.yml", "config.yml:points"] },
  { id: "outcomes", group: "Set up", title: "Outcomes", icon: ICONS.outcome, render: outcomesPage, owns: ["outcomes.yml"] },
];
