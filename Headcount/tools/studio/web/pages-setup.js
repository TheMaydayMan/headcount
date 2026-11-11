// Set up pages, part 1: Overview, Cycle & schedule, Vault & heads, Scoring (formula lab), Outcomes.
import { el, btn, badge, field, allFields, sectionTitle, mcText, miniInline, tooltip, itemTile, chest, seg, switchCtl, drawer, closeDrawer, toast,
  promptDialog, confirmDialog, hoverTip, SAMPLE, ICONS, humanize } from "./ui.js";
import { compileFormula, roundScore, parseRange, describeRange, parseDuration, formatDuration, isObj, clone, renderMini, stripMini, expandSlots } from "./core.js";
import * as S from "./store.js";
import { editActions, summarize } from "./actions.js";

export const C = (p) => S.get("config.yml", p) ?? S.def("config.yml", p);
const go = (h) => { location.hash = h; };
const link = (text, hash, key) => el("a", { href: hash, onclick: (e) => { e.preventDefault(); window.studio.go(hash, key); } }, text);
export const pageHead = (title, sub, ...actions) => el("div", { class: "pagehead" }, el("div", { class: "grow" }, el("h1", {}, title), sub ? el("div", { class: "sub" }, sub) : null), ...actions);

// ============================================================================================
//  Overview
// ============================================================================================
function overview(root) {
  const live = S.store.live;
  const formula = S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula");
  const f = compileFormula(String(formula));
  const ex = (u, a) => roundScore(f.eval({ unique: u, active: a, total: u * 3, own: 9, rank: 3, percentile: 60, players: a + 4, kills: 0, deaths: 0, trades: 0, streak: 0, points: 0, bonus: 0 }), S.get("scoring.yml", "rounding"));
  const rules = Object.entries(S.get("outcomes.yml", "rules") || {}).filter(([, r]) => r && r.enabled !== false);
  const worstFirst = rules.filter(([, r]) => parseRange(r.score).max < 0).sort((a, b) => parseRange(b[1].score).max - parseRange(a[1].score).max);
  const tiers = Object.entries(S.get("shop.yml", "tiers") || {});
  const entries = tiers.reduce((n, [, t]) => n + Object.keys(t?.entries || {}).length, 0);
  const B = (v, hash, key) => el("b", {}, link(String(v), hash, key));
  root.append(pageHead("Overview", "Your Headcount setup at a glance. Every highlighted value is a link to where it's set."));
  if (live?.cycle) {
    const c = live.cycle;
    const pct = Math.max(0, Math.min(100, (Date.now() - c.start_ms) / (c.end_ms - c.start_ms) * 100));
    root.append(el("div", { class: "card click", style: { marginBottom: "14px" }, onclick: () => go("#/live") },
      el("div", { class: "kpi" }, el("div", { class: "ring", style: { "--p": pct.toFixed(1) } }, el("div", {}, el("b", {}, formatDuration(c.end_ms - Date.now())), el("div", { class: "faint" }, "left"))),
        el("div", { class: "grow" }, el("h3", {}, `Cycle ${c.id} is running`, " ", badge("live", "good")),
          el("div", { class: "muted" }, `${live.activeCount} active of ${live.playerCount} players · ${live.online.length} online · ends ${new Date(c.end_ms).toLocaleString()}`)),
        live.top?.[0] ? el("div", {}, el("div", { class: "faint small" }, "Leading"), el("b", {}, live.top[0].name), el("div", { class: "small muted" }, live.top[0].unique + " unique")) : null)));
  }
  const p = el("div", { class: "card", style: { fontSize: "15px", lineHeight: "1.7" } });
  p.append("Cycles last ", B(C("cycle.length-days") + " days", "#/cycle", "config.yml#cycle.length-days"), " and roll over at ",
    B(C("cycle.end-time"), "#/cycle", "config.yml#cycle.end-time"), " ", B(C("cycle.time-zone"), "#/cycle", "config.yml#cycle.time-zone"), ". Everyone starts with ",
    B(C("vault.starting-heads"), "#/vault", "config.yml#vault.starting-heads"), " of their own heads in a ", B(C("vault.size") + "-slot", "#/vault", "config.yml#vault.size"),
    " vault. A score is ", el("code", {}, link(String(formula), "#/scoring")), f.ok ? frag2(": with 12 active players, 4 unique heads scores ", el("b", {}, ex(4, 12)), " and 10 score ", el("b", {}, ex(10, 12))) : el("span", { style: { color: "var(--bad)" } }, " (the formula has an error)"),
    ". Positive scores earn ", B((C("points.earn-multiplier") ?? 1) + "× points", "#/scoring"), ", ",
    worstFirst.length ? frag2("a score of ", el("b", {}, describeRange(worstFirst[0][1].score)), " brings ", miniInline(worstFirst[0][1].display || worstFirst[0][0]), ", ") : "",
    "and the shop has ", B(tiers.length + " tiers", "#/shop"), " with ", el("b", {}, entries), " items. PvP is handled by ", B(S.get("integrations.yml", "pvp.provider") ?? "included", "#/integrations"), ".");
  root.append(p);
  const card = (icon, title, value, sub, hash) => el("div", { class: "card click", onclick: () => go(hash) }, el("h4", {}, icon + "  " + title), el("div", { class: "big" }, value), el("div", { class: "muted small" }, sub));
  const statuses = Object.keys(S.get("statuses.yml", "statuses") || {});
  const lists = Object.keys(S.get("actions.yml", "lists") || {});
  const titles = Object.keys(S.get("titles.yml", "titles") || {});
  const langChanged = countChanged("lang/en_US.yml");
  root.append(sectionTitle("Areas"), el("div", { class: "grid g4" },
    card(ICONS.cycle, "Cycle", C("cycle.length-days") + "d", "rolls over " + C("cycle.end-time"), "#/cycle"),
    card(ICONS.vault, "Vault", C("vault.size"), C("vault.starting-heads") + " starting heads", "#/vault"),
    card(ICONS.score, "Scoring", f.ok ? "ƒ" : "⚠", String(formula).slice(0, 40), "#/scoring"),
    card(ICONS.outcome, "Outcomes", rules.length, "active rules · " + (S.get("outcomes.yml", "mode") || "most-extreme"), "#/outcomes"),
    card(ICONS.shop, "Shop", entries, tiers.length + " tiers", "#/shop"),
    card(ICONS.title, "Titles", titles.length, "titles", "#/titles"),
    card(ICONS.status, "Statuses", statuses.length, "named states", "#/statuses"),
    card(ICONS.lang, "Messages", langChanged, "changed from the defaults", "#/messages")));
  const plugs = ["pvp", "placeholders", "economy", "permissions", "moderation", "player-state"].map((k) => [k, S.get("integrations.yml", k + ".provider") ?? S.def("integrations.yml", k + ".provider")]);
  root.append(sectionTitle("Who handles what"), el("div", { class: "row" }, plugs.map(([k, v]) => el("span", { class: "pill", style: { cursor: "pointer" }, onclick: () => go("#/integrations") }, humanize(k), ": ", el("b", {}, v)))));
  const problems = window.studio.problems();
  root.append(sectionTitle("Problems", problems.length ? badge(problems.length, problems.some((x) => x.level === "error") ? "bad" : "warn") : badge("none", "good")));
  if (!problems.length) root.append(el("div", { class: "note good" }, "Nothing looks wrong in your configs."));
  for (const pr of problems.slice(0, 6)) root.append(el("div", { class: "note " + (pr.level === "error" ? "bad" : "warn"), style: { margin: "6px 0", cursor: pr.go ? "pointer" : "default" }, onclick: () => pr.go && window.studio.go(pr.go, pr.key) }, pr.text));
  if (problems.length > 6) root.append(link(`All ${problems.length} problems →`, "#/problems"));
  const dirty = S.dirtyNames();
  if (dirty.length) root.append(sectionTitle("Unsaved changes"), el("div", { class: "row" }, dirty.map((n) => badge(n, "warn")), btn("Review & save", () => go("#/review"), "primary")));
  root.append(sectionTitle("Getting started"), checklist());
}
const frag2 = (...k) => { const f = document.createDocumentFragment(); k.forEach((x) => f.append(x.nodeType ? x : document.createTextNode(String(x)))); return f; };
export function countChanged(file) {
  const d = S.doc(file); let n = 0;
  const walk = (a, b) => { if (isObj(a) && isObj(b)) { for (const k of Object.keys(a)) walk(a[k], b[k]); } else if (JSON.stringify(a) !== JSON.stringify(b)) n++; };
  walk(d?.data, d?.defaults);
  return n;
}
function checklist() {
  const items = [["cycle", "Pick a cycle length and check the time zone", "#/cycle"], ["vault", "Choose the vault size and starting heads", "#/vault"],
    ["scoring", "Try the formula in the scoring lab", "#/scoring"], ["shop", "Look through the shop", "#/shop"], ["integrations", "Check who handles PvP and permissions", "#/integrations"],
    ["learn", "Read how a cycle works", "#/learn"], ["review", "Review and save your changes", "#/review"], ["reload", "Reload the server and run selftest", "#/console"]];
  let done = {};
  try { done = JSON.parse(localStorage.getItem("hc-checklist") || "{}"); } catch (e) { /* private window */ }
  const box = el("div", { class: "card" });
  for (const [id, text, hash] of items) {
    const cb = el("input", { type: "checkbox" }); cb.checked = !!done[id];
    cb.addEventListener("change", () => { done[id] = cb.checked; try { localStorage.setItem("hc-checklist", JSON.stringify(done)); } catch (e) { /* ignore */ } });
    box.append(el("div", { class: "row", style: { padding: "4px 0" } }, cb, el("span", { class: "grow" }, text), link("open →", hash)));
  }
  return box;
}

// ============================================================================================
//  Cycle & schedule
// ============================================================================================
const ZONES = ["UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu",
  "America/Toronto", "America/Sao_Paulo", "Europe/London", "Europe/Dublin", "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Europe/Amsterdam", "Europe/Stockholm",
  "Europe/Warsaw", "Europe/Moscow", "Asia/Dubai", "Asia/Kolkata", "Asia/Singapore", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Australia/Perth",
  "Pacific/Auckland", "MST", "MDT", "EST", "EDT", "CST", "CDT", "PST", "PDT", "CET", "GMT", "GMT-7", "UTC+5:30", "system"];
const ABBR = { UTC: 0, GMT: 0, Z: 0, WET: 0, WEST: 60, BST: 60, CET: 60, CEST: 120, EET: 120, EEST: 180, MSK: 180, IST: 330, SGT: 480, AWST: 480, JST: 540, KST: 540,
  ACST: 570, AEST: 600, AEDT: 660, NZST: 720, NZDT: 780, HST: -600, AKST: -540, AKDT: -480, PST: -480, PDT: -420, MST: -420, MDT: -360, CST: -360, CDT: -300, EST: -300, EDT: -240,
  AST: -240, ADT: -180, NST: -210, NDT: -150 };
/** {iana} or {offset minutes}, or null, for the zone forms Headcount accepts. */
export function zoneInfo(z) {
  const s = String(z || "").trim();
  if (s.toLowerCase() === "system") return { iana: Intl.DateTimeFormat().resolvedOptions().timeZone, label: "this computer's zone" };
  if (ABBR[s.toUpperCase()] !== undefined) return { offset: ABBR[s.toUpperCase()], label: s.toUpperCase() + " (fixed offset)" };
  const m = /^(?:utc|gmt|ut)?\s*([+-])\s*(\d{1,2})(?::?(\d{2}))?$/i.exec(s);
  if (m) return { offset: (m[1] === "-" ? -1 : 1) * (+m[2] * 60 + +(m[3] || 0)), label: "fixed offset" };
  try { new Intl.DateTimeFormat("en", { timeZone: s }).format(); return { iana: s, label: "follows daylight saving" }; } catch (e) { return null; }
}
function wallClockNow(zi) {
  if (!zi) return "";
  if (zi.iana) return new Intl.DateTimeFormat(undefined, { timeZone: zi.iana, weekday: "short", hour: "2-digit", minute: "2-digit" }).format(new Date());
  const d = new Date(Date.now() + zi.offset * 60000);
  return d.toUTCString().slice(0, 3) + " " + d.toISOString().slice(11, 16);
}
function cyclePage(root) {
  root.append(pageHead("Cycle & schedule", "When cycles start and end. A cycle's length and vault settings are fixed when it starts; changes apply from the next cycle (or a /hca reset cycle)."));
  const split = el("div", { class: "split" });
  const left = el("div", { class: "panel" }, el("div", { class: "pb" }));
  const fl = left.firstChild;
  const len = field("config.yml", "cycle.length-days", { label: "Cycle length", suffix: "days", min: 1, onChange: () => draw() });
  len.querySelector(".ctl").prepend(el("div", { style: { marginBottom: "6px" } }, seg([[1, "1 day"], [7, "1 week"], [14, "2 weeks"], [30, "30 days"]], C("cycle.length-days"), (v) => { S.set("config.yml", "cycle.length-days", +v); len.querySelector("input[type=number]").value = v; draw(); })));
  fl.append(len);
  // start-date: auto, or a date
  const sd = String(C("cycle.start-date") ?? "auto");
  const dateInp = el("input", { type: "date", value: sd === "auto" ? "" : sd, style: { maxWidth: "200px" }, oninput: (e) => { if (e.target.value) { S.set("config.yml", "cycle.start-date", e.target.value); draw(); } } });
  fl.append(el("div", { class: "field", "data-key": "config.yml#cycle.start-date" }, el("div", {}, el("div", { class: "lbl" }, "First cycle starts"), el("div", { class: "help" }, "auto starts at once (aligned to the roll-over time today). A date waits until then; no cycle runs before it.")),
    el("div", { class: "ctl" }, seg([["auto", "Right away"], ["date", "On a date"]], sd === "auto" ? "auto" : "date", (m) => { if (m === "auto") S.set("config.yml", "cycle.start-date", "auto"); else if (dateInp.value) S.set("config.yml", "cycle.start-date", dateInp.value); dateInp.style.display = m === "date" ? "" : "none"; draw(); }),
      el("div", { style: { marginTop: "6px" } }, dateInp))));
  if (sd === "auto") dateInp.style.display = "none";
  fl.append(field("config.yml", "cycle.end-time", { label: "Roll-over time", type: "time", onChange: () => draw() }));
  const tz = field("config.yml", "cycle.time-zone", { label: "Time zone", type: "text", list: "zones", onChange: () => { draw(); clock(); } });
  const clockEl = el("div", { class: "small", style: { marginTop: "4px" } });
  const clock = () => { const zi = zoneInfo(S.get("config.yml", "cycle.time-zone") ?? "UTC"); clockEl.innerHTML = ""; clockEl.append(zi ? el("span", { class: "muted" }, `It's ${wallClockNow(zi)} there now · ${zi.label}`) : el("span", { style: { color: "var(--bad)" } }, "Not a time zone Headcount knows; it would use UTC.")); };
  tz.querySelector(".ctl").insertBefore(clockEl, tz.querySelector(".meta"));
  tz.append(el("datalist", { id: "zones" }, ZONES.map((z) => el("option", { value: z }))));
  fl.append(tz);
  clock();
  fl.append(sectionTitle("Activity"), field("config.yml", "activity.min-playtime", { label: "Playtime to count as active" }), field("config.yml", "activity.check-interval", { label: "Check every" }));
  const right = el("div", { class: "sticky" });
  const draw = () => {
    right.innerHTML = "";
    const days = +C("cycle.length-days") || 30;
    const [hh, mm] = String(C("cycle.end-time") || "00:00").split(":").map(Number);
    const sdv = String(S.get("config.yml", "cycle.start-date") ?? "auto");
    const base = sdv === "auto" ? new Date() : new Date(sdv + "T00:00:00Z");
    let start = Date.UTC(base.getUTCFullYear(), base.getUTCMonth(), base.getUTCDate(), hh || 0, mm || 0);
    const fmt = (t) => new Date(t).toUTCString().slice(0, 22).replace(" GMT", "");
    const card = el("div", { class: "card" }, el("h4", {}, "The next cycles"), el("div", { class: "small muted" }, "Wall-clock times in " + (S.get("config.yml", "cycle.time-zone") ?? "UTC") + (sdv === "auto" ? ", if the first started today." : ".")));
    for (let k = 0; k < 3; k++) {
      const end = start + days * 86400000;
      card.append(el("div", { class: "row", style: { padding: "6px 0", borderTop: "1px solid var(--line)" } }, badge("Cycle " + (k + 1), k ? "plain" : ""), el("span", { class: "grow small" }, fmt(start) + "  →  " + fmt(end))));
      start = end;
    }
    // Warnings on the timeline of one cycle
    const tl = el("div", { class: "timeline" }, el("div", { class: "bar" }));
    const total = days * 86400000;
    const warnings = C("hooks.warnings") || {};
    for (const [id, w] of Object.entries(warnings)) {
      const before = parseDuration(w?.before);
      if (!isFinite(before) || w?.enabled === false) continue;
      const x = Math.max(0, Math.min(100, (1 - before / total) * 100));
      tl.append(el("div", { class: "mk", style: { left: x + "%" }, title: id + ": " + w.before + " before the end" }, el("span", {}, id)));
    }
    tl.append(el("div", { class: "end", style: { left: 0 } }, "start"), el("div", { class: "end", style: { right: 0 } }, "end"));
    right.append(card, el("div", { class: "card", style: { marginTop: "14px" } }, el("h4", {}, "Warnings before the end"), tl,
      el("div", { class: "small muted" }, "Each marker is a warning hook. ", link("Edit warnings →", "#/hooks"))));
    right.append(el("div", { class: "card", style: { marginTop: "14px" } }, el("h4", {}, "Running cycle"), el("div", { class: "small muted" }, "Change the current cycle's end, end it, or start it over from the console:"),
      el("div", { class: "row", style: { marginTop: "8px" } }, btn("Cycle tools →", () => go("#/live-cycle")))));
  };
  draw();
  split.append(left, right);
  root.append(split);
}

// ============================================================================================
//  Vault & heads
// ============================================================================================
function vaultPage(root) {
  root.append(pageHead("Vault & heads", "The head vault (/hc), the economy head item, placed heads, duplicates, kills and points."));
  const split = el("div", { class: "split" });
  const left = el("div");
  const right = el("div", { class: "sticky" });
  const main = el("div", { class: "panel" }, el("div", { class: "pb" }));
  main.firstChild.append(
    field("config.yml", "vault.size", { label: "Vault size", suffix: "slots", slider: true, min: 1, max: 54, step: 1, onChange: () => drawPrev() }),
    field("config.yml", "vault.starting-heads", { label: "Starting heads", suffix: "of your own head", slider: true, min: 0, max: 54, step: 1, onChange: () => drawPrev() }),
    field("config.yml", "vault.gui.title", { label: "Vault title", type: "mini", onChange: () => drawPrev() }),
    field("config.yml", "vault.block-movement.statuses", { label: "Statuses that block moving heads", type: "list", suggestions: Object.keys(S.get("statuses.yml", "statuses") || {}) }),
    field("config.yml", "vault.block-movement.block-opening", { label: "…also block opening the vault" }),
  );
  left.append(main);
  const heads = el("div", { class: "panel", style: { marginTop: "14px" } }, el("div", { class: "ph" }, el("h3", {}, "The head item")), el("div", { class: "pb" }));
  heads.lastChild.append(
    field("config.yml", "heads.item.name", { label: "Name", onChange: () => drawPrev() }), field("config.yml", "heads.item.lore", { label: "Lore", type: "yaml", onChange: () => drawPrev() }),
    field("config.yml", "heads.item.expired-name", { label: "Expired name", onChange: () => drawPrev() }), field("config.yml", "heads.item.expired-lore", { label: "Expired lore", type: "yaml", onChange: () => drawPrev() }),
    field("config.yml", "heads.item.defunct-name", { label: "Defunct name", onChange: () => drawPrev() }), field("config.yml", "heads.item.defunct-lore", { label: "Defunct lore", type: "yaml", onChange: () => drawPrev() }),
    field("config.yml", "heads.item.glint"), field("config.yml", "heads.item.max-stack-size"), field("config.yml", "heads.item.item-model"));
  left.append(heads);
  const more = (title, file, base, skip) => el("details", { class: "panel more", style: { marginTop: "14px", padding: "0 16px" } }, el("summary", {}, title), allFields(file, base, { skip }));
  left.append(more("All vault options", "config.yml", "vault", ["vault.size", "vault.starting-heads", "vault.gui.title", "vault.block-movement"]),
    more("Placed heads", "config.yml", "heads.placed"), more("Duplicates", "config.yml", "duplicates"), more("Kills", "config.yml", "kills"), more("Points", "config.yml", "points"));
  const drawPrev = () => {
    right.innerHTML = "";
    const size = Math.max(1, +C("vault.size") || 27), start = Math.min(size, +C("vault.starting-heads") || 0);
    const rows = Math.min(6, Math.ceil(Math.min(size, 54) / 9));
    const vars = { owner: "Steve", unique: 1, total: start, size, used: start, score: SAMPLE.score };
    right.append(el("div", { class: "card" }, el("h4", {}, "Preview"), chest(rows, C("vault.gui.title"), (i) => i >= Math.min(size, 54) ? { cls: "dim", item: "BLACK_STAINED_GLASS_PANE" }
      : i < start ? { item: "PLAYER_HEAD", tip: () => tooltip(C("heads.item.name"), C("heads.item.lore"), { owner: "Steve", cycle: 3 }) } : null, { vars }),
      size > 54 ? el("div", { class: "small muted", style: { marginTop: "6px" } }, `${size} slots: shown in pages of 45.`) : null));
    const h = (t, n, l, extra) => el("div", { style: { marginTop: "12px" } }, el("div", { class: "small muted", style: { marginBottom: "4px" } }, t), tooltip(n, l, { owner: "Steve", cycle: 3, ...extra }));
    right.append(el("div", { class: "card", style: { marginTop: "14px" } }, el("h4", {}, "Head tooltips"),
      h("Current", C("heads.item.name"), C("heads.item.lore")), h("Expired (older cycle)", C("heads.item.expired-name"), C("heads.item.expired-lore"), { cycle: 2 }),
      h("Defunct (from before a reset)", C("heads.item.defunct-name"), C("heads.item.defunct-lore"))));
  };
  drawPrev();
  split.append(left, right);
  root.append(split);
}

// ============================================================================================
//  Scoring: the formula lab
// ============================================================================================
const INPUTS = [["unique", 0, 60, 6], ["active", 1, 80, 12], ["total", 0, 120, 20], ["own", 0, 60, 8], ["rank", 1, 80, 3], ["percentile", 0, 100, 70], ["kills", 0, 30, 1],
  ["deaths", 0, 30, 1], ["trades", 0, 40, 3], ["streak", -10, 10, 0], ["points", 0, 200, 10], ["playtime", 0, 3000, 300], ["last_score", -20, 30, 2], ["bonus", -10, 10, 0]];
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
  root.append(pageHead("Scoring", "The formula that turns a vault into a score. Try it with the sliders; the chart shows scores for every number of unique heads."));
  const env = Object.fromEntries(INPUTS.map(([k, , , v]) => [k, v]));
  const split = el("div", { class: "split" });
  const left = el("div");
  const fCard = el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "Formula")), el("div", { class: "pb" }));
  const fInp = el("textarea", { rows: 2, spellcheck: "false", style: { fontSize: "15px" } });
  fInp.value = String(S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula"));
  const fErr = el("div", { class: "small", style: { minHeight: "18px", marginTop: "4px" } });
  fInp.addEventListener("input", () => { S.set("scoring.yml", "formula", fInp.value); calc(); });
  const ideas = el("div", { class: "row gap-s", style: { marginTop: "6px" } }, el("span", { class: "small muted" }, "Ideas:"),
    (S.store.catalog?.formula?.ideas || []).map((i) => el("button", { class: "btn small", title: i.formula, onclick: () => { fInp.value = i.formula; S.set("scoring.yml", "formula", i.formula); calc(); } }, i.name)),
    el("button", { class: "btn small ghost", onclick: () => { fInp.value = S.def("scoring.yml", "formula"); S.set("scoring.yml", "formula", fInp.value); calc(); } }, "↺ default"));
  const varsHelp = el("details", { class: "more" }, el("summary", {}, "Variables and functions"),
    el("table", { class: "t small" }, (S.store.catalog?.formula?.variables || []).map((v) => el("tr", {}, el("td", {}, el("code", {}, v.name)), el("td", { class: "muted" }, v.description)))),
    el("div", { class: "small muted", style: { marginTop: "6px" } }, "Functions: " + (S.store.catalog?.formula?.functions || []).join(", ") + ". Operators: + − × ÷ % ^, comparisons, && || !, and a ? b : c."));
  fCard.lastChild.append(fInp, fErr, ideas, varsHelp);
  const lab = el("div", { class: "panel", style: { marginTop: "14px" } }, el("div", { class: "ph" }, el("h3", {}, "Try it"), el("span", { class: "small muted" }, "a player in a cycle with these numbers")), el("div", { class: "pb" }));
  const inputsBox = el("div", { class: "grid g2" });
  INPUTS.forEach(([k, min, max], i) => {
    const out = el("b", { style: { minWidth: "38px", textAlign: "right" } }, env[k]);
    const r = el("input", { type: "range", min, max, value: env[k], style: { flex: 1 } });
    r.addEventListener("input", () => { env[k] = +r.value; out.textContent = r.value; calc(); });
    const row = el("div", { class: "row", style: { flexWrap: "nowrap" } }, el("code", { style: { width: "86px" } }, k), r, out);
    if (i >= 4) row.dataset.more = "1";
    inputsBox.append(row);
  });
  const moreBtn = el("button", { class: "btn small ghost", onclick: () => { inputsBox.querySelectorAll("[data-more]").forEach((x) => x.classList.toggle("hidden")); } }, "more inputs");
  inputsBox.querySelectorAll("[data-more]").forEach((x) => x.classList.add("hidden"));
  lab.lastChild.append(inputsBox, moreBtn);
  left.append(fCard, lab);
  left.append(el("details", { class: "panel more", style: { marginTop: "14px", padding: "0 16px" }, open: true }, el("summary", {}, "Rounding, ranks and streaks"),
    field("scoring.yml", "rounding", { onChange: () => calc() }), field("scoring.yml", "add-bonus-after-formula", { onChange: () => calc() }), field("scoring.yml", "rank-mode"),
    allFields("scoring.yml", "streaks"), field("config.yml", "points.earn-multiplier", { label: "Points per score point" }), field("config.yml", "points.rollover-percent", { label: "Unspent points kept", suffix: "%" })));
  const right = el("div", { class: "sticky" });
  const result = el("div", { class: "card" });
  const chartCard = el("div", { class: "card", style: { marginTop: "14px" } });
  right.append(result, chartCard);
  const scoreFor = (f, e) => {
    const raw = f.eval({ players: e.active + 6, online: 4, duplicates: Math.max(0, e.total - e.unique), vault_size: +C("vault.size") || 27, vault_free: 0, starting_heads: +C("vault.starting-heads") || 27,
      unique_avg: 5, unique_median: 5, unique_max: 15, unique_min: 1, unique_sum: 60, heads_in: 2, heads_out: 2, purchases: 1, points_spent: 3, points_next: 0, playtime_hours: e.playtime / 60,
      streak_positive: Math.max(0, e.streak), streak_negative: Math.max(0, -e.streak), last_unique: 5, has_last: 1, titles: 1, cycle: 3, cycle_length: +C("cycle.length-days") || 30,
      days_elapsed: 10, days_left: 20, is_active: 1, ...e });
    let s = roundScore(raw, S.get("scoring.yml", "rounding") ?? "floor");
    if ((S.get("scoring.yml", "add-bonus-after-formula") ?? true) !== false) s += e.bonus;
    return { raw, s };
  };
  const calc = () => {
    const f = compileFormula(fInp.value);
    fErr.innerHTML = "";
    if (!f.ok) fErr.append(el("span", { style: { color: "var(--bad)" } }, "⚠ " + f.error + " (at character " + (f.at + 1) + ")"));
    else {
      const unknown = [...f.vars].filter((v) => !v.startsWith("%") && !(v in env) && !(S.store.catalog?.formula?.variables || []).some((x) => x.name === v));
      if (unknown.length) fErr.append(el("span", { style: { color: "var(--warn)" } }, "Unknown variable: " + unknown.join(", ")));
      else fErr.append(el("span", { class: "muted" }, "✓ The formula reads fine."));
    }
    const { raw, s } = scoreFor(f, env);
    const outs = outcomesFor(s);
    const pts = s > 0 ? Math.floor(s * (+C("points.earn-multiplier") || 1)) : 0;
    result.innerHTML = "";
    result.append(el("h4", {}, "Result"), el("div", { class: "kpi" }, el("div", { class: "big", style: { fontSize: "48px", color: s > 0 ? "var(--good)" : s < 0 ? "var(--bad)" : "var(--muted)" } }, isFinite(raw) ? s : "?"),
      el("div", {}, el("div", { class: "small muted" }, "raw " + (isFinite(raw) ? Math.round(raw * 1000) / 1000 : "not a number")), el("div", {}, pts ? `earns ${pts} point${pts === 1 ? "" : "s"}` : s < 0 ? "no points, and…" : "no points"))),
      el("div", { style: { marginTop: "8px" } }, outs.length ? outs.map(([id, r]) => el("div", { class: "row", style: { margin: "3px 0" } }, miniInline(r.display || id), el("span", { class: "small muted" }, "(" + describeRange(r.score) + ")")))
        : el("span", { class: "muted small" }, "No outcome for this score.")));
    // Chart: score for unique = 0..max at this active count
    chartCard.innerHTML = "";
    const maxU = Math.max(10, Math.min(40, env.active + 5));
    const pts2 = [];
    for (let u = 0; u <= maxU; u++) pts2.push(scoreFor(f, { ...env, unique: u, total: Math.max(u, env.total) }).s);
    const lo = Math.min(0, ...pts2), hi = Math.max(1, ...pts2);
    const W = 340, H = 200, pad = 28;
    const x = (u) => pad + (u / maxU) * (W - pad - 6), y = (v) => 8 + (hi - v) / (hi - lo || 1) * (H - 30);
    const svg = [`<svg class="chart" viewBox="0 0 ${W} ${H}">`, `<line class="gridl" x1="${pad}" x2="${W - 6}" y1="${y(0)}" y2="${y(0)}"/>`];
    pts2.forEach((v, u) => { const bw = Math.max(2, (W - pad) / (maxU + 1) - 2); svg.push(`<rect x="${x(u) - bw / 2}" y="${Math.min(y(v), y(0))}" width="${bw}" height="${Math.max(1, Math.abs(y(v) - y(0)))}" rx="2" fill="${v > 0 ? "var(--good)" : v < 0 ? "var(--bad)" : "var(--line-2)"}" opacity="${u === env.unique ? 1 : 0.55}"/>`); });
    svg.push(`<text x="${pad - 4}" y="${y(hi) + 4}" text-anchor="end">${hi}</text><text x="${pad - 4}" y="${y(lo) + 4}" text-anchor="end">${lo}</text><text x="${pad - 4}" y="${y(0) + 4}" text-anchor="end">0</text>`);
    svg.push(`<text x="${pad}" y="${H - 4}">0 unique</text><text x="${W - 6}" y="${H - 4}" text-anchor="end">${maxU}</text></svg>`);
    chartCard.append(el("h4", {}, `Score by unique heads, ${env.active} active players`), el("div", { html: svg.join("") }));
  };
  calc();
  split.append(left, right);
  root.append(split);
}

// ============================================================================================
//  Outcomes
// ============================================================================================
const PAL = ["#c0392b", "#d35400", "#e67e22", "#8e44ad", "#16a085", "#27ae60", "#2980b9", "#b7950b"];
function outcomesPage(root) {
  root.append(pageHead("Outcomes", "What happens at the end of a cycle for each score. Drag the marker to see which rules a score gets.", btn("＋ New rule", async () => {
    const id = await promptDialog("New rule", "An ID for the rule (letters, numbers, dashes):", "my-rule", { check: (v) => !/^[a-z0-9][a-z0-9-]*$/i.test(v) ? "Use letters, numbers and dashes." : S.get("outcomes.yml", "rules." + v) ? "That ID exists." : null });
    if (!id) return;
    S.set("outcomes.yml", "rules." + id, { display: "<yellow>" + humanize(id), score: "..-1", actions: [{ message: "<yellow>Score <score>." }] });
    window.studio.refresh();
  }, "primary")));
  let score = -3;
  const top = el("div", { class: "panel" }, el("div", { class: "pb" }));
  top.firstChild.append(field("outcomes.yml", "mode", { label: "Which rules apply", onChange: () => drawLine() }));
  const line = el("div", { class: "card", style: { marginTop: "14px" } });
  const list = el("div", { class: "grid g2", style: { marginTop: "14px" } });
  root.append(top, line, list);
  const MIN = -20, MAX = 20;
  const drawLine = () => {
    line.innerHTML = "";
    const rules = Object.entries(S.get("outcomes.yml", "rules") || {});
    const pos = (v) => ((Math.max(MIN, Math.min(MAX, v)) - MIN) / (MAX - MIN)) * 100;
    const wrap = el("div", { class: "scoreline" });
    const axis = el("div", { class: "axis" });
    for (let t = MIN; t <= MAX; t += 5) axis.append(el("div", { class: "tick", style: { left: pos(t) + "%" } }, t));
    const bands = el("div", { style: { position: "relative", height: rules.length * 24 + 10 + "px", margin: "26px 10px 0" } });
    rules.forEach(([id, r], i) => {
      const rg = parseRange(r?.score);
      if (!rg.ok) return;
      const a = pos(rg.min === -Infinity ? MIN : rg.min), b = pos(rg.max === Infinity ? MAX : rg.max);
      const band = el("div", { class: "band", style: { left: a + "%", width: Math.max(1.2, b - a) + "%", top: i * 24 + "px", background: PAL[i % PAL.length], opacity: r?.enabled === false ? 0.3 : 1 }, title: id + ": " + describeRange(r.score), onclick: () => edit(id) }, stripMini(r?.display || id));
      bands.append(band);
    });
    const marker = el("div", { class: "marker", "data-v": score, style: { left: `calc(${pos(score)}% )` } });
    const slider = el("input", { type: "range", min: MIN, max: MAX, value: score, style: { width: "100%", marginTop: "8px" } });
    slider.addEventListener("input", () => { score = +slider.value; marker.dataset.v = score; marker.style.left = pos(score) + "%"; result(); });
    const res = el("div", { style: { marginTop: "8px" } });
    const result = () => {
      res.innerHTML = "";
      const outs = outcomesFor(score);
      res.append(el("span", { class: "muted" }, `A score of ${score} gets: `), outs.length ? outs.map(([id, r]) => el("span", { class: "pill", style: { margin: "0 4px", cursor: "pointer" }, onclick: () => edit(id) }, miniInline(r.display || id))) : el("span", { class: "muted" }, "nothing"));
      res.append(el("div", { class: "small faint" }, "Rules with conditions (streak, rank, statuses…) also need those to match."));
    };
    const bandsWrap = el("div", { style: { position: "relative" } }, bands, el("div", { style: { position: "absolute", inset: "0 10px", pointerEvents: "none" } }, marker));
    wrap.append(axis, bandsWrap);
    line.append(el("h4", {}, "Score line"), wrap, slider, res);
    result();
    list.innerHTML = "";
    for (const [id, r] of rules) {
      const acts = Array.isArray(r?.actions) ? r.actions : r?.actions ? [r.actions] : [];
      list.append(el("div", { class: "card click", onclick: () => edit(id) },
        el("div", { class: "row" }, el("div", { class: "grow" }, mcText(r?.display || id)), r?.enabled === false ? badge("off", "plain") : null, badge(describeRange(r?.score), parseRange(r?.score).max < 0 ? "bad" : "good")),
        el("div", { class: "small muted", style: { marginTop: "8px" } }, typeof r?.actions === "string" ? "runs the list “" + r.actions + "”" : acts.length + " action" + (acts.length === 1 ? "" : "s") + ": " + acts.slice(0, 3).map(summarize).join(" · ")),
        r?.conditions ? el("div", { class: "small faint" }, "conditions: " + JSON.stringify(r.conditions)) : null));
    }
  };
  const edit = (id) => {
    const base = "rules." + id;
    const body = el("div", {},
      field("outcomes.yml", base + ".display", { label: "Name", type: "mini" }),
      field("outcomes.yml", base + ".score", { label: "Score range", type: "text", help: "5, 1..3, ..-1 (−1 or lower), 10.. (10 or more), >=2, <0", onChange: () => drawLine() }),
      field("outcomes.yml", base + ".enabled", { label: "Enabled", type: "bool", onChange: () => drawLine() }),
      field("outcomes.yml", base + ".punishment", { label: "Counts as a punishment (a shield blocks it)", type: "bool" }),
      field("outcomes.yml", base + ".group", { label: "Group", type: "text", help: "Rules compete within a group in most-extreme mode (default: punishments or rewards)." }),
      field("outcomes.yml", base + ".priority", { label: "Priority", type: "number", help: "Higher wins in most-extreme mode (default: how far the range is from 0)." }),
      field("outcomes.yml", base + ".conditions", { label: "Conditions", type: "yaml", help: "Extra requirements, e.g.  streak: \"<=-3\"  or  rank: \"1..3\"" }));
    body.prepend(el("div", { class: "row", style: { margin: "4px 0 10px" } }, btn("⚡ Edit actions", () => editActions("outcomes.yml", base + ".actions", { title: "Actions: " + id, hint: "Variables: <player> <score> <unique> <rank> <points> <streak> <cycle> <outcome> …" }), "primary"),
      btn("Rename", async () => { const n = await promptDialog("Rename rule", "New ID:", id); if (n && n !== id) { S.rename("outcomes.yml", base, n); closeDrawer(); window.studio.refresh(); } }),
      btn("Delete", async () => { if (await confirmDialog("Delete " + id + "?", "The rule and its actions are removed (undo with Ctrl+Z).", { ok: "Delete", danger: true })) { S.remove("outcomes.yml", base); closeDrawer(); window.studio.refresh(); } }, "danger")));
    drawer("Rule: " + id, body, { onClose: () => drawLine() });
  };
  drawLine();
}

export const setupPages = [
  { id: "overview", group: "Home", title: "Overview", icon: ICONS.home, render: overview },
  { id: "cycle", group: "Set up", title: "Cycle & schedule", icon: ICONS.cycle, render: cyclePage, owns: ["config.yml:cycle", "config.yml:activity"] },
  { id: "vault", group: "Set up", title: "Vault & heads", icon: ICONS.vault, render: vaultPage, owns: ["config.yml:vault", "config.yml:heads", "config.yml:duplicates", "config.yml:kills", "config.yml:points"] },
  { id: "scoring", group: "Set up", title: "Scoring", icon: ICONS.score, render: scoringPage, owns: ["scoring.yml"] },
  { id: "outcomes", group: "Set up", title: "Outcomes", icon: ICONS.outcome, render: outcomesPage, owns: ["outcomes.yml"] },
];
