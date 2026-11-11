// Live server pages: the running cycle and its tools, players (ranked, with each one's vault), past cycles, running
// effects, the audit log with the head tracer, and the console. The database is only read; every change is a /hca
// command sent over RCON, and anything that changes the server asks first.
import { el, btn, icon, iconBtn, chest, tooltip, toast, confirmDialog, seg, select, numberCtl, durationCtl, drawer, sprite, face, emptyBox, copyText, ICONS, humanize } from "./ui.js";
import { legacyToHtml, compileFormula, roundScore, formatDuration, parseDuration, stripMini, isObj, prettyMaterial } from "./core.js";
import * as S from "./store.js";
import { pageHead, C, outcomesFor, mcName, zoneInfo } from "./pages-setup.js";
import { ACTION_SPRITES, ACTION_DESC } from "./actions.js";

const go = (h) => { location.hash = h; };
const live = () => S.store.live;
const fmt = (ms) => (ms ? new Date(ms).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : "");
const ago = (ms) => (!ms ? "never" : Date.now() - ms < 60000 ? "just now" : formatDuration(Date.now() - ms) + " ago");
const scoreEl = (s) => el("b", { class: s > 0 ? "good-ink" : s < 0 ? "bad-ink" : "" }, s === null || s === undefined ? "?" : s);
const plural = (n, one, many) => n + " " + (n === 1 ? one : many || one + "s");

function needLive(root, title) {
  if (S.store.info.live) return false;
  root.append(pageHead(title), emptyBox("barrier", "No database in this folder yet", "The live pages read Headcount's database, and " + S.store.dir + " doesn't have one. Start the server with Headcount once, then open Studio again."));
  return true;
}
const rconOff = () => emptyBox("redstone_torch", "RCON is off for this server", "Studio sends these as commands over RCON. Set enable-rcon=true and an rcon.password in server.properties, restart the server, then open Studio again.");

export const rconReady = () => !!S.store.info.rcon;
/** Runs a command over RCON and returns the reply. Asks first when `confirm` says what the command changes. */
export async function rcon(command, { quiet, confirm, danger } = {}) {
  if (!rconReady()) { toast("RCON is off. Turn it on in server.properties, restart the server and open Studio again.", "err"); return null; }
  const cmd = command.replace(/^\//, "");
  if (confirm && !(await confirmDialog(danger ? "This can't be undone" : "Run this on the server?", el("div", {}, el("div", { class: "cmdline", style: { marginBottom: "10px" } }, el("span", {}, "/" + cmd)), el("p", { class: "muted" }, confirm)), { ok: "Run it", danger }))) return null;
  try {
    const r = await S.post("/api/rcon", { command: cmd });
    if (!quiet) toast(el("div", { class: "mc", html: legacyToHtml(r.reply || "Done, with no reply.") }));
    return r.reply;
  } catch (e) { toast("The server didn't take it: " + e.message, "err"); return null; }
}
/** A command's reply in a drawer, with its colours. */
export function showReply(title, reply) {
  drawer(title, el("div", { class: "console", style: { height: "auto", minHeight: "200px" }, html: legacyToHtml(reply || "No reply.") }));
}
const runBtn = (label, cmd, opts = {}, cls = "") => {
  const b = btn(label, async () => { const r = await rcon(typeof cmd === "function" ? cmd() : cmd, opts); if (r !== null && opts.show) showReply(opts.show, r); }, cls);
  if (!rconReady()) { b.disabled = true; b.title = "Needs RCON"; }
  return b;
};

/** The score a player would get now, from the live numbers and the formula as it is in Studio. */
export function liveScore(p, all) {
  const f = compileFormula(String(S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula")));
  if (!f.ok) return null;
  const active = all.filter((x) => x.active);
  const env = { unique: p.unique, total: p.total, own: p.own, active: active.length || 1, players: all.length, rank: active.filter((x) => x.unique > p.unique).length + 1,
    percentile: active.length ? Math.round((active.filter((x) => x.unique < p.unique).length / active.length) * 100) : 0, kills: p.kills, deaths: p.deaths, trades: p.trades,
    points: p.points, points_next: p.points_next, streak: p.streak, playtime: Math.round((p.playtime_ms || 0) / 60000), last_score: p.last_score ?? 0, bonus: p.score_bonus || 0,
    duplicates: p.total - p.unique, vault_size: p.vault_size, is_active: p.active ? 1 : 0 };
  let s = roundScore(f.eval(env), S.get("scoring.yml", "rounding") ?? "floor");
  if ((S.get("scoring.yml", "add-bonus-after-formula") ?? true) !== false) s += env.bonus;
  return isFinite(s) ? s : null;
}
const outcomeText = (score) => { const o = score === null ? [] : outcomesFor(score); return o.length ? o.map(([id, r]) => stripMini(r.display || id)).join(", ") : el("span", { class: "faint" }, "Nothing"); };
const outcomeChips = (score) => { const o = score === null ? [] : outcomesFor(score); return o.length ? el("span", { class: "row gap-s" }, o.map(([id, r]) => mcName(r.display || id))) : el("span", { class: "faint" }, "Nothing"); };

/** A wall-clock "yyyy-MM-ddTHH:mm" for a moment in the cycle's time zone, which is how /hca cycle setend reads it. */
function zoneIso(ms) {
  const zi = zoneInfo(S.get("config.yml", "cycle.time-zone") ?? "UTC") || { offset: 0 };
  const opts = { hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" };
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-US", { ...opts, timeZone: zi.iana || "UTC" }).formatToParts(new Date(zi.iana ? ms : ms + zi.offset * 60000)).map((p) => [p.type, p.value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour === "24" ? "00" : parts.hour}:${parts.minute}`;
}

// ---------------------------------------------------------------- audit lines in words
const AUDIT = {
  HEAD_CREATE: ["Head made", "player_head"], HEAD_DELETE: ["Head deleted", "lava_bucket"], HEAD_PLACE: ["Placed as a block", "oak_planks"], HEAD_BREAK: ["Block broken", "iron_pickaxe"],
  HEAD_DESTROYED: ["Head destroyed", "tnt"], HEAD_EXPIRED: ["Head expired", "clock"], VAULT_IN: ["Put in a vault", "ender_chest"], VAULT_OUT: ["Taken out of a vault", "chest"],
  VAULT_ADMIN: ["Vault changed by an admin", "ender_chest"], VAULT_REJECT: ["Refused by a vault", "barrier"], TRADE_REQUEST: ["Trade request", "paper"], TRADE_START: ["Trade opened", "bundle"],
  TRADE_COMPLETE: ["Trade done", "bundle"], TRADE_CANCEL: ["Trade cancelled", "barrier"], TRADE_RETURN: ["Trade items returned", "hopper"], KILL_DROP: ["Kill dropped a head", "iron_sword"],
  CYCLE_START: ["Cycle started", "lime_dye"], CYCLE_END: ["Cycle ended", "red_dye"], SCORE: ["Score", "experience_bottle"], OUTCOME: ["Outcome", "totem_of_undying"], POINTS: ["Points", "sunflower"],
  SHOP_PURCHASE: ["Bought in the shop", "emerald"], ACTION_APPLY: ["Action ran", "firework_rocket"], ACTION_REVERT: ["Action undone", "milk_bucket"], TITLE: ["Title", "name_tag"],
  ADMIN: ["Admin", "command_block"], DUPLICATE: ["Copied head found", "structure_void"], ITEM_DELIVERY: ["Items delivered", "chest_minecart"], GUI_ITEM_REMOVED: ["Menu item taken away", "barrier"],
};
const PERSON = /^(.+)\(([0-9a-f]{8}-[0-9a-f-]{27})\)$/i;
function fieldVal(k, v) {
  const m = PERSON.exec(v);
  if (m) return el("a", { href: "#/live-player/" + m[2], title: m[2] }, m[1]);
  if (k === "head" && /^[0-9a-f-]{8,}$/i.test(v)) return el("a", { href: "#/live-audit/head/" + v, title: "Follow this head", class: "cmd" }, v.slice(0, 8));
  return v.replace(/_/g, " ");
}
function auditItem(a) {
  const [name, spr] = AUDIT[a.type] || [humanize(a.type.toLowerCase()), "paper"];
  const f = { ...(a.fields || {}) };
  let t1 = name;
  if (a.type === "ADMIN" && f.action) { t1 = "Admin: " + f.action.replace(/-/g, " "); delete f.action; }
  if (a.type === "ACTION_APPLY" || a.type === "ACTION_REVERT") { if (f.type) { t1 += ": " + (ACTION_DESC[f.type] || f.type).toLowerCase(); delete f.type; } }
  const parts = Object.entries(f).filter(([, v]) => v !== "null").map(([k, v]) => el("span", { style: { marginRight: "12px", overflowWrap: "anywhere" } }, el("span", { class: "faint" }, k.replace(/-/g, " ") + " "), fieldVal(k, v)));
  const ts = Date.parse(a.ts);
  return el("div", { class: "item static" }, sprite(spr, 26), el("div", { class: "main" }, el("div", { class: "t1" }, t1), el("div", { class: "t2 wrap" }, parts)),
    el("div", { class: "end", title: a.ts }, isFinite(ts) ? (Date.now() - ts < 86400000 ? ago(ts) : fmt(ts)) : ""));
}

// ---------------------------------------------------------------- this cycle
async function cyclePage(root) {
  if (needLive(root, "This cycle")) return;
  const head = el("div");
  const body = el("div");
  root.append(head, body);
  const tools = cycleTools();
  const draw = () => {
    const L = live();
    head.innerHTML = ""; body.innerHTML = "";
    const acts = [runBtn([icon("refresh"), "Reload configs"], "hca reload", { confirm: "Headcount reads its files again. Open trades are cancelled and menus close first." }, "ghost"), runBtn([icon("check"), "Selftest"], "hca selftest", { quiet: true, show: "Selftest" }, "ghost")];
    if (!L) { head.append(pageHead("This cycle", null, ...acts)); body.append(el("div", { class: "empty" }, "Reading the database")); return; }
    const c = L.cycle;
    head.append(pageHead(c ? "Cycle " + c.id : "This cycle", null, ...acts));
    if (!c) {
      body.append(emptyBox("clock", "No cycle is running", "The first cycle starts on the date set under Cycle, or right away if that's set to start right away. You can also start one now.",
        runBtn("Start a cycle now", "hca cycle start", { confirm: "Starts cycle 1 now. Every player who joins gets a full vault." }, "primary"), btn("Cycle settings", () => go("#/cycle"), "ghost")));
      return;
    }
    const total = c.end_ms - c.start_ms, pct = Math.max(0, Math.min(100, (Date.now() - c.start_ms) / total * 100));
    const tl = el("div", { class: "timeline" }, el("div", { class: "bar" }), el("div", { class: "fill", style: { width: pct + "%" } }));
    const fired = new Set(L.warnings || []);
    let lastX = -100;
    for (const [id, w] of Object.entries(C("hooks.warnings") || {}).sort((a, b) => parseDuration(b[1]?.before) - parseDuration(a[1]?.before))) {
      const b = parseDuration(w?.before);
      if (!isFinite(b) || w?.enabled === false) continue;
      const x = Math.max(0, Math.min(100, (1 - b / total) * 100));
      const show = x - lastX > 8 && Math.abs(x - pct) > 6;
      if (show) lastX = x;
      tl.append(el("div", { class: "mk" + (fired.has(id) ? " done" : ""), style: { left: x + "%" }, title: (fired.has(id) ? "Sent: " : "Not sent yet: ") + formatDuration(b) + " before the end" }, el("span", {}, show ? formatDuration(b) : "")));
    }
    tl.append(el("div", { class: "mk now", style: { left: pct + "%" } }, el("span", {}, "Now")), el("div", { class: "end", style: { left: 0 } }, fmt(c.start_ms)), el("div", { class: "end", style: { right: 0 } }, fmt(c.end_ms)));
    const leader = L.top[0];
    body.append(el("section", { class: "group" }, el("div", { class: "sheet", style: { padding: "16px 18px 8px" } },
      el("div", { class: "row", style: { alignItems: "baseline" } }, el("h2", { style: { fontSize: "22px" } }, formatDuration(c.end_ms - Date.now()) + " left"), el("span", { class: "grow muted" }, "Ends " + fmt(c.end_ms))), tl)),
      el("div", { class: "stats group" },
        el("a", { href: "#/live-players" }, el("div", { class: "l" }, "Online"), L.online.length ? el("div", { class: "faces" }, L.online.slice(0, 14).map((p) => face(p.uuid, 24))) : el("div", { class: "v faint" }, "Nobody")),
        el("a", { href: "#/live-players" }, el("div", { class: "l" }, "Active this cycle"), el("div", { class: "v" }, L.activeCount, L.playerCount ? el("span", { class: "faint", style: { fontSize: "15px", fontWeight: 400 } }, "of " + L.playerCount) : null)),
        el("a", { href: leader ? "#/live-player/" + leader.uuid : "#/live-players" }, el("div", { class: "l" }, "Most unique heads"), el("div", { class: "v" }, leader ? [face(leader.uuid, 26), leader.name, el("span", { class: "faint", style: { fontSize: "15px", fontWeight: 400 } }, leader.unique)] : el("span", { class: "faint" }, "Nobody yet"))),
        el("a", { href: "#/live-effects" }, el("div", { class: "l" }, "Effects running"), el("div", { class: "v" }, L.effects))));
    body.append(tools);
    const feed = el("div", { class: "items" }, (L.recent || []).slice(0, 10).map(auditItem));
    body.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Latest in the audit log"), el("div", { class: "side" }, el("a", { href: "#/live-audit" }, "Search it"))),
      L.recent?.length ? feed : emptyBox("book", "The audit log is empty", "Heads being made, traded and moved show up here as they happen.")));
  };
  draw();
  return S.on((ev) => { if (ev.type === "live" && !root.contains(document.activeElement)) draw(); });
}
function cycleTools() {
  if (!rconReady()) return el("section", { class: "group" }, el("header", {}, el("h2", {}, "Change this cycle")), rconOff());
  const c = () => live()?.cycle;
  const endInp = el("input", { type: "datetime-local", style: { maxWidth: "230px" } });
  const zone = S.get("config.yml", "cycle.time-zone") ?? "UTC";
  let by = "1d";
  const row = (label, hint, ...ctl) => el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, label), hint ? el("div", { class: "hint" }, hint) : null), el("div", { class: "ctl" }, el("div", { class: "row" }, ...ctl)));
  const soon = (min) => runBtn(min < 60 ? "In " + min + " minutes" : "In an hour", () => "hca cycle setend " + zoneIso(Date.now() + min * 60000), { confirm: "The cycle ends " + (min < 60 ? min + " minutes" : "an hour") + " from now, with scores, outcomes and a fresh vault for everyone." }, "small");
  const box = el("section", { class: "group" }, el("header", {}, el("h2", {}, "Change this cycle")), el("div", { class: "sheet" },
    row("Ends at", "In " + zone + ", the cycle's time zone.", endInp, runBtn("Move the end", () => "hca cycle setend " + endInp.value, { confirm: "Moves the end of the running cycle." })),
    row("Move it by", null, durationCtl(by, (v) => { by = v; }, { units: ["m", "h", "d"] }), runBtn("Later", () => "hca cycle extend " + by, { confirm: "The cycle ends later by " + by + "." }), runBtn("Sooner", () => "hca cycle extend -" + String(by).replace(/^-/, ""), { confirm: "The cycle ends sooner by " + by + "." })),
    row("End soon", null, soon(10), soon(30), soon(60)),
    row("See the end first", "What ending now would do, without doing it.", runBtn("Simulate", "hca cycle simulate", { quiet: true, show: "If the cycle ended now" }, "primary")),
    row("End it now", "Scores, outcomes and points are applied, vaults are emptied and the next cycle starts.", runBtn("End the cycle", () => "hca cycle end confirm", { confirm: "Ends the cycle now and starts the next one.", danger: true }, "danger")),
    row("Start it over", "Same cycle number, full vaults again. Heads made before stop counting.", runBtn("Keep old heads", "hca reset cycle keep-heads confirm", { confirm: "Old heads stay in the world as defunct heads.", danger: true }, "danger"),
      runBtn("Delete old heads", "hca reset cycle delete-heads confirm", { confirm: "Old heads are deleted wherever Headcount finds them.", danger: true }, "danger")),
    row("Wipe everything", "Deletes all of Headcount's data and starts again at cycle 1. It asks for typed codes, so it runs from the console.", btn([icon("terminal"), "Open the console"], () => go("#/console/hca reset hard"), "ghost"))));
  queueMicrotask(() => { if (c()) endInp.value = zoneIso(c().end_ms); });
  return box;
}

// ---------------------------------------------------------------- players
let playerCache = [];
async function playersPage(root) {
  if (needLive(root, "Players")) return;
  root.append(pageHead("Players"));
  const q = el("input", { type: "search", placeholder: "Find a player", "aria-label": "Find a player" });
  const show = seg([["all", "Everyone"], ["online", "Online"], ["active", "Active"]], "all", (v) => { filter = v; draw(); });
  const body = el("div");
  const bar = el("div", { class: "toolbar" }, q, el("span", { class: "grow" }), show);
  root.append(bar, body);
  let all = [], sort = "rank", filter = "all";
  const COLS = [["rank", "#", "n hide-s"], ["name", "Player"], ["unique", "Unique", "n"], ["score", "Score now", "n"], ["outcome", "Would get", "hide-s"], ["points", "Points", "n hide-s"],
    ["streak", "Streak", "n hide-s"], ["playtime_ms", "Played", "n hide-s"], ["seen", "Last seen", "hide-s"]];
  const draw = () => {
    body.innerHTML = "";
    bar.hidden = !all.length;
    if (!all.length) { body.append(emptyBox("player_head", "Nobody has joined yet", "Players show up here after their first join, with their vault, score and everything they've bought.")); return; }
    const act = all.filter((p) => p.active).sort((a, b) => b.unique - a.unique);
    const rank = new Map(); act.forEach((p, i) => rank.set(p.uuid, i && act[i - 1].unique === p.unique ? rank.get(act[i - 1].uuid) : i + 1));
    let rows = all.map((p) => ({ ...p, rank: rank.get(p.uuid) ?? null, score: p.active ? liveScore(p, all) : null, seen: p.online ? Date.now() : p.last_quit || p.last_join }))
      .filter((p) => (!q.value || p.name.toLowerCase().includes(q.value.toLowerCase())) && (filter === "all" || p[filter]));
    const key = (p) => (sort === "rank" ? (p.rank ?? 1e9) : sort === "name" ? p.name.toLowerCase() : -(p[sort] ?? -1e9));
    rows.sort((a, b) => (key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : a.name.localeCompare(b.name)));
    const t = el("table", { class: "t" }, el("tr", {}, COLS.map(([k, label, cls]) => el("th", { class: "sort " + (cls || "") + (sort === k ? " on" : ""), onclick: () => { if (k !== "outcome") { sort = k; draw(); } } }, label))));
    for (const p of rows) t.append(el("tr", { class: "click", onclick: () => go("#/live-player/" + p.uuid) },
      el("td", { class: "n faint hide-s" }, p.rank ?? ""),
      el("td", {}, el("span", { class: "who" }, face(p.uuid, 22), el("b", {}, p.name), p.online ? el("span", { class: "dotlive", title: "Online" }) : null, p.bedrock ? el("span", { class: "tag" }, "Bedrock") : null)),
      el("td", { class: "n" }, p.unique), el("td", { class: "n" }, p.active ? scoreEl(p.score) : el("span", { class: "faint", title: "Not active this cycle yet" }, "Not active")),
      el("td", { class: "hide-s muted" }, p.active ? outcomeText(p.score) : null), el("td", { class: "n hide-s" }, p.points), el("td", { class: "n hide-s" }, p.streak),
      el("td", { class: "n hide-s" }, formatDuration(p.playtime_ms || 0)), el("td", { class: "hide-s muted" }, p.online ? "Online now" : ago(p.seen))));
    body.append(rows.length ? el("div", { class: "sheet" }, t) : el("div", { class: "empty" }, "No player by that name."));
  };
  q.addEventListener("input", draw);
  const load = async () => { try { all = await S.api("/api/live/players"); playerCache = all; if (!root.contains(document.activeElement) || document.activeElement === q) draw(); } catch (e) { body.innerHTML = ""; body.append(el("div", { class: "note bad" }, e.message)); } };
  await load();
  const iv = setInterval(load, 8000);
  return () => clearInterval(iv);
}

function formDialog(title, controls, command, { ok = "Run it", danger } = {}) {
  const line = el("span");
  const upd = () => { line.textContent = "/" + command(); };
  const body = el("div", { style: { display: "grid", gap: "12px", justifyItems: "start" }, oninput: upd, onclick: () => setTimeout(upd, 0), onchange: upd }, controls, el("div", { class: "cmdline", style: { justifySelf: "stretch" } }, line));
  upd();
  confirmDialog(title, body, { ok, danger }).then((yes) => { if (yes) rcon(command()); });
}
const lab = (text, ctl) => el("label", { style: { display: "grid", gap: "4px" } }, el("span", { class: "small muted" }, text), ctl);

async function playerPage(root, params) {
  if (needLive(root, "Player")) return;
  const uuid = params[0];
  let data;
  try { data = await S.api("/api/live/player?uuid=" + encodeURIComponent(uuid)); }
  catch (e) { root.append(pageHead("Player"), emptyBox("player_head", "No player with that ID", "They may have been removed by a hard reset.", btn("All players", () => go("#/live-players")))); return; }
  const p = data.player, name = p.name;
  const all = await S.api("/api/live/players").catch(() => []);
  const me = all.find((x) => x.uuid === uuid) || { ...p, unique: 0, total: 0, own: 0, active: false };
  const score = me.active ? liveScore(me, all) : null;
  const titlesDef = S.get("titles.yml", "titles") || {};
  const giveTitle = btn("Give a title", () => {
    let t = Object.keys(titlesDef).find((k) => !data.titles.includes(k)) || Object.keys(titlesDef)[0] || "";
    formDialog("Give " + name + " a title", lab("Title", select(Object.keys(titlesDef).map((k) => [k, stripMini(titlesDef[k]?.display || k)]), t, (v) => { t = v; })), () => `hca title ${name} grant ${t}`, { ok: "Give it" });
  }, "ghost");
  const acts = [
    btn("Points", () => {
      let op = "give", n = 5, next = false;
      formDialog("Change " + name + "'s points", [seg([["give", "Give"], ["take", "Take"], ["set", "Set to"]], op, (v) => { op = v; }), numberCtl(n, (v) => { n = v; }, { min: 0, step: 1, unit: "points" }),
        el("label", { class: "switchrow" }, el("input", { type: "checkbox", onchange: (e) => { next = e.target.checked; } }), "For next cycle instead")], () => `hca points ${name} ${op} ${n}${next ? " next" : ""}`, { ok: "Change points" });
    }),
    btn("Heads", () => {
      let n = 3, owner = "self", where = "inventory";
      formDialog("Give " + name + " heads", [numberCtl(n, (v) => { n = v; }, { min: 1, step: 1, unit: "heads" }),
        lab("Whose heads", select([["self", "Their own"], ...all.filter((x) => x.uuid !== uuid).map((x) => [x.name, x.name + "'s"])], owner, (v) => { owner = v; })),
        lab("Put them in", seg([["inventory", "Inventory"], ["vault", "Vault"]], where, (v) => { where = v; }))], () => `hca giveheads ${name} ${n} ${owner} current ${where}`, { ok: "Make the heads" });
    }),
    btn("Reset", () => {
      let what = "vault";
      formDialog("Reset part of " + name, [lab("What to reset", select([["vault", "Vault"], ["points", "Points"], ["titles", "Titles"], ["streak", "Streak"], ["effects", "Effects"], ["history", "History"], ["all", "All of it"]], what, (v) => { what = v; })),
        el("p", { class: "muted", style: { margin: 0 } }, "This can't be undone.")], () => `hca reset ${name} ${what} confirm`, { ok: "Reset", danger: true });
    }, "danger"),
  ];
  if (!rconReady()) acts.forEach((b) => { b.disabled = true; b.title = "Needs RCON"; });
  root.append(el("div", { class: "crumbs" }, el("a", { href: "#/live-players" }, "Players")),
    el("div", { class: "playerhead" }, face(uuid, 56), el("div", { class: "grow" }, el("h1", {}, name), el("div", { class: "muted" }, me.online ? "Online now" : "Last seen " + ago(me.last_quit || me.last_join),
      me.active ? ", active this cycle" : ", not active this cycle yet", p.bedrock ? ", on Bedrock" : "")), el("div", { class: "row acts" }, acts)));
  root.append(el("div", { class: "stats group" },
    el("div", {}, el("div", { class: "l" }, "Unique heads"), el("div", { class: "v" }, me.unique, el("span", { class: "faint", style: { fontSize: "15px", fontWeight: 400 } }, "of " + me.total + " in the vault"))),
    el("div", {}, el("div", { class: "l" }, "Score if it ended now"), el("div", { class: "v" }, me.active ? scoreEl(score) : el("span", { class: "faint" }, "Not active")), me.active && outcomesFor(score ?? 0).length ? el("div", { style: { marginTop: "6px" } }, outcomeChips(score)) : null),
    el("div", {}, el("div", { class: "l" }, "Points"), el("div", { class: "v" }, p.points, p.points_next ? el("span", { class: "faint", style: { fontSize: "15px", fontWeight: 400 } }, "+" + p.points_next + " next cycle") : null)),
    el("div", {}, el("div", { class: "l" }, "Streak"), el("div", { class: "v" }, p.streak, el("span", { class: "faint", style: { fontSize: "15px", fontWeight: 400 } }, p.last_score !== null && p.last_score !== undefined ? "last score " + p.last_score : "")))));

  // The vault, as the chest they see.
  const size = Math.max(1, me.vault_size || p.vault_size || C("vault.size") || 27);
  const bySlot = Object.fromEntries((data.vault || []).map((v) => [v.slot, v]));
  const owners = {};
  for (const v of data.vault || []) (owners[v.head_owner] ||= { name: v.head_owner_name, n: 0 }).n++;
  let pick = null;
  const vaultBox = el("div");
  const drawVault = () => {
    vaultBox.innerHTML = "";
    const rows = Math.min(6, Math.ceil(Math.min(size, 54) / 9));
    vaultBox.append(chest(rows, S.get("config.yml", "vault.gui.title") ?? "", (i) => {
      if (i >= size) return { cls: "dim", item: C("vault.gui.locked-slot.material") || "BLACK_STAINED_GLASS_PANE" };
      const v = bySlot[i];
      if (!v) return null;
      return { item: { material: "PLAYER_HEAD", head: v.head_owner_name }, cls: pick && pick !== v.head_owner ? "dim" : pick ? "sel" : "", title: v.head_owner_name,
        tip: () => tooltip("<yellow>" + v.head_owner_name + "'s Head", ["<gray>From " + String(v.source).replace(/-/g, " "), "<gray>Made " + fmt(v.created_ms), "<dark_gray>" + v.head_id, "<dark_gray>Click to follow it"]),
        onclick: () => go("#/live-audit/head/" + v.head_id) };
    }, { vars: { owner: name, unique: me.unique, total: me.total, size, used: (data.vault || []).length, score: score ?? 0 } }));
  };
  drawVault();
  const legend = el("div", { class: "legend" }, Object.entries(owners).sort((a, b) => b[1].n - a[1].n).map(([o, x]) => el("button", { class: "linkbtn row", style: { color: "var(--ink)", fontWeight: 400, textAlign: "left" },
    title: "Show only these", onclick: () => { pick = pick === o ? null : o; drawVault(); } }, face(o, 20), el("span", { class: "grow" }, o === uuid ? x.name + " (their own)" : x.name), el("span", { class: "faint" }, x.n))));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Vault")),
    data.vault?.length ? el("div", { class: "row", style: { alignItems: "flex-start", gap: "28px" } }, vaultBox, legend) : emptyBox("ender_chest", "Empty vault", me.active || p.setup_cycle ? "Nothing in it this cycle." : "Their vault fills when they first join during a cycle.")));

  // Effects, titles, purchases, past cycles.
  const effects = el("div", { class: "items" }, data.effects.map((e) => effectItem({ ...e, name, uuid })));
  const ownedTitles = el("div", { class: "items" }, data.titles.map((t) => el("div", { class: "item static" }, sprite(titlesDef[t]?.icon || "NAME_TAG", 26), el("div", { class: "main" }, el("div", { class: "t1" }, mcName(titlesDef[t]?.display || t)),
    el("div", { class: "t2" }, p.equipped_title === t ? "Wearing it" : titlesDef[t] ? "" : "Not in titles.yml any more")),
    rconReady() ? iconBtn("close", "Take this title away", () => rcon(`hca title ${name} revoke ${t}`, { confirm: "Takes the title " + t + " from " + name + "." }), "sm") : null)),
    data.flags.map((f) => el("div", { class: "item static" }, sprite("redstone", 26), el("div", { class: "main" }, el("div", { class: "t1" }, "Flag " + f), el("div", { class: "t2" }, "Statuses can check it")),
      rconReady() ? iconBtn("close", "Clear this flag", () => rcon(`hca flag ${name} clear ${f}`, { confirm: "Clears the flag " + f + "." }), "sm") : null)));
  const shopEntry = (tier, id) => S.get("shop.yml", `tiers.${tier}.entries.${id}`) || {};
  const bought = el("div", { class: "items" }, data.purchases.slice(0, 15).map((x) => { const e = shopEntry(x.tier, x.entry); return el("div", { class: "item static" }, sprite(e.display || "PAPER", 26),
    el("div", { class: "main" }, el("div", { class: "t1" }, stripMini(e.display?.name || x.entry)), el("div", { class: "t2" }, "Cycle " + x.cycle + ", " + fmt(x.ms))), el("div", { class: "end" }, plural(x.price, "point"))); }));
  root.append(el("div", { class: "grid g2" },
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Effects running")), data.effects.length ? effects : el("div", { class: "sheet pad muted" }, "None right now.")),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Titles and flags"), el("div", { class: "side" }, rconReady() && Object.keys(titlesDef).length ? giveTitle : null)), data.titles.length || data.flags.length ? ownedTitles : el("div", { class: "sheet pad muted" }, "No titles or flags.")),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Bought")), data.purchases.length ? bought : el("div", { class: "sheet pad muted" }, "Nothing from the shop yet.")),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Past cycles")), data.results.length ? pastCycles(data.results) : el("div", { class: "sheet pad muted" }, "This is their first cycle."))));
  const more = el("div", { class: "row" }, runBtn("Their statuses now", "hca status " + name, { quiet: true, show: name + "'s statuses" }, "ghost small"), runBtn("Their placeholder values", "hca placeholders " + name, { quiet: true, show: "Placeholders for " + name }, "ghost small"));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "In the audit log"), el("div", { class: "side" }, more)), auditView(name, { compact: true })));
}
function pastCycles(results) {
  const rs = [...results].reverse();
  const scored = rs.filter((r) => r.score !== null && r.score !== undefined);
  const max = Math.max(1, ...scored.map((r) => Math.abs(r.score)));
  const bars = el("div", { style: { display: "flex", alignItems: "center", gap: "3px", height: "64px", padding: "10px 18px 4px" } }, rs.slice(-24).map((r) => {
    const s = r.score ?? 0, h = Math.max(2, Math.abs(s) / max * 26);
    return el("div", { title: "Cycle " + r.cycle + ": " + (r.active ? "score " + s : "not active"), style: { flex: 1, maxWidth: "22px", height: "52px", position: "relative" } },
      el("div", { style: { position: "absolute", left: 0, right: 0, height: h + "px", borderRadius: "2px", background: !r.active ? "var(--line-2)" : s < 0 ? "var(--bad)" : s > 0 ? "var(--good)" : "var(--ink-3)", ...(s < 0 ? { top: "26px" } : { bottom: "26px" }) } }));
  }));
  return el("div", { class: "sheet" }, bars, el("table", { class: "t" }, results.slice(0, 8).map((r) => el("tr", { class: "click", onclick: () => go("#/live-history/" + r.cycle) },
    el("td", {}, "Cycle " + r.cycle), el("td", { class: "n" }, r.active ? scoreEl(r.score) : el("span", { class: "faint" }, "Not active")), el("td", { class: "muted" }, r.active ? plural(r.unique_heads, "unique head") : ""),
    el("td", { class: "hide-s" }, r.outcomes ? el("span", { class: "row gap-s" }, r.outcomes.split(",").filter(Boolean).map((id) => mcName(S.get("outcomes.yml", "rules." + id + ".display") || id))) : null)))));
}

// ---------------------------------------------------------------- past cycles
async function historyPage(root, params) {
  if (needLive(root, "Past cycles")) return;
  if (params[0]) {
    const id = +params[0];
    const rows = await S.api("/api/live/cycle?id=" + id);
    root.append(el("div", { class: "crumbs" }, el("a", { href: "#/live-history" }, "Past cycles")), pageHead("Cycle " + id));
    if (!rows.length) { root.append(emptyBox("book", "No results for this cycle", "Results are written when a cycle ends.")); return; }
    const t = el("table", { class: "t" }, el("tr", {}, el("th", { class: "n" }, "Rank"), el("th", {}, "Player"), el("th", { class: "n" }, "Unique heads"), el("th", { class: "n" }, "Score"), el("th", { class: "hide-s" }, "Got"),
      el("th", { class: "n hide-s" }, "Points after"), el("th", { class: "n hide-s" }, "Streak")));
    for (const r of rows) t.append(el("tr", { class: "click", onclick: () => go("#/live-player/" + r.uuid) }, el("td", { class: "n faint" }, r.rank ?? ""), el("td", {}, el("span", { class: "who" }, face(r.uuid, 22), el("b", {}, r.name))),
      el("td", { class: "n" }, r.unique_heads), el("td", { class: "n" }, r.active ? scoreEl(r.score) : el("span", { class: "faint" }, "Not active")),
      el("td", { class: "hide-s" }, r.outcomes ? el("span", { class: "row gap-s" }, r.outcomes.split(",").filter(Boolean).map((o) => mcName(S.get("outcomes.yml", "rules." + o + ".display") || o))) : null),
      el("td", { class: "n hide-s" }, r.points_after), el("td", { class: "n hide-s" }, r.streak)));
    root.append(el("div", { class: "sheet" }, t));
    return;
  }
  root.append(pageHead("Past cycles"));
  const cycles = await S.api("/api/live/history");
  if (!cycles.length) { root.append(emptyBox("book", "No cycles yet", "Each cycle shows up here once it starts, and its results once it ends.")); return; }
  root.append(el("div", { class: "items" }, cycles.map((c) => {
    const running = c.state === "active";
    const line = running ? "Running, ends " + fmt(c.end_ms) : c.active ? `${plural(c.active, "player")} scored, ${c.rewarded} above 0 and ${c.punished} below. Average ${c.avg}.` : "Nobody was active.";
    return el("div", { class: "item", onclick: () => go(running ? "#/live" : "#/live-history/" + c.id) }, sprite(running ? "clock" : "book", 28),
      el("div", { class: "main" }, el("div", { class: "t1" }, "Cycle " + c.id), el("div", { class: "t2" }, line)),
      el("div", { class: "end hide-s" }, c.top ? "Best: " + c.top.split(", ")[0] : "", el("div", {}, fmt(c.start_ms))));
  })));
}

// ---------------------------------------------------------------- effects
function sourceText(src) {
  const [kind, id] = String(src || "").split(/:(.*)/);
  if (kind === "outcome") return "Outcome " + stripMini(S.get("outcomes.yml", "rules." + id + ".display") || id);
  if (kind === "shop") { for (const T of Object.values(S.get("shop.yml", "tiers") || {})) if (T?.entries?.[id]) return "Bought: " + stripMini(T.entries[id].display?.name || id); return "Bought: " + id; }
  if (kind === "hook") return humanize(id) + " hook";
  if (kind === "admin" || kind === "command") return "An admin";
  return humanize(src || "");
}
function effectLeft(e) {
  if (e.ends_ms) return formatDuration(Math.max(0, e.ends_ms - Date.now())) + " left";
  if (e.remaining_ms) return formatDuration(e.remaining_ms) + " of play left";
  if (e.cycles_left) return plural(e.cycles_left, "more cycle");
  return { CYCLE: "Until the cycle ends", DEATH: "Until they die", PERMANENT: "For good", MANUAL: "Until removed" }[e.duration_kind] || humanize(String(e.duration_kind || "").toLowerCase());
}
function effectItem(e, withName) {
  const rm = rconReady() ? iconBtn("close", "End it now", () => rcon(`hca effects ${e.name} remove ${e.id.slice(0, 8)}`, { confirm: "Ends this effect now and undoes what it did." }), "sm") : null;
  return el("div", { class: "item static" }, withName ? face(e.uuid, 28) : sprite(ACTION_SPRITES[e.type] || "potion", 26),
    el("div", { class: "main" }, el("div", { class: "t1" }, withName ? [el("a", { href: "#/live-player/" + e.uuid }, e.name), ": " + (ACTION_DESC[e.type] || e.type).toLowerCase()] : ACTION_DESC[e.type] || e.type),
      el("div", { class: "t2" }, sourceText(e.source) + (e.state && e.state !== "ACTIVE" ? ", " + humanize(e.state.toLowerCase()).toLowerCase() : ""))),
    el("div", { class: "end" }, effectLeft(e)), rm);
}
async function effectsPage(root) {
  if (needLive(root, "Effects")) return;
  root.append(pageHead("Effects"));
  const rows = await S.api("/api/live/effects");
  if (!rows.length) { root.append(emptyBox("potion", "Nothing timed is running", "Punishments, shop buffs, shields and bounties with a duration are listed here while they last, and can be ended early.")); return; }
  root.append(el("div", { class: "items" }, rows.map((e) => effectItem(e, true))));
}

// ---------------------------------------------------------------- audit log and the head tracer
const TYPE_NAMES = Object.entries(AUDIT).map(([k, [n]]) => [k, n]);
function auditView(initial, { compact } = {}) {
  const box = el("div");
  const q = el("input", { type: "search", placeholder: "A player, a head ID or any word", value: initial || "", "aria-label": "Search the audit log" });
  let type = "";
  const res = el("div");
  let page = 1;
  const run = async () => {
    res.innerHTML = "";
    res.append(el("div", { class: "empty" }, "Searching"));
    try {
      const r = await S.api(`/api/audit?q=${encodeURIComponent(q.value)}&type=${type}&page=${page}`);
      res.innerHTML = "";
      if (!r.lines.length) { res.append(compact ? el("div", { class: "sheet pad muted" }, "Nothing in the audit log for them yet.") : emptyBox("spyglass", page > 1 ? "No more lines" : "Nothing matches", q.value || type ? "Try fewer words, or any type." : "The audit log is empty. It fills as heads are made, moved and traded.")); }
      else res.append(el("div", { class: "items" }, r.lines.map(auditItem)));
      if (page > 1 || r.more) res.append(el("div", { class: "row", style: { marginTop: "10px" } }, page > 1 ? btn([icon("left"), "Newer"], () => { page--; run(); }, "small ghost") : null, el("span", { class: "grow" }), r.more ? btn(["Older", icon("right")], () => { page++; run(); }, "small ghost") : null));
    } catch (e) { res.innerHTML = ""; res.append(el("div", { class: "note bad" }, e.message)); }
  };
  q.addEventListener("keydown", (e) => { if (e.key === "Enter") { page = 1; run(); } });
  if (!compact) box.append(el("div", { class: "toolbar" }, q, select([["", "Any kind"], ...TYPE_NAMES], type, (v) => { type = v; page = 1; run(); }), btn("Search", () => { page = 1; run(); }, "primary")));
  box.append(res);
  run();
  return box;
}
async function auditPage(root, params) {
  if (params[0] === "head" && params[1]) return tracer(root, params[1]);
  root.append(pageHead("Audit log", null, btn("Follow a head", async () => {
    const inp = el("input", { type: "text", placeholder: "3f9a1c2e-0b4d-...", spellcheck: "false", style: { fontFamily: "var(--mono)" } });
    if (await confirmDialog("Follow a head", el("div", {}, el("p", { class: "muted" }, "Paste a head ID. /hca inspect shows the one in your hand."), inp), { ok: "Follow it" }) && inp.value.trim()) go("#/live-audit/head/" + inp.value.trim());
  }, "ghost")));
  root.append(auditView(params[0] ? decodeURIComponent(params[0]) : ""));
}
async function tracer(root, id) {
  root.append(el("div", { class: "crumbs" }, el("a", { href: "#/live-audit" }, "Audit log")), pageHead("One head's story", el("span", { class: "cmd" }, id)));
  let r;
  try { r = await S.api("/api/audit/head?id=" + encodeURIComponent(id)); } catch (e) { root.append(el("div", { class: "note bad" }, e.message)); return; }
  if (!r.lines.length) { root.append(emptyBox("spyglass", "This head isn't in the audit log", "It may be older than the log keeps (audit.keep-days), or its kind of event isn't logged.", btn("Audit log settings", () => window.studio.goToOption("config.yml", "audit.keep-days"), "ghost"))); return; }
  const list = el("ol", { class: "trace" });
  for (const a of r.lines) {
    const [name, spr] = AUDIT[a.type] || [a.type, "paper"];
    const f = Object.entries(a.fields || {}).filter(([k, v]) => k !== "head" && v !== "null");
    list.append(el("li", {}, el("span", { class: "pt" }, sprite(spr, 20)), el("div", { class: "grow" }, el("div", { class: "row gap-s" }, el("b", {}, name), el("span", { class: "small faint" }, fmt(Date.parse(a.ts)))),
      el("div", { class: "small muted" }, f.map(([k, v]) => el("span", { style: { marginRight: "12px" } }, el("span", { class: "faint" }, k.replace(/-/g, " ") + " "), fieldVal(k, v)))))));
  }
  root.append(el("div", { class: "sheet" }, list));
}

// ---------------------------------------------------------------- console
async function consolePage(root, params) {
  root.append(pageHead("Console"));
  if (!rconReady()) { root.append(rconOff()); return; }
  const out = el("div", { class: "console", role: "log" });
  const inp = el("input", { type: "text", placeholder: "hca cycle info", list: "cmds", spellcheck: "false", "aria-label": "Command", style: { fontFamily: "var(--mono)" } });
  const cmds = [...new Set((S.store.catalog?.commands || []).flatMap((c) => c.syntax).map((s) => s.replace(/^\//, "")))];
  let hist = [];
  try { hist = JSON.parse(localStorage.getItem("hc-console") || "[]"); } catch (e) { /* private window */ }
  let hi = hist.length;
  const run = async (cmd) => {
    cmd = cmd.trim().replace(/^\//, "");
    if (!cmd) return;
    if (/^(hca reset hard\b|stop$|restart$)/.test(cmd) && !(await confirmDialog("Run /" + cmd + "?", cmd === "stop" ? "The server shuts down." : cmd === "restart" ? "The server restarts." : "Headcount asks for typed codes next, one at a time. Each code is in the reply.", { ok: "Run it", danger: true }))) return;
    hist.push(cmd); hist = hist.slice(-100); hi = hist.length;
    try { localStorage.setItem("hc-console", JSON.stringify(hist)); } catch (e) { /* ignore */ }
    out.append(el("div", { class: "cmd" }, "> /" + cmd));
    try { const r = await S.post("/api/rcon", { command: cmd }); out.append(el("div", { html: legacyToHtml(r.reply || "Done, with no reply.") })); }
    catch (e) { out.append(el("div", { class: "err" }, e.message)); }
    out.scrollTop = out.scrollHeight;
  };
  inp.addEventListener("keydown", (e) => {
    if (e.key === "Enter") { run(inp.value); inp.value = ""; }
    if (e.key === "ArrowUp" && hi > 0) { inp.value = hist[--hi]; e.preventDefault(); }
    if (e.key === "ArrowDown") { hi = Math.min(hist.length, hi + 1); inp.value = hist[hi] || ""; }
  });
  const quick = ["hca cycle info", "hca selftest", "hca integrations", "list", "hca reload"];
  if (!hist.length) out.append(el("div", { style: { color: "#9aa5ad" } }, "Replies from the server show here, in colour. Up and down arrows go through earlier commands."));
  root.append(el("div", { class: "row", style: { marginBottom: "10px" } }, quick.map((q) => btn("/" + q, () => run(q), "small ghost"))), out,
    el("div", { class: "row", style: { marginTop: "10px", flexWrap: "nowrap" } }, inp, btn("Run", () => { run(inp.value); inp.value = ""; }, "primary")),
    el("datalist", { id: "cmds" }, cmds.map((c) => el("option", { value: c }))));
  if (params[0]) inp.value = decodeURIComponent(params.join("/"));
  setTimeout(() => inp.focus(), 30);
}

export const livePages = [
  { id: "live", group: "Live server", title: "This cycle", icon: ICONS.cycle, render: cyclePage },
  { id: "live-players", group: "Live server", title: "Players", icon: ICONS.players, render: playersPage },
  { id: "live-player", group: "Live server", title: "Player", icon: ICONS.players, render: playerPage, hidden: true },
  { id: "live-history", group: "Live server", title: "Past cycles", icon: ICONS.history, render: historyPage },
  { id: "live-effects", group: "Live server", title: "Effects", icon: ICONS.effects, render: effectsPage },
  { id: "live-audit", group: "Live server", title: "Audit log", icon: ICONS.audit, render: auditPage },
  { id: "console", group: "Live server", title: "Console", icon: ICONS.console, render: consolePage },
];
export const liveAliases = { "live-cycle": "live", "live-top": "live-players" };
export const players = () => playerCache;
