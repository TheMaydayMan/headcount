// Live server pages: read-only views of the running server's database and audit log, plus RCON commands.
import { el, btn, badge, sectionTitle, itemTile, chest, tooltip, hoverTip, toast, confirmDialog, promptDialog, seg, ICONS, mcText } from "./ui.js";
import { legacyToHtml, compileFormula, roundScore, formatDuration, esc } from "./core.js";
import * as S from "./store.js";
import { pageHead, C, outcomesFor } from "./pages-setup.js";

const go = (h) => { location.hash = h; };
const ago = (ms) => (ms ? formatDuration(Date.now() - ms) + " ago" : "never");
const when = (ms) => (ms ? new Date(ms).toLocaleString() : "—");
const live = () => S.store.live;

function needLive(root) {
  if (S.store.info.live) return false;
  root.append(pageHead("Live server", ""), el("div", { class: "note warn" }, "No Headcount database in " + S.store.dir + ". Start the server once (with Headcount), then reopen Studio."));
  return true;
}
export const rconReady = () => !!S.store.info.rcon;
/** Runs a command over RCON and returns the reply as HTML. Asks first for anything that changes the server. */
export async function rcon(command, { quiet, confirm } = {}) {
  if (!rconReady()) { toast("RCON is off: set enable-rcon=true and rcon.password in server.properties, restart the server, and reopen Studio.", "err"); return null; }
  if (confirm && !(await confirmDialog("Run this command?", el("div", {}, el("code", {}, "/" + command.replace(/^\//, "")), el("p", { class: "muted" }, confirm)), { ok: "Run" }))) return null;
  try {
    const r = await S.post("/api/rcon", { command: command.replace(/^\//, "") });
    if (!quiet) toast(el("div", { class: "mc", html: legacyToHtml(r.reply || "(no reply)") }), "info");
    return r.reply;
  } catch (e) { toast("RCON: " + e.message, "err"); return null; }
}

/** The score a player would have now, from the live data and the current formula. */
export function liveScore(p, all) {
  const f = compileFormula(String(S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula")));
  const active = all.filter((x) => x.active);
  const better = active.filter((x) => x.unique > p.unique).length;
  const env = { unique: p.unique, total: p.total, own: p.own, active: active.length || 1, players: all.length, rank: better + 1, percentile: active.length ? Math.round((active.filter((x) => x.unique < p.unique).length / active.length) * 100) : 0,
    kills: p.kills, deaths: p.deaths, trades: p.trades, points: p.points, points_next: p.points_next, streak: p.streak, playtime: Math.round((p.playtime_ms || 0) / 60000),
    last_score: p.last_score ?? 0, bonus: p.score_bonus || 0, duplicates: p.total - p.unique, vault_size: p.vault_size, is_active: p.active ? 1 : 0 };
  if (!f.ok) return null;
  let s = roundScore(f.eval(env), S.get("scoring.yml", "rounding") ?? "floor");
  if ((S.get("scoring.yml", "add-bonus-after-formula") ?? true) !== false) s += env.bonus;
  return s;
}

// ---------------------------------------------------------------- dashboard
async function dashboard(root) {
  if (needLive(root)) return;
  root.append(pageHead("Live server", "What's happening on the server right now. Refreshes every few seconds.",
    btn("↻ Reload configs", () => rcon("hca reload", { confirm: "Reloads Headcount's configs on the running server (open trades are cancelled)." })), btn("✓ Selftest", () => rcon("hca selftest"))));
  const body = el("div");
  root.append(body);
  const draw = () => {
    const L = live();
    body.innerHTML = "";
    if (!L) { body.append(el("div", { class: "empty" }, "Loading…")); return; }
    const c = L.cycle;
    if (!c) body.append(el("div", { class: "note warn" }, "No cycle is running."));
    else {
      const pct = Math.max(0, Math.min(100, (Date.now() - c.start_ms) / (c.end_ms - c.start_ms) * 100));
      body.append(el("div", { class: "grid g4" },
        el("div", { class: "card" }, el("div", { class: "kpi" }, el("div", { class: "ring", style: { "--p": pct.toFixed(1) } }, el("div", {}, el("b", {}, Math.round(pct) + "%"))),
          el("div", {}, el("h4", {}, "Cycle " + c.id), el("div", {}, formatDuration(c.end_ms - Date.now()) + " left"), el("div", { class: "small faint" }, "run " + (c.token || "—"))))),
        el("div", { class: "card click", onclick: () => go("#/live-players") }, el("h4", {}, "Players"), el("div", { class: "big" }, L.online.length + " online"), el("div", { class: "muted small" }, `${L.activeCount} active · ${L.playerCount} ever`)),
        el("div", { class: "card click", onclick: () => go("#/live-top") }, el("h4", {}, "Leading"), el("div", { class: "big" }, L.top[0] ? L.top[0].name : "—"), el("div", { class: "muted small" }, L.top[0] ? L.top[0].unique + " unique heads" : "")),
        el("div", { class: "card click", onclick: () => go("#/live-effects") }, el("h4", {}, "Effects running"), el("div", { class: "big" }, L.effects), el("div", { class: "muted small" }, "timed actions"))));
      const tl = el("div", { class: "timeline" }, el("div", { class: "bar" }), el("div", { class: "mk now", style: { left: pct + "%" } }, el("span", {}, "now")));
      for (const w of L.warnings || []) tl.append(el("div", { class: "mk", style: { left: "96%" }, title: "fired" }, el("span", {}, w)));
      body.append(el("div", { class: "card", style: { marginTop: "14px" } }, el("div", { class: "row" }, el("h4", { class: "grow" }, "This cycle"), el("span", { class: "small muted" }, when(c.start_ms) + " → " + when(c.end_ms))), tl,
        el("div", { class: "small muted" }, "Warnings fired: " + ((L.warnings || []).join(", ") || "none yet") + " · vault " + c.vault_size + " · starting heads " + c.starting_heads)));
    }
    const split = el("div", { class: "grid g2", style: { marginTop: "14px" } });
    const top = el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "Top players")), el("div", { class: "pb" }));
    for (const [i, p] of L.top.slice(0, 10).entries()) top.lastChild.append(el("div", { class: "row", style: { padding: "5px 0", borderTop: i ? "1px solid var(--line)" : "", cursor: "pointer" }, onclick: () => go("#/live-player/" + p.uuid) },
      el("b", { style: { width: "24px" } }, i + 1), el("span", { class: "grow" }, p.name), badge(p.unique + " unique", "plain")));
    if (!L.top.length) top.lastChild.append(el("div", { class: "empty" }, "No heads in vaults yet."));
    const feed = el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", { class: "grow" }, "Recent events"), el("a", { href: "#/live-audit" }, "audit log →")), el("div", { class: "pb" }));
    for (const a of L.recent || []) feed.lastChild.append(auditRow(a, true));
    split.append(top, feed);
    body.append(split);
  };
  draw();
  return S.on((ev) => { if (ev.type === "live") draw(); });
}

function auditRow(a, compact) {
  const fields = Object.entries(a.fields || {}).filter(([k]) => !compact || !["actor"].includes(k)).map(([k, v]) => el("span", { class: "small" }, el("span", { class: "faint" }, k + "="), v.replace(/_/g, " ")));
  const head = (a.fields || {}).head;
  return el("div", { style: { padding: "6px 0", borderTop: "1px solid var(--line)" } },
    el("div", { class: "row gap-s" }, badge(a.type, a.type.startsWith("HEAD") ? "info" : a.type.includes("CYCLE") ? "warn" : a.type === "ADMIN" ? "bad" : "plain"), el("span", { class: "small faint" }, (a.ts || "").replace("T", " ").slice(0, 19)),
      head ? el("a", { href: "#/live-audit/head/" + head, class: "small" }, "trace head") : null),
    el("div", { class: "row gap-s", style: { marginTop: "2px" } }, fields));
}

// ---------------------------------------------------------------- players
async function players(root) {
  if (needLive(root)) return;
  root.append(pageHead("Players", "Everyone who has joined. Scores are what they'd get if the cycle ended now, with your current formula."));
  const q = el("input", { type: "search", placeholder: "Search players…" });
  const body = el("div", { class: "panel" }, el("div", { class: "pb" }));
  root.append(el("div", { style: { marginBottom: "10px" } }, q), body);
  let all = [];
  let sort = "unique";
  const draw = () => {
    const t = el("table", { class: "t" });
    const th = (k, label) => el("th", { style: { cursor: "pointer" }, onclick: () => { sort = k; draw(); } }, label + (sort === k ? " ▾" : ""));
    t.append(el("tr", {}, th("name", "Player"), th("unique", "Unique"), th("score", "Score now"), th("points", "Points"), th("streak", "Streak"), th("playtime_ms", "Playtime"), th("last_join", "Last seen")));
    const rows = all.filter((p) => !q.value || p.name.toLowerCase().includes(q.value.toLowerCase())).map((p) => ({ ...p, score: liveScore(p, all) }));
    rows.sort((a, b) => (sort === "name" ? a.name.localeCompare(b.name) : (b[sort] ?? -1e9) - (a[sort] ?? -1e9)));
    for (const p of rows) t.append(el("tr", { class: "click", onclick: () => go("#/live-player/" + p.uuid) },
      el("td", {}, el("span", { class: "row gap-s" }, el("span", { style: { width: "8px", height: "8px", borderRadius: "50%", background: p.online ? "var(--good)" : "var(--line-2)" } }), el("b", {}, p.name), p.bedrock ? badge("bedrock", "info") : null, p.active ? null : badge("not active", "plain"))),
      el("td", {}, p.unique, el("span", { class: "faint small" }, " / " + p.total)), el("td", {}, p.score === null ? "?" : el("b", { style: { color: p.score > 0 ? "var(--good)" : p.score < 0 ? "var(--bad)" : "" } }, p.score)),
      el("td", {}, p.points, p.points_next ? el("span", { class: "faint small" }, " +" + p.points_next) : null), el("td", {}, p.streak), el("td", {}, formatDuration(p.playtime_ms || 0)),
      el("td", { class: "small muted" }, p.online ? "online now" : ago(p.last_quit || p.last_join))));
    body.firstChild.innerHTML = "";
    body.firstChild.append(rows.length ? t : el("div", { class: "empty" }, "No players."));
  };
  q.addEventListener("input", draw);
  const load = async () => { try { all = await S.api("/api/live/players"); draw(); } catch (e) { body.firstChild.textContent = e.message; } };
  await load();
  const iv = setInterval(load, 8000);
  return () => clearInterval(iv);
}

async function player(root, params) {
  if (needLive(root)) return;
  const uuid = params[0];
  let data;
  try { data = await S.api("/api/live/player?uuid=" + encodeURIComponent(uuid)); } catch (e) { root.append(el("div", { class: "note bad" }, e.message)); return; }
  const p = data.player;
  const name = p.name;
  const all = await S.api("/api/live/players").catch(() => [p]);
  const me = all.find((x) => x.uuid === uuid) || p;
  const score = liveScore(me, all);
  root.append(el("div", { class: "crumbs" }, el("a", { href: "#/live-players" }, "Players"), " › " + name),
    pageHead(name, el("span", {}, me.online ? badge("online", "good") : badge("offline", "plain"), " ", el("code", { class: "small" }, uuid)),
      btn("Give points", async () => { const n = await promptDialog("Give points", "How many points to give " + name + "? (negative takes)", "5"); if (n) rcon(`hca points ${name} ${+n < 0 ? "take" : "give"} ${Math.abs(+n)}`, { confirm: "Changes " + name + "'s points." }); }),
      btn("Give heads", async () => { const n = await promptDialog("Give heads", `How many of ${name}'s own heads to put in their inventory?`, "3"); if (n) rcon(`hca giveheads ${name} ${+n}`, { confirm: "Makes new economy heads." }); }),
      btn("Reset…", async () => { const w = await promptDialog("Reset part of " + name, "vault, points, titles, streak, effects, history or all:", "vault", { check: (v) => ["vault", "points", "titles", "streak", "effects", "history", "all"].includes(v) ? null : "One of: vault, points, titles, streak, effects, history, all" }); if (w) rcon(`hca reset ${name} ${w} confirm`, { confirm: "Resets " + w + " for " + name + ". This can't be undone." }); }, "danger")));
  const outs = score === null ? [] : outcomesFor(score);
  root.append(el("div", { class: "grid g4" },
    el("div", { class: "card" }, el("h4", {}, "Unique heads"), el("div", { class: "big" }, me.unique), el("div", { class: "small muted" }, me.total + " total · " + me.own + " own")),
    el("div", { class: "card" }, el("h4", {}, "Score now"), el("div", { class: "big", style: { color: score > 0 ? "var(--good)" : score < 0 ? "var(--bad)" : "" } }, score ?? "?"), el("div", { class: "small muted" }, outs.map(([id]) => id).join(", ") || "no outcome")),
    el("div", { class: "card" }, el("h4", {}, "Points"), el("div", { class: "big" }, p.points), el("div", { class: "small muted" }, "+" + p.points_next + " next cycle")),
    el("div", { class: "card" }, el("h4", {}, "Streak"), el("div", { class: "big" }, p.streak), el("div", { class: "small muted" }, "last score " + (p.last_score ?? "—")))));
  // The vault
  const size = Math.max(1, me.vault_size || C("vault.size") || 27);
  const bySlot = Object.fromEntries((data.vault || []).map((v) => [v.slot, v]));
  const owners = [...new Set((data.vault || []).map((v) => v.head_owner))];
  const hue = (o) => { let h = 0; for (const ch of o) h = (h * 31 + ch.charCodeAt(0)) >>> 0; return `hsl(${h % 360} 45% 55%)`; };
  const rows = Math.min(6, Math.ceil(Math.min(size, 54) / 9));
  const grid = chest(rows, (S.get("config.yml", "vault.gui.title") ?? "") , (i) => {
    if (i >= size) return { cls: "dim", item: "BLACK_STAINED_GLASS_PANE" };
    const v = bySlot[i];
    if (!v) return null;
    return { item: { material: "PLAYER_HEAD" }, title: v.head_owner_name, tip: () => tooltip("<yellow>" + v.head_owner_name + "'s Head", ["<gray>source: " + v.source, "<dark_gray>" + v.head_id, "<dark_gray>made " + when(v.created_ms)]), onclick: () => go("#/live-audit/head/" + v.head_id) };
  }, { vars: { owner: name, unique: me.unique, total: me.total, size } });
  grid.querySelectorAll(".slot").forEach((s) => { const v = bySlot[+s.dataset.slot]; if (v) s.querySelector(".tile").style.background = v.head_owner === uuid ? "#c69a6c" : hue(v.head_owner); });
  root.append(sectionTitle("Vault"), el("div", { class: "row", style: { alignItems: "flex-start", gap: "20px" } }, grid,
    el("div", { class: "card grow" }, el("h4", {}, "Heads by owner"), owners.map((o) => { const v = data.vault.find((x) => x.head_owner === o); const n = data.vault.filter((x) => x.head_owner === o).length; return el("div", { class: "row", style: { padding: "3px 0" } }, el("span", { style: { width: "12px", height: "12px", borderRadius: "3px", background: o === uuid ? "#c69a6c" : hue(o) } }), el("span", { class: "grow" }, v.head_owner_name), badge("×" + n, "plain")); }),
      el("div", { class: "small muted", style: { marginTop: "8px" } }, "Click a head to trace it through the audit log."))));
  // Effects, history, titles, purchases
  const g = el("div", { class: "grid g2", style: { marginTop: "14px" } });
  g.append(el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "Active effects")), el("div", { class: "pb" },
    data.effects.length ? data.effects.map((e) => el("div", { class: "row", style: { padding: "5px 0", borderTop: "1px solid var(--line)" } }, badge(e.type, "info"), el("span", { class: "grow small muted" }, e.source), el("span", { class: "small" }, e.ends_ms ? formatDuration(e.ends_ms - Date.now()) + " left" : e.duration_kind),
      el("button", { class: "iconbtn", title: "Remove", onclick: () => rcon(`hca effects ${name} remove ${e.id.slice(0, 8)}`, { confirm: "Ends this effect now and undoes it." }) }, "✕"))) : el("div", { class: "muted small" }, "None."))),
    el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "History")), el("div", { class: "pb" }, historyBars(data.results),
      data.results.slice(0, 8).map((r) => el("div", { class: "row small", style: { padding: "4px 0", borderTop: "1px solid var(--line)" } }, el("b", { style: { width: "70px" } }, "Cycle " + r.cycle), el("span", { class: "grow" }, r.active ? `${r.unique_heads} unique → score ${r.score}` : "not active"), el("span", { class: "muted" }, (r.outcomes || "").replace(/,/g, ", ")))))),
    el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "Titles and flags")), el("div", { class: "pb" }, el("div", { class: "row" }, data.titles.map((t) => badge(t, t === p.equipped_title ? "good" : "plain")), data.flags.map((f) => badge("flag " + f, "warn"))), !data.titles.length && !data.flags.length ? el("div", { class: "muted small" }, "None.") : null)),
    el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "Purchases")), el("div", { class: "pb" }, data.purchases.length ? data.purchases.slice(0, 12).map((x) => el("div", { class: "row small", style: { padding: "3px 0" } }, el("span", { class: "grow" }, x.entry), el("span", { class: "muted" }, x.price + " pts · " + ago(x.ms)))) : el("div", { class: "muted small" }, "None."))));
  root.append(g, sectionTitle("Audit trail"), auditView(name));
}
function historyBars(results) {
  const rs = [...results].reverse().filter((r) => r.score !== null && r.score !== undefined);
  if (!rs.length) return el("div", { class: "muted small" }, "No past cycles.");
  const max = Math.max(1, ...rs.map((r) => Math.abs(r.score)));
  return el("div", { class: "bars", style: { height: "70px", marginBottom: "8px" } }, rs.slice(-20).map((r) => el("div", { class: r.score < 0 ? "neg" : "", style: { height: Math.max(3, Math.abs(r.score) / max * 70) + "px" }, title: "Cycle " + r.cycle + ": " + r.score })));
}

// ---------------------------------------------------------------- cycle
async function cycle(root) {
  if (needLive(root)) return;
  const L = live();
  const c = L?.cycle;
  root.append(pageHead("Cycle tools", "The running cycle, and the commands that change it (they go over RCON and ask first)."));
  if (!c) root.append(el("div", { class: "note warn" }, "No cycle is running.", rconReady() ? el("div", { style: { marginTop: "8px" } }, btn("Start a cycle now", () => rcon("hca cycle start", { confirm: "Starts a cycle now." }), "primary")) : null));
  else {
    root.append(el("div", { class: "card" }, el("h3", {}, "Cycle " + c.id), el("div", { class: "muted" }, when(c.start_ms) + " → " + when(c.end_ms) + " · " + formatDuration(c.end_ms - Date.now()) + " left · run " + (c.token || "—"))));
    const dt = el("input", { type: "datetime-local", style: { maxWidth: "240px" } });
    const ext = el("input", { type: "text", value: "1d", style: { maxWidth: "120px" } });
    root.append(el("div", { class: "grid g2", style: { marginTop: "14px" } },
      el("div", { class: "card" }, el("h4", {}, "Change the end"), el("div", { class: "row" }, dt, btn("Set end", () => dt.value && rcon("hca cycle setend " + dt.value.slice(0, 16), { confirm: "Moves the end of cycle " + c.id + "." }))),
        el("div", { class: "row", style: { marginTop: "8px" } }, ext, btn("Extend", () => rcon("hca cycle extend " + ext.value, { confirm: "Extends the cycle." })), btn("Shorten", () => rcon("hca cycle extend -" + ext.value.replace(/^-/, ""), { confirm: "Shortens the cycle." }))),
        el("div", { class: "row", style: { marginTop: "8px" } }, ["10m", "30m", "1h"].map((t) => btn("End in " + t, () => { const d = new Date(Date.now() + ({ "10m": 600000, "30m": 1800000, "1h": 3600000 })[t]); const pad = (n) => String(n).padStart(2, "0"); rcon(`hca cycle setend ${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`, { confirm: "Sets the end " + t + " from now (in the server's zone, assuming it matches this computer)." }); }, "small")))),
      el("div", { class: "card" }, el("h4", {}, "Preview and end"), el("div", { class: "row" }, btn("Simulate the end", async () => { const r = await rcon("hca cycle simulate", { quiet: true }); if (r !== null) showReply("Simulated end of cycle", r); }, "primary"),
        btn("End now", () => rcon("hca cycle end confirm", { confirm: "Ends cycle " + c.id + " now: scores, outcomes and points are applied, vaults are emptied, and the next cycle starts." }), "danger")),
        el("div", { class: "row", style: { marginTop: "8px" } }, btn("Reset cycle (keep heads)", () => rcon("hca reset cycle keep-heads confirm", { confirm: "Starts cycle " + c.id + " over: vaults refilled, older heads go defunct." })),
          btn("Reset cycle (delete heads)", () => rcon("hca reset cycle delete-heads confirm", { confirm: "Starts cycle " + c.id + " over: vaults refilled, older heads are deleted where seen." }), "danger")),
        el("div", { class: "small muted", style: { marginTop: "8px" } }, "A hard reset needs typed codes; use the console for it."))));
  }
}
export function showReply(title, reply) {
  import("./ui.js").then(({ drawer }) => drawer(title, el("div", { class: "console", style: { height: "auto", minHeight: "200px" }, html: legacyToHtml(reply) })));
}

// ---------------------------------------------------------------- leaderboard and history
async function leaderboard(root) {
  if (needLive(root)) return;
  root.append(pageHead("Leaderboard", "Live ranks by unique heads, with the score and outcome each would get now."));
  const all = await S.api("/api/live/players");
  const act = all.filter((p) => p.active).map((p) => ({ ...p, score: liveScore(p, all) })).sort((a, b) => b.unique - a.unique || b.score - a.score);
  const t = el("table", { class: "t" }, el("tr", {}, el("th", {}, "#"), el("th", {}, "Player"), el("th", {}, "Unique"), el("th", {}, "Score"), el("th", {}, "Would get")));
  let rank = 0, last = null;
  act.forEach((p, i) => { if (p.unique !== last) { rank = i + 1; last = p.unique; } const outs = p.score === null ? [] : outcomesFor(p.score);
    t.append(el("tr", { class: "click", onclick: () => go("#/live-player/" + p.uuid) }, el("td", {}, el("b", {}, rank)), el("td", {}, p.name), el("td", {}, p.unique), el("td", {}, el("b", { style: { color: p.score > 0 ? "var(--good)" : p.score < 0 ? "var(--bad)" : "" } }, p.score)),
      el("td", {}, outs.length ? outs.map(([id, r]) => el("span", { class: "pill", style: { marginRight: "4px" } }, el("span", { html: (r.display || id).replace(/<[^>]*>/g, "") }))) : el("span", { class: "faint" }, "—")))); });
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, act.length ? t : el("div", { class: "empty" }, "No active players yet."))));
}
async function history(root, params) {
  if (needLive(root)) return;
  const cycles = await S.api("/api/live/history");
  if (params[0]) {
    const rows = await S.api("/api/live/cycle?id=" + params[0]);
    root.append(el("div", { class: "crumbs" }, el("a", { href: "#/live-history" }, "History"), " › Cycle " + params[0]), pageHead("Cycle " + params[0], ""));
    const t = el("table", { class: "t" }, el("tr", {}, ["Rank", "Player", "Unique", "Score", "Points after", "Streak", "Outcomes"].map((h) => el("th", {}, h))));
    for (const r of rows) t.append(el("tr", { class: "click", onclick: () => go("#/live-player/" + r.uuid) }, el("td", {}, r.rank ?? "—"), el("td", {}, r.name), el("td", {}, r.unique_heads), el("td", {}, r.score ?? "inactive"), el("td", {}, r.points_after), el("td", {}, r.streak), el("td", { class: "small" }, (r.outcomes || "").replace(/,/g, ", "))));
    root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)));
    return;
  }
  root.append(pageHead("History", "Every cycle so far."));
  const g = el("div", { class: "grid g3" });
  for (const c of cycles) g.append(el("div", { class: "card click", onclick: () => go("#/live-history/" + c.id) }, el("div", { class: "row" }, el("h3", { class: "grow" }, "Cycle " + c.id), badge(c.state, c.state === "active" ? "good" : "plain")),
    el("div", { class: "small muted" }, when(c.start_ms) + " → " + when(c.ended_ms || c.end_ms)),
    el("div", { style: { marginTop: "8px" } }, `${c.active} active · avg ${c.avg ?? "—"} · `, el("span", { style: { color: "var(--good)" } }, c.rewarded + " rewarded"), " · ", el("span", { style: { color: "var(--bad)" } }, c.punished + " punished")),
    c.top ? el("div", { class: "small", style: { marginTop: "4px" } }, "Top: " + c.top) : null));
  root.append(cycles.length ? g : el("div", { class: "empty" }, "No cycles yet."));
}

// ---------------------------------------------------------------- audit
function auditView(initial, head) {
  const box = el("div");
  const q = el("input", { type: "search", placeholder: "Player, head ID or any text…", value: initial || "" });
  const typeSel = el("select", {}, el("option", { value: "" }, "any type"), ["HEAD_CREATE", "HEAD_DELETE", "HEAD_PLACE", "HEAD_BREAK", "VAULT_IN", "VAULT_OUT", "VAULT_ADMIN", "TRADE_COMPLETE", "TRADE_CANCEL", "KILL_DROP", "CYCLE_START", "CYCLE_END", "SCORE", "OUTCOME", "POINTS", "SHOP_PURCHASE", "ACTION_APPLY", "ACTION_REVERT", "TITLE", "ADMIN", "DUPLICATE", "ITEM_DELIVERY"].map((t) => el("option", { value: t }, t)));
  const res = el("div", { class: "panel" }, el("div", { class: "pb" }));
  let page = 1;
  const run = async () => {
    res.firstChild.innerHTML = "";
    res.firstChild.append(el("div", { class: "muted small" }, "Searching…"));
    try {
      const r = await S.api(`/api/audit?q=${encodeURIComponent(q.value)}&type=${typeSel.value}&page=${page}`);
      res.firstChild.innerHTML = "";
      for (const a of r.lines) res.firstChild.append(auditRow(a));
      if (!r.lines.length) res.firstChild.append(el("div", { class: "empty" }, "No matching events."));
      res.firstChild.append(el("div", { class: "row", style: { marginTop: "10px" } }, page > 1 ? btn("← newer", () => { page--; run(); }, "small") : null, r.more ? btn("older →", () => { page++; run(); }, "small") : null));
    } catch (e) { res.firstChild.textContent = e.message; }
  };
  q.addEventListener("keydown", (e) => { if (e.key === "Enter") { page = 1; run(); } });
  typeSel.addEventListener("change", () => { page = 1; run(); });
  box.append(el("div", { class: "row", style: { marginBottom: "10px", flexWrap: "nowrap" } }, q, typeSel, btn("Search", () => { page = 1; run(); }, "primary")), res);
  run();
  return box;
}
async function audit(root, params) {
  if (params[0] === "head" && params[1]) {
    const id = params[1];
    root.append(el("div", { class: "crumbs" }, el("a", { href: "#/live-audit" }, "Audit log"), " › head"), pageHead("Head tracer", el("code", {}, id)));
    const r = await S.api("/api/audit/head?id=" + encodeURIComponent(id));
    const icons = { HEAD_CREATE: "✦", VAULT_IN: "⤓", VAULT_OUT: "⤒", TRADE_COMPLETE: "⇄", KILL_DROP: "⚔", HEAD_PLACE: "▣", HEAD_BREAK: "⛏", HEAD_DELETE: "✕", DUPLICATE: "⚠", ITEM_DELIVERY: "✉" };
    const list = el("ol", { class: "steps" });
    for (const a of r.lines) list.append(el("li", {}, el("div", { class: "row" }, el("b", {}, (icons[a.type] || "•") + " " + a.type.replace(/_/g, " ").toLowerCase()), el("span", { class: "small faint" }, (a.ts || "").replace("T", " ").slice(0, 19))),
      el("div", { class: "small muted" }, Object.entries(a.fields).filter(([k]) => k !== "head").map(([k, v]) => k + ": " + v.replace(/_/g, " ")).join(" · "))));
    root.append(r.lines.length ? el("div", { class: "card" }, el("div", { class: "muted small", style: { marginBottom: "10px" } }, "Everything the audit log knows about this head, oldest first."), list) : el("div", { class: "empty" }, "This head isn't in the audit log (it may be older than audit.keep-days)."));
    return;
  }
  root.append(pageHead("Audit log", "Every economy event, newest first. Search by player name, head ID or text; click “trace head” to follow one head's life."));
  root.append(auditView(params[0] ? decodeURIComponent(params[0]) : ""));
}

// ---------------------------------------------------------------- effects
async function effects(root) {
  if (needLive(root)) return;
  root.append(pageHead("Effects", "Every timed action running on the server: punishments, buffs, protections, bounties…"));
  const rows = await S.api("/api/live/effects");
  const t = el("table", { class: "t" }, el("tr", {}, ["Player", "Type", "From", "Left", "State", ""].map((h) => el("th", {}, h))));
  for (const e of rows) t.append(el("tr", {}, el("td", {}, el("a", { href: "#/live-player/" + e.uuid }, e.name)), el("td", {}, badge(e.type, "info")), el("td", { class: "small muted" }, e.source),
    el("td", {}, e.ends_ms ? formatDuration(e.ends_ms - Date.now()) : e.cycles_left ? e.cycles_left + " cycle end(s)" : e.duration_kind.toLowerCase()), el("td", { class: "small" }, e.state.toLowerCase().replace("_", " ")),
    el("td", {}, el("button", { class: "iconbtn", title: "Remove", onclick: () => rcon(`hca effects ${e.name} remove ${e.id.slice(0, 8)}`, { confirm: "Ends this effect now and undoes it." }) }, "✕"))));
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, rows.length ? t : el("div", { class: "empty" }, "No timed actions are running."))));
}

// ---------------------------------------------------------------- console
async function consolePage(root, params) {
  root.append(pageHead("Console", "Run server commands over RCON. Replies show with their colors. ↑ and ↓ go through your history."));
  if (!rconReady()) {
    root.append(el("div", { class: "note warn" }, "RCON isn't set up for this server. In server.properties set enable-rcon=true and a rcon.password (TestServer/setup.sh does this for the test server), restart the server, then reopen Studio."));
    return;
  }
  const out = el("div", { class: "console" });
  const inp = el("input", { type: "text", placeholder: "hca cycle info", list: "cmds", style: { fontFamily: "var(--mono)" } });
  const cmds = [...new Set((S.store.catalog?.commands || []).flatMap((c) => c.syntax).map((s) => s.replace(/^\//, "")))];
  let hist = [];
  try { hist = JSON.parse(localStorage.getItem("hc-console") || "[]"); } catch (e) { /* ignore */ }
  let hi = hist.length;
  const run = async (cmd) => {
    cmd = cmd.trim().replace(/^\//, "");
    if (!cmd) return;
    if (/^hca reset hard$|^stop$/.test(cmd) && !(await confirmDialog("Run /" + cmd + "?", cmd === "stop" ? "Stops the server." : "Starts a hard reset (it then asks for typed codes).", { ok: "Run", danger: true }))) return;
    hist.push(cmd); hist = hist.slice(-100); hi = hist.length;
    try { localStorage.setItem("hc-console", JSON.stringify(hist)); } catch (e) { /* ignore */ }
    out.append(el("div", { class: "cmd" }, "> /" + cmd));
    try { const r = await S.post("/api/rcon", { command: cmd }); out.append(el("div", { html: legacyToHtml(r.reply || "(no reply)") })); }
    catch (e) { out.append(el("div", { class: "err" }, e.message)); }
    out.scrollTop = out.scrollHeight;
  };
  inp.addEventListener("keydown", (e) => {
    if (e.key === "Enter") { run(inp.value); inp.value = ""; }
    if (e.key === "ArrowUp" && hi > 0) { inp.value = hist[--hi]; e.preventDefault(); }
    if (e.key === "ArrowDown") { hi = Math.min(hist.length, hi + 1); inp.value = hist[hi] || ""; }
  });
  const quick = ["hca reload", "hca selftest", "hca cycle info", "hca integrations", "hca cycle simulate", "list", "hca audit type:admin"];
  root.append(el("div", { class: "row", style: { marginBottom: "10px" } }, quick.map((q) => btn("/" + q, () => run(q), "small"))), out,
    el("div", { class: "row", style: { marginTop: "10px", flexWrap: "nowrap" } }, el("span", { class: "mono", style: { color: "var(--accent)" } }, "/"), inp, btn("Run", () => { run(inp.value); inp.value = ""; }, "primary")),
    el("datalist", { id: "cmds" }, cmds.map((c) => el("option", { value: c }))));
  if (params[0]) { inp.value = decodeURIComponent(params[0]); }
  setTimeout(() => inp.focus(), 30);
}

export const livePages = [
  { id: "live", group: "Live server", title: "Dashboard", icon: ICONS.live, render: dashboard },
  { id: "live-players", group: "Live server", title: "Players", icon: ICONS.players, render: players },
  { id: "live-player", group: "Live server", title: "Player", icon: ICONS.players, render: player, hidden: true },
  { id: "live-cycle", group: "Live server", title: "Cycle tools", icon: ICONS.cycle, render: cycle },
  { id: "live-top", group: "Live server", title: "Leaderboard", icon: ICONS.trophy, render: leaderboard },
  { id: "live-history", group: "Live server", title: "History", icon: ICONS.history, render: history },
  { id: "live-audit", group: "Live server", title: "Audit log", icon: ICONS.audit, render: audit },
  { id: "live-effects", group: "Live server", title: "Effects", icon: ICONS.effects, render: effects },
  { id: "console", group: "Live server", title: "Console", icon: ICONS.console, render: consolePage },
];
