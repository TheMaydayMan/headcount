// Learn, Reference and Tools pages, and the problems checker.
import { el, btn, badge, sectionTitle, mcText, miniInline, tooltip, itemTile, seg, switchCtl, drawer, closeDrawer, toast, confirmDialog, promptDialog, SAMPLE, ICONS, humanize, field } from "./ui.js";
import { renderMini, esc, isObj, readYaml, setValue, parseDoc, diffHunks, compileFormula, parseRange, parseDuration, expandSlots, stripMini, clone } from "./core.js";
import * as S from "./store.js";
import { pageHead, C, zoneInfo } from "./pages-setup.js";
import { types as actionTypes, listNames, normalize } from "./actions.js";
import { rcon, rconReady } from "./pages-live.js";
import { menuSlots } from "./pages-setup2.js";

const go = (h, key) => window.studio.go(h, key);
const link = (text, hash, key) => el("a", { href: hash, onclick: (e) => { e.preventDefault(); go(hash, key); } }, text);
const cat = () => S.store.catalog || {};

// ============================================================================================
//  Markdown (for the docs)
// ============================================================================================
function inline(s) {
  return esc(s).replace(/`([^`]+)`/g, "<code>$1</code>").replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (m, t, u) => /^https?:/.test(u) ? `<a href="${u}" target="_blank" rel="noopener">${t}</a>` : u.endsWith(".md") || u.includes(".md#") ? `<a href="#/docs/${u.split("/").pop().split("#")[0]}">${t}</a>` : t);
}
export function markdown(md) {
  const out = [];
  const lines = md.replace(/\r/g, "").split("\n");
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (l.startsWith("```")) { const buf = []; i++; while (i < lines.length && !lines[i].startsWith("```")) buf.push(lines[i++]); out.push(`<pre><code>${esc(buf.join("\n"))}</code></pre>`); continue; }
    const h = /^(#{1,4})\s+(.*)$/.exec(l);
    if (h) { const id = h[2].toLowerCase().replace(/[^a-z0-9 -]/g, "").replace(/ /g, "-"); out.push(`<h${h[1].length} id="${id}">${inline(h[2])}</h${h[1].length}>`); continue; }
    if (l.startsWith("|") && lines[i + 1] && /^\|[\s:|-]+\|$/.test(lines[i + 1].trim())) {
      const row = (r) => r.trim().replace(/^\||\|$/g, "").split(/(?<!\\)\|/).map((c) => c.trim().replace(/\\\|/g, "|"));
      let html = "<table><tr>" + row(l).map((c) => `<th>${inline(c)}</th>`).join("") + "</tr>";
      i += 2;
      while (i < lines.length && lines[i].startsWith("|")) html += "<tr>" + row(lines[i++]).map((c) => `<td>${inline(c)}</td>`).join("") + "</tr>";
      i--; out.push(html + "</table>"); continue;
    }
    if (/^\s*[-*] /.test(l)) { const buf = []; while (i < lines.length && /^\s*[-*] /.test(lines[i])) buf.push(`<li>${inline(lines[i++].replace(/^\s*[-*] (\[[ x]\] )?/, ""))}</li>`); i--; out.push("<ul>" + buf.join("") + "</ul>"); continue; }
    if (/^\d+\. /.test(l)) { const buf = []; while (i < lines.length && /^\d+\. /.test(lines[i])) buf.push(`<li>${inline(lines[i++].replace(/^\d+\. /, ""))}</li>`); i--; out.push("<ol>" + buf.join("") + "</ol>"); continue; }
    if (l.startsWith("> ")) { out.push(`<blockquote class="note">${inline(l.slice(2))}</blockquote>`); continue; }
    if (l.trim()) out.push(`<p>${inline(l)}</p>`);
  }
  return out.join("\n");
}

// ============================================================================================
//  Learn
// ============================================================================================
function howItWorks(root) {
  const vs = +C("vault.size") || 27, sh = +C("vault.starting-heads") || 27, days = +C("cycle.length-days") || 30;
  const formula = String(S.get("scoring.yml", "formula") ?? "unique - ceil(active / 4)");
  const steps = [
    ["A cycle starts", `Every player's vault fills with ${sh} copies of their own head. On this server a cycle lasts ${days} days and rolls over at ${C("cycle.end-time")} ${C("cycle.time-zone")}.`, "fill", "#/cycle"],
    ["Collect heads", "Heads of other players come from trading (/trade) and killing: a kill drops a fresh head of the victim, once per killer and victim per cycle. Only heads in the vault count.", "trade", "#/trading"],
    ["What counts", `Unique heads: how many different players' heads are in your vault (your own counts once). Heads are tagged with their cycle, so old ones can't be reused, and ${vs} slots is all the room there is.`, "unique", "#/vault"],
    ["The end", `Each active player's unique heads become a score: ${formula}. Positive scores earn points; negative ones bring outcomes like debuffs or a dunce cap.`, "score", "#/scoring"],
    ["The reset", "Vaults are emptied and refilled for the next cycle. Heads left outside vaults expire: they stay as normal heads but are worthless.", "reset", "#/outcomes"],
    ["Spend points", "Points earned are spent in /hc shop during the next cycle. Tiers unlock with last cycle's score: buffs, protection, titles, bigger vaults…", "shop", "#/shop"],
  ];
  let i = 0;
  root.append(pageHead("How Headcount works", "One cycle, step by step, using this server's settings."));
  const stage = el("div", { class: "card", style: { minHeight: "330px" } });
  const nav = el("div", { class: "row", style: { marginTop: "12px" } });
  const draw = () => {
    const [t, text, anim, hash] = steps[i];
    stage.innerHTML = "";
    const vault = el("div", { class: "anim-vault" });
    const n = Math.min(27, vs);
    for (let k = 0; k < n; k++) vault.append(el("div"));
    stage.append(el("div", { class: "row", style: { alignItems: "flex-start", gap: "28px" } },
      el("div", { class: "grow" }, el("div", { class: "faint small" }, `Step ${i + 1} of ${steps.length}`), el("h2", { style: { margin: "4px 0 10px" } }, t), el("p", { style: { fontSize: "15px", lineHeight: 1.6 } }, text), link("Where to change this →", hash)),
      el("div", {}, vault, el("div", { class: "small muted", style: { marginTop: "8px", textAlign: "center" } }, "a vault"))));
    const cells = [...vault.children];
    const anims = {
      fill: () => cells.forEach((c, k) => setTimeout(() => c.classList.add("on"), k * 40)),
      trade: () => { cells.forEach((c) => c.classList.add("on")); [3, 8, 14, 20].forEach((k, j) => setTimeout(() => { cells[k]?.classList.remove("on"); cells[k]?.classList.add("other"); }, 300 + j * 400)); },
      unique: () => { cells.forEach((c) => c.classList.add("on")); [3, 8, 14, 20].forEach((k) => cells[k]?.classList.add("other")); },
      score: () => { cells.forEach((c) => c.classList.add("on")); [3, 8, 14, 20].forEach((k) => cells[k]?.classList.add("other")); stage.append(el("div", { class: "mc", style: { marginTop: "16px", fontSize: "15px" }, html: renderMini("<gray>5 unique heads → score <green>" + Math.floor(compileFormula(formula).eval({ unique: 5, active: 12, total: 27, own: 23, rank: 3, percentile: 60 })) + "</green> with 12 active players") })); },
      reset: () => { cells.forEach((c) => c.classList.add("on", "other")); cells.forEach((c, k) => setTimeout(() => { c.classList.remove("other"); }, 500 + k * 30)); },
      shop: () => { stage.append(el("div", { class: "row", style: { marginTop: "16px" } }, ["SUGAR", "SHIELD", "CHEST", "NAME_TAG", "NETHER_STAR"].map((m) => itemTile(m, { size: 38 })))); },
    };
    anims[anim]?.();
    nav.innerHTML = "";
    nav.append(btn("← Back", () => { if (i > 0) { i--; draw(); } }), el("div", { class: "row gap-s grow", style: { justifyContent: "center" } }, steps.map((_, k) => el("span", { style: { width: "9px", height: "9px", borderRadius: "50%", background: k === i ? "var(--accent)" : "var(--line-2)", cursor: "pointer" }, onclick: () => { i = k; draw(); } }))),
      i < steps.length - 1 ? btn("Next →", () => { i++; draw(); }, "primary") : btn("Try the scoring lab →", () => go("#/scoring"), "primary"));
  };
  draw();
  root.append(stage, nav, sectionTitle("Read more"), el("div", { class: "row" }, ["heads.md", "formula.md", "outcomes.md", "shop.md", "statuses.md", "actions.md"].map((d) => btn(d.replace(".md", ""), () => go("#/docs/" + d), "small"))));
}

const GUIDES = [
  ["Make cycles a week long", [["Set the cycle length to 7 days.", "#/cycle", "config.yml#cycle.length-days"], ["Pick when it rolls over (e.g. Sunday 20:00 means a start date on a Sunday and an end time of 20:00).", "#/cycle", "config.yml#cycle.end-time"], ["Check the time zone shows the right clock.", "#/cycle", "config.yml#cycle.time-zone"], ["Save, then /hca reload. The running cycle keeps its end; the next one is a week.", "#/review"]]],
  ["Add a shop item", [["Open the shop and pick a tier.", "#/shop"], ["Click an empty slot and give the item an ID.", "#/shop"], ["Set its price, item and name in the drawer.", "#/shop"], ["Click “Edit what it does” and add actions (give-item, potion, points…).", "#/shop"], ["Save and /hca reload.", "#/review"]]],
  ["Make a punishment that locks PvP on", [["Open Outcomes and add a rule with a score range like ..-3.", "#/outcomes"], ["Edit its actions and add a pvp action: state on, lock on, duration cycle.", "#/outcomes"], ["Drag the score marker to check which scores get it.", "#/outcomes"]]],
  ["Let players trade while their PvP is on", [["Remove in-pvp from the trade status lists.", "#/trading", "trade.yml#statuses.block-request"], ["Also from the open-trade cancel list.", "#/trading", "trade.yml#statuses.cancel-open"], ["And from the vault rule if heads should move too.", "#/vault", "config.yml#vault.block-movement.statuses"]]],
  ["Show titles as LuckPerms prefixes", [["Set permissions to luckperms.", "#/integrations"], ["Turn on permission-prefix in the titles settings.", "#/titles", "titles.yml#settings.permission-prefix.enabled"], ["Save and /hca reload: online players get their prefix at once.", "#/review"]]],
  ["Use another combat plugin", [["Set pvp.provider to none.", "#/integrations"], ["Make a status with source placeholder that reads your plugin's combat placeholder.", "#/statuses"], ["Use that status in the vault and trade rules.", "#/trading"]]],
  ["Test changes safely", [["Edit what you want; nothing is written until Review & save.", "#/overview"], ["Check Problems.", "#/problems"], ["Review the diff and save. Each save keeps a backup.", "#/review"], ["Reload and run selftest from the console.", "#/console"], ["Restore any backup if needed.", "#/backups"]]],
  ["Prepare for a test night", [["Read test-plan.md in the repository root.", "#/docs/examples.md"], ["Shorten the cycle from Cycle tools.", "#/live-cycle"], ["Start fresh with a cycle reset.", "#/live-cycle"]]],
];
function guides(root, params) {
  root.append(pageHead("Guides", "Step-by-step walkthroughs. Each step opens the right page and points at the setting."));
  const g = el("div", { class: "grid g2" });
  GUIDES.forEach(([title, steps], gi) => g.append(el("div", { class: "card" }, el("h3", {}, title),
    el("ol", { class: "steps", style: { padding: 0, margin: "10px 0 0" } }, steps.map(([t, hash, key]) => el("li", {}, el("div", {}, t), el("a", { href: hash, onclick: (e) => { e.preventDefault(); go(hash, key); } , class: "small" }, "show me →")))))));
  root.append(g);
}

function recipes(root) {
  root.append(pageHead("Recipes", "Ready-made setups from the docs. Apply shows exactly what changes before anything happens; then review and save as usual."));
  for (const r of cat().recipes || []) {
    const plan = planRecipe(r);
    root.append(el("div", { class: "card", style: { marginBottom: "12px" } }, el("div", { class: "row" }, el("h3", { class: "grow" }, r.title),
      plan.ok ? btn("Apply…", () => applyRecipe(r, plan), "primary") : badge("copy by hand", "plain")),
      r.text ? el("p", { class: "muted" }, r.text.slice(0, 400)) : null,
      el("details", { class: "more" }, el("summary", {}, "The YAML"), el("pre", { class: "md", style: { margin: 0 } }, el("code", {}, r.yaml.join("\n"))))));
  }
}
function planRecipe(r) {
  const files = {};
  try {
    for (const block of r.yaml) {
      let file = null, buf = [];
      const flush = () => {
        if (!file || !buf.join("").trim()) return;
        const data = readYaml(buf.join("\n"));
        (files[file] ||= []).push(data);
        buf = [];
      };
      for (const line of block.split("\n")) {
        const m = /^#\s*([a-z_\/]+\.yml)/.exec(line.trim());
        if (m && !line.startsWith(" ")) { flush(); file = m[1]; continue; }
        if (line.trim().startsWith("#") && !line.startsWith(" ")) continue;
        buf.push(line.replace(/\s+#.*$/, (x) => (/["']/.test(x) ? x : "")));
      }
      flush();
    }
  } catch (e) { return { ok: false }; }
  const texts = {};
  for (const [file, datas] of Object.entries(files)) {
    if (!S.doc(file)) return { ok: false };
    let t = S.doc(file).text;
    for (const data of datas) {
      const leaves = [];
      const walk = (v, p) => { if (isObj(v) && Object.keys(v).length) for (const [k, x] of Object.entries(v)) walk(x, p ? p + "." + k : k); else leaves.push([p, v]); };
      walk(file === "actions.yml" && !("lists" in data) ? { lists: data } : data, "");
      for (const [p, v] of leaves) t = setValue(t, p, v);
    }
    texts[file] = t;
  }
  return { ok: Object.keys(texts).length > 0, texts };
}
function applyRecipe(r, plan) {
  const body = el("div");
  for (const [file, t] of Object.entries(plan.texts)) body.append(el("h3", {}, file), diffView(S.doc(file).text, t));
  drawer("Apply: " + r.title, body, { footer: el("div", { class: "row" }, btn("Cancel", () => closeDrawer()), btn("Apply these changes", () => { for (const [f, t] of Object.entries(plan.texts)) S.setText(f, t, null); closeDrawer(); toast("Applied. Review & save when ready."); }, "primary")) });
}

const GLOSSARY = [
  ["Cycle", "A round of the game, 30 days by default. It starts with full vaults and ends with scores, outcomes and a reset."],
  ["Run", "One run of a cycle. A /hca reset cycle starts the same cycle number over as a new run; heads from the old run stop counting."],
  ["Economy head", "A player head made by Headcount, tagged with its owner, cycle, run and a unique ID. Vanilla or /give heads never count."],
  ["Vault", "Each player's head storage (/hc). Only heads in the vault count toward the score."],
  ["Unique heads", "How many different players' heads are in a vault. Your own head counts once."],
  ["Expired head", "A head from an earlier cycle. It still works as a normal head but is worthless."],
  ["Defunct head", "A head from before a reset that kept the old heads. Worthless, like an expired one."],
  ["Active player", "Someone who played at least activity.min-playtime this cycle. Only active players get a score."],
  ["Score", "What a vault is worth at the end of a cycle, from the formula in scoring.yml."],
  ["Points", "Headcount's own currency. Positive scores earn them; they're spent in the shop during the next cycle."],
  ["Streak", "Consecutive positive (or negative) cycles. Usable in the formula and in conditions."],
  ["Outcome", "A rule in outcomes.yml: for a score range, run some actions (rewards or punishments)."],
  ["Shield", "A shop item: blocks the next punishment outcome."],
  ["Status", "A named player state (in-pvp, afk, low-health…) defined in statuses.yml, used by gates and conditions."],
  ["Gate", "A check that blocks something (moving heads, trading) while a player has certain statuses, each with a message and bypass permission."],
  ["Action", "One thing that happens: a message, a potion, points, a ban… 60 types, shared by hooks, outcomes and the shop."],
  ["Action list", "A list of actions. Named lists in actions.yml can be reused anywhere by name."],
  ["Duration", "How long a timed action lasts: a time, until the cycle ends, N cycles, until death, permanent or manual. Headcount undoes it when it ends."],
  ["Hook", "Actions that run at a moment: cycle start, warnings, cycle end, first join, every join…"],
  ["Tier", "A level of the shop, unlocked by last cycle's score."],
  ["Bounty", "A price on a player's head: whoever kills them gets points."],
  ["Included provider", "Headcount's own version of a job another plugin can do (PvP, permissions, moderation, AFK…). Chosen in integrations.yml."],
  ["Selftest", "/hca selftest: checks every configured item, menu, message, list and status. Runs quietly on every start and reload."],
  ["Audit log", "A log of every economy event (heads made, moved, traded, deleted…) in plugins/Headcount/audit, searchable with /hca audit or here."],
];
function glossary(root) {
  root.append(pageHead("Glossary", "The words Headcount uses."));
  const q = el("input", { type: "search", placeholder: "Search terms…" });
  const box = el("div", { class: "grid g2", style: { marginTop: "12px" } });
  const draw = () => { box.innerHTML = ""; for (const [t, d] of GLOSSARY) if (!q.value || (t + d).toLowerCase().includes(q.value.toLowerCase())) box.append(el("div", { class: "card" }, el("h3", {}, t), el("div", { class: "muted" }, d))); };
  q.addEventListener("input", draw); draw();
  root.append(q, box);
}

async function docs(root, params) {
  const list = cat().docs || [];
  const name = params[0] || "README";
  const split = el("div", { style: { display: "grid", gridTemplateColumns: "220px 1fr", gap: "20px", alignItems: "start" } });
  const nav = el("div", { class: "panel sticky", style: { padding: "6px" } }, list.map((d) => el("a", { class: "navitem" + (d.name === name ? " active" : ""), href: "#/docs/" + d.name }, d.title)));
  const body = el("div", { class: "card md" }, "Loading…");
  split.append(nav, body);
  root.append(split);
  if (!params[0]) { body.innerHTML = ""; body.append(el("h1", {}, "Documentation"), el("p", {}, "Every page of Headcount's docs. Pick one on the left."), el("div", { class: "grid g2" }, list.map((d) => el("div", { class: "card click", onclick: () => go("#/docs/" + d.name) }, el("b", {}, d.title), el("div", { class: "small muted" }, d.name))))); return; }
  try { const md = await S.api("/api/doc?name=" + encodeURIComponent(name)); body.innerHTML = markdown(md.text); }
  catch (e) { body.textContent = e.message; }
}

const GATES = [["vault-move", "Move a head in or out of the vault", "config.yml", "vault.block-movement.statuses"], ["trade-send", "Send a trade request", "trade.yml", "statuses.block-request"],
  ["trade-accept", "Accept a trade", "trade.yml", "statuses.block-accept"], ["trade-cancel", "Keep a trade open", "trade.yml", "statuses.cancel-open"]];
function why(root) {
  root.append(pageHead("Why is it blocked?", "Pick what the player tried, tick which statuses are true for them, and see exactly what stops them, what they're told, and how to let them through."));
  let gate = GATES[0];
  const truths = {};
  const body = el("div");
  const draw = () => {
    body.innerHTML = "";
    const list = (S.get(gate[2], gate[3]) ?? S.def(gate[2], gate[3]) ?? []).map(String);
    const names = [...new Set(list.map((x) => x.replace(/^!/, "")))];
    const box = el("div", { class: "card" }, el("h4", {}, "Their statuses"));
    for (const n of names) box.append(el("label", { class: "row", style: { padding: "4px 0" } }, switchCtl(!!truths[n], (c) => { truths[n] = c; draw(); }), el("b", {}, n), el("span", { class: "small muted" }, S.get("statuses.yml", "statuses." + n + ".source") ? "source: " + S.get("statuses.yml", "statuses." + n + ".source") : "not defined in statuses.yml")));
    if (!names.length) box.append(el("div", { class: "muted" }, "No statuses block this. It's never blocked by a status."));
    const hit = list.find((x) => (x.startsWith("!") ? !truths[x.slice(1)] : !!truths[x]));
    const res = el("div", { class: "card", style: { marginTop: "14px" } });
    if (!hit) res.append(el("div", { class: "note good" }, "Allowed: none of the blocking statuses are active."));
    else {
      const st = hit.replace(/^!/, "");
      const key = `gates.${gate[0]}.${hit.startsWith("!") ? "not-" + st : st}`;
      const msg = S.lang(key) ?? S.lang(`gates.${gate[0]}.default`) ?? "";
      res.append(el("div", { class: "note bad" }, "Blocked by ", el("b", {}, hit), hit.startsWith("!") ? " (it's NOT active)" : "", "."),
        el("h4", { style: { marginTop: "12px" } }, "They see"), el("div", { class: "mc", html: renderMini(msg, { vars: { ...SAMPLE, status: st }, prefix: S.lang("prefix") }) }), el("div", { class: "small faint" }, "lang: " + key),
        el("h4", { style: { marginTop: "12px" } }, "To let them through"), el("div", {}, "Give the permission ", el("code", {}, `headcount.bypass.${gate[0]}.${hit.startsWith("!") ? "not-" + st : st}`), " (or ", el("code", {}, `headcount.bypass.${gate[0]}.*`), "), or ", link("remove it from the list", gate[0].startsWith("vault") ? "#/vault" : "#/trading", gate[2] + "#" + gate[3]), "."));
    }
    body.append(el("div", { class: "split" }, box, res));
  };
  root.append(el("div", { style: { marginBottom: "14px" } }, seg(GATES.map((g) => [g[0], g[1]]), gate[0], (v) => { gate = GATES.find((g) => g[0] === v); draw(); })), body);
  draw();
}

// ============================================================================================
//  Reference
// ============================================================================================
function commands(root) {
  root.append(pageHead("Commands & permissions", "Every command, who can use it, and examples. Copy one, or run it on the server (RCON)."));
  const q = el("input", { type: "search", placeholder: "Search commands…" });
  const box = el("div");
  const perms = Object.fromEntries((cat().permissions || []).map((p) => [p.name, p]));
  const who = (p) => (perms[p]?.default === "true" ? badge("everyone", "good") : perms[p]?.default === "false" ? badge("nobody by default", "plain") : badge("ops", "warn"));
  const draw = () => {
    box.innerHTML = "";
    for (const group of ["player", "admin"]) {
      box.append(sectionTitle(group === "player" ? "Player commands" : "Admin commands"));
      const t = el("table", { class: "t" });
      for (const c of (cat().commands || []).filter((c) => c.group === group && (!q.value || (c.syntax.join(" ") + c.does + c.permissionText).toLowerCase().includes(q.value.toLowerCase())))) {
        t.append(el("tr", {}, el("td", { style: { width: "42%" } }, c.syntax.map((s) => el("div", {}, el("code", {}, s)))),
          el("td", {}, el("div", {}, c.does), el("div", { class: "row gap-s", style: { marginTop: "4px" } }, c.permission.map((p) => el("span", { class: "row gap-s" }, el("code", { class: "small faint" }, p), who(p))))),
          el("td", { style: { width: "110px" } }, el("button", { class: "iconbtn", title: "Copy", onclick: () => { navigator.clipboard?.writeText(c.syntax[0]); toast("Copied " + c.syntax[0]); } }, "⧉"),
            el("button", { class: "iconbtn", title: "Open in the console", onclick: () => go("#/console/" + encodeURIComponent(c.syntax[0].replace(/^\//, "").replace(/\s*[<\[].*$/, " "))) }, "›_"))));
      }
      box.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)));
    }
    box.append(sectionTitle("Bypass permissions"), el("div", { class: "panel" }, el("div", { class: "pb" }, el("table", { class: "t" }, (cat().bypass || []).map((b) => el("tr", {}, el("td", {}, b.permission.map((p) => el("div", {}, el("code", {}, p)))), el("td", {}, b.bypasses)))))));
    box.append(sectionTitle("Permission tree"), permTree(perms));
  };
  q.addEventListener("input", draw);
  root.append(q, builder(), box);
  draw();
}
function permTree(perms) {
  const root = el("div", { class: "panel" }, el("div", { class: "pb" }));
  const seen = new Set();
  const node = (name, depth) => {
    const p = perms[name];
    if (!p || seen.has(name + depth)) return null;
    seen.add(name + depth);
    const kids = (p.children || []).map((c) => node(c, depth + 1)).filter(Boolean);
    return el("div", { style: { marginLeft: depth * 18 + "px", padding: "3px 0" } }, el("code", {}, name), " ", el("span", { class: "small muted" }, p.description), " ", badge(p.default === "true" ? "everyone" : p.default === "false" ? "nobody" : "ops", p.default === "true" ? "good" : "plain"), kids);
  };
  root.firstChild.append(node("headcount.*", 0) || "", node("headcount.admin.reset.hard", 0) || "", node("headcount.pvp.override", 0) || "", node("headcount.bypass.*", 0) || "");
  return root;
}
function builder() {
  const forms = {
    "Give points": [["player", "Player", "Steve"], ["amount", "Amount", "10"], ["op", "give | take | set", "give"], ["next", "now | next cycle", "now"]],
    "Give heads": [["player", "To", "Steve"], ["amount", "Amount", "3"], ["owner", "Whose heads (self or a name)", "self"], ["where", "inventory | vault", "inventory"]],
    "Reset a player": [["player", "Player", "Steve"], ["what", "vault | points | titles | streak | effects | history | all", "vault"]],
    "Search the audit log": [["terms", "Player, head ID or text", "Steve"], ["type", "type (optional)", ""]],
  };
  const make = { "Give points": (v) => `/hca points ${v.player} ${v.op} ${v.amount}${v.next.startsWith("next") ? " next" : ""}`, "Give heads": (v) => `/hca giveheads ${v.player} ${v.amount} ${v.owner} current ${v.where}`,
    "Reset a player": (v) => `/hca reset ${v.player} ${v.what} confirm`, "Search the audit log": (v) => `/hca audit ${v.terms}${v.type ? " type:" + v.type : ""}` };
  let which = Object.keys(forms)[0];
  const box = el("div", { class: "card", style: { margin: "12px 0" } });
  const draw = () => {
    box.innerHTML = "";
    const vals = Object.fromEntries(forms[which].map(([k, , d]) => [k, d]));
    const out = el("code", { style: { fontSize: "14px" } });
    const upd = () => { out.textContent = make[which](vals); };
    box.append(el("h4", {}, "Command builder"), seg(Object.keys(forms), which, (w) => { which = w; draw(); }),
      el("div", { class: "grid g4", style: { marginTop: "10px" } }, forms[which].map(([k, label, d]) => el("label", {}, el("div", { class: "small muted" }, label), el("input", { type: "text", value: d, oninput: (e) => { vals[k] = e.target.value; upd(); } })))),
      el("div", { class: "row", style: { marginTop: "10px" } }, out, el("span", { class: "grow" }), btn("Copy", () => { navigator.clipboard?.writeText(out.textContent); toast("Copied"); }, "small"),
        btn("Run on the server", () => rcon(out.textContent, { confirm: "Runs this on the server." }), "small primary")));
    upd();
  };
  draw();
  return box;
}

function placeholders(root) {
  root.append(pageHead("Placeholders", "Use %headcount_<name>% in any Headcount text, and (with PlaceholderAPI) in other plugins."));
  const p = el("input", { type: "text", placeholder: "player name", style: { maxWidth: "200px" } });
  root.append(el("div", { class: "row", style: { marginBottom: "12px" } }, el("span", { class: "muted" }, "See real values:"), p, btn("Show for this player", async () => { const r = await rcon("hca placeholders " + p.value, { quiet: true }); if (r !== null) import("./pages-live.js").then((m) => m.showReply("Placeholders for " + p.value, r)); }, "small")));
  const t = el("table", { class: "t" }, el("tr", {}, el("th", {}, "Placeholder"), el("th", {}, "Value"), el("th", {})));
  for (const ph of cat().placeholders || []) t.append(el("tr", {}, el("td", {}, el("code", {}, `%headcount_${ph.name}%`)), el("td", {}, ph.description), el("td", {}, el("button", { class: "iconbtn", onclick: () => { navigator.clipboard?.writeText(`%headcount_${ph.name}%`); toast("Copied"); } }, "⧉"))));
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)));
}
function actionRef(root) {
  root.append(pageHead("Action types", "All 60 action types, grouped. Every one also takes the common options: duration, time, stacking, chance, delay, repeat, conditions, start and end messages."));
  const q = el("input", { type: "search", placeholder: "Search actions…" });
  const box = el("div");
  const draw = () => {
    box.innerHTML = "";
    const groups = {};
    for (const a of Object.values(actionTypes())) if (!q.value || (a.type + a.settingsText).toLowerCase().includes(q.value.toLowerCase())) (groups[a.group] ||= []).push(a);
    for (const [g, as] of Object.entries(groups)) {
      box.append(sectionTitle(g));
      box.append(el("div", { class: "grid g2" }, as.map((a) => el("div", { class: "card" }, el("div", { class: "row" }, el("h3", { class: "grow" }, a.type), a.online ? badge(a.online.startsWith("yes") ? "online only" : "works offline", "plain") : null),
        el("div", { class: "small muted" }, a.settingsText), a.undo && a.undo !== "-" ? el("div", { class: "small", style: { marginTop: "4px" } }, "Undo when timed: " + a.undo) : null,
        el("pre", { class: "md small", style: { margin: "8px 0 0" } }, el("code", {}, `- type: ${a.type}${a.main ? `\n  ${a.main}: …` : ""}`))))));
    }
  };
  q.addEventListener("input", draw); draw();
  root.append(q, box);
}
function statusRef(root) {
  root.append(pageHead("Status sources", "What a status can be built from (statuses.yml source:)."));
  const t = el("table", { class: "t" }, el("tr", {}, ["Source", "Settings", "True when"].map((h) => el("th", {}, h))));
  for (const s of cat().statuses || []) t.append(el("tr", {}, el("td", {}, el("code", {}, s.source)), el("td", { class: "small" }, s.settingsText), el("td", { class: "small muted" }, s.trueWhen)));
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)));
}
function formulaRef(root) {
  const f = cat().formula || {};
  root.append(pageHead("Formula", "Everything a score formula can use: variables, functions and ready-made ideas."), el("div", { class: "row", style: { marginBottom: "12px" } }, btn("Open the scoring lab", () => go("#/scoring"), "primary")));
  root.append(sectionTitle("Variables"), el("div", { class: "panel" }, el("div", { class: "pb" }, el("table", { class: "t" }, (f.variables || []).map((v) => el("tr", {}, el("td", {}, el("code", {}, v.name)), el("td", {}, v.description)))))),
    sectionTitle("Functions"), el("div", { class: "card" }, (f.functions || []).map((x) => el("code", { style: { marginRight: "10px" } }, x + "()"))),
    sectionTitle("Ideas"), el("div", { class: "panel" }, el("div", { class: "pb" }, el("table", { class: "t" }, (f.ideas || []).map((i) => el("tr", {}, el("td", {}, i.name), el("td", {}, el("code", {}, i.formula))))))));
}
const MM = [
  ["Colors", [["<red>", "<red>red"], ["<gold>", "<gold>gold"], ["<green>", "<green>green"], ["<aqua>", "<aqua>aqua"], ["<yellow>", "<yellow>yellow"], ["<gray>", "<gray>gray"], ["<dark_gray>", "<dark_gray>dark_gray"], ["<white>", "<white>white"], ["<#ff8800>", "<#ff8800>any hex color"]]],
  ["Gradients", [["<gradient:gold:yellow>…</gradient>", "<gradient:gold:yellow>[Legend]</gradient>"], ["<rainbow>…</rainbow>", "<rainbow>rainbow text</rainbow>"]]],
  ["Styles", [["<bold> <b>", "<bold>bold"], ["<italic> <i>", "<italic>italic"], ["<underlined> <u>", "<underlined>underlined"], ["<strikethrough> <st>", "<st>struck"], ["<!italic>", "turns italic off"], ["<reset>", "<red><b>red bold<reset> plain"]]],
  ["Layout", [["<newline>", "one<newline>two"], ["\\<", "a literal \\<"]]],
  ["Chat only", [["<click:run_command:'/hc shop'>…", "<click:run_command:'/hc shop'><yellow>[Shop]"], ["<hover:show_text:'Hi'>…", "a tooltip"], ["<insert:text>", "shift-click inserts text"]]],
  ["Headcount", [["<player> <cycle> …", "variables: each option lists its own"], ["<prefix>", "<prefix>the lang prefix"], ["%headcount_points%", "points: %headcount_points%"], ["<score:green:red:gray>", "vault title: <score:green:red:gray>"]]],
];
function miniRef(root) {
  root.append(pageHead("MiniMessage", "The text format every Headcount message uses. Tags stay on until closed (</red>) or <reset>."));
  const ta = el("textarea", { rows: 3, spellcheck: "false" });
  ta.value = "<prefix><gold><bold>Cycle <cycle></bold> <gray>ends in <white>%headcount_time_left%</white>. <gradient:aqua:light_purple>Good luck!</gradient>";
  const out = el("div", { class: "mc", style: { marginTop: "6px" } });
  const upd = () => (out.innerHTML = renderMini(ta.value, { vars: SAMPLE, prefix: S.lang("prefix") }));
  ta.addEventListener("input", upd); upd();
  root.append(el("div", { class: "card" }, el("h4", {}, "Try it"), ta, out));
  for (const [g, rows] of MM) root.append(sectionTitle(g), el("div", { class: "panel" }, el("div", { class: "pb" }, el("table", { class: "t" }, rows.map(([c, ex]) => el("tr", {}, el("td", { style: { width: "40%" } }, el("code", {}, c)), el("td", {}, el("div", { class: "mc", html: renderMini(ex, { vars: SAMPLE, prefix: S.lang("prefix") }) }))))))));
  root.append(el("p", { class: "muted" }, "Full documentation: ", el("a", { href: "https://docs.advntr.dev/minimessage/format", target: "_blank", rel: "noopener" }, "docs.advntr.dev/minimessage/format")));
}
function optionsRef(root) {
  root.append(pageHead("All options", "Every option in every file: its default, your value, and what it does. Click one to jump to it."));
  const q = el("input", { type: "search", placeholder: "Search options…" });
  const file = el("select", {}, el("option", { value: "" }, "all files"), [...new Set((cat().options || []).map((o) => o.file))].map((f) => el("option", { value: f }, f)));
  const box = el("div", { class: "panel" }, el("div", { class: "pb" }));
  const draw = () => {
    const t = el("table", { class: "t" }, el("tr", {}, ["Option", "Default", "Yours", "What it does"].map((h) => el("th", {}, h))));
    let n = 0;
    for (const o of cat().options || []) {
      if (file.value && o.file !== file.value) continue;
      if (q.value && !(o.path + " " + o.help).toLowerCase().includes(q.value.toLowerCase())) continue;
      if (++n > 300) break;
      const v = S.get(o.file, o.path);
      const vs = v === undefined ? "—" : typeof v === "object" ? "(section)" : String(v);
      t.append(el("tr", { class: "click", onclick: () => window.studio.goToOption(o.file, o.path) }, el("td", {}, el("code", {}, o.path), el("div", { class: "small faint" }, o.file)),
        el("td", { class: "small" }, el("code", {}, String(o.default).slice(0, 40))), el("td", { class: "small" }, vs === String(o.default).replace(/^"|"$/g, "") ? el("span", { class: "faint" }, "same") : el("code", {}, vs.slice(0, 40))), el("td", { class: "small muted" }, o.help.slice(0, 220))));
    }
    box.firstChild.innerHTML = "";
    box.firstChild.append(t, n > 300 ? el("div", { class: "muted small" }, "Showing 300; search to narrow.") : null);
  };
  q.addEventListener("input", draw); file.addEventListener("change", draw); draw();
  root.append(el("div", { class: "row", style: { marginBottom: "10px", flexWrap: "nowrap" } }, q, file), box);
}

// ============================================================================================
//  Problems
// ============================================================================================
const TRACKED_ONLY = ["head-protection", "extra-heads", "bounty", "shield", "deny", "force-title"];
const DURATION_KEYS = new Set(["min-playtime", "check-interval", "timeout", "countdown", "cooldown", "update-interval", "refresh-interval", "join-delay", "missed-warning-grace",
  "message-cooldown", "click-cooldown", "confirm-timeout", "before", "after", "duration", "killer-pickup-only"]);
export function runChecks() {
  const out = [];
  const add = (level, text, goHash, key) => out.push({ level, text, go: goHash, key });
  for (const n of S.store.names) if (S.doc(n).error) add("error", `${n} isn't valid YAML: ${S.doc(n).error}`, "#/files/" + n);
  const statuses = S.get("statuses.yml", "statuses") || {};
  const stNames = new Set(Object.keys(statuses));
  const checkStatus = (name, where, hash, key) => { const n = String(name).replace(/^!/, ""); if (n && !stNames.has(n) && !["always", "never"].includes(n)) add("error", `${where} uses the status "${n}", which isn't defined in statuses.yml.`, hash, key); };
  for (const s of S.get("config.yml", "vault.block-movement.statuses") || []) checkStatus(s, "The vault rule", "#/vault", "config.yml#vault.block-movement.statuses");
  for (const k of ["block-request", "block-accept", "cancel-open"]) for (const s of S.get("trade.yml", "statuses." + k) || []) checkStatus(s, "Trade " + k, "#/trading", "trade.yml#statuses." + k);
  const srcs = new Set((cat().statuses || []).map((x) => x.source));
  for (const [n, s] of Object.entries(statuses)) {
    if (!isObj(s)) continue;
    if (s.source && srcs.size && !srcs.has(s.source) && !["all-of", "any-of", "not", "always", "never"].includes(s.source)) add("error", `Status ${n} has an unknown source "${s.source}".`, "#/statuses");
    for (const x of [...(s.statuses || []), s.status, s.fallback].filter(Boolean)) checkStatus(x, "Status " + n, "#/statuses");
  }
  // Loops among combined statuses
  const deps = (n, seen = new Set()) => { if (seen.has(n)) return true; seen.add(n); const s = statuses[n]; if (!isObj(s)) return false; return [...(s.statuses || []), s.status].filter(Boolean).some((x) => deps(String(x).replace(/^!/, ""), new Set(seen))); };
  for (const n of stNames) if (deps(n)) { add("error", `Status ${n} refers back to itself through other statuses (a loop).`, "#/statuses"); break; }
  // Actions
  const T = actionTypes(), lists = new Set(listNames());
  const titles = new Set(Object.keys(S.get("titles.yml", "titles") || {}));
  const checkList = (v, where, hash) => {
    const arr = Array.isArray(v) ? v : v === undefined || v === null ? [] : [v];
    if (typeof v === "string" && !lists.has(v) && !T[v]) { add("error", `${where} runs the list "${v}", which isn't in actions.yml.`, hash); return; }
    for (const item of arr) {
      if (typeof item === "string" && !item.includes(":") && !lists.has(item) && !T[item]) add("error", `${where} runs "${item}", which is neither an action list nor an action type.`, hash);
      const c = normalize(item);
      if (c.type === "?" || (c.form !== "ref" && !T[c.type])) add("error", `${where} has an unknown action type "${c.type}".`, hash);
      if (TRACKED_ONLY.includes(c.type) && !c.data.duration) add("warn", `${where}: ${c.type} does nothing without a duration.`, hash);
      if (["grant-title", "force-title", "revoke-title"].includes(c.type) && c.data.title && !titles.has(String(c.data.title))) add("warn", `${where} uses the title "${c.data.title}", which isn't in titles.yml.`, hash);
      if (c.type === "run" && c.data.list && !lists.has(c.data.list)) add("error", `${where} runs the list "${c.data.list}", which isn't in actions.yml.`, hash);
    }
  };
  for (const [id, r] of Object.entries(S.get("outcomes.yml", "rules") || {})) { if (!isObj(r)) continue; checkList(r.actions, "Outcome " + id, "#/outcomes"); if (!parseRange(r.score).ok) add("error", `Outcome ${id} has a score range "${r.score}" that can't be read.`, "#/outcomes"); }
  for (const [n, v] of Object.entries(S.get("actions.yml", "lists") || {})) checkList(v, "Action list " + n, "#/hooks");
  const hooks = S.get("config.yml", "hooks") || {};
  for (const [h, v] of Object.entries(hooks.player || {})) checkList(v, "The " + h + " hook", "#/hooks");
  for (const h of ["cycle-start", "cycle-end"]) { checkList(hooks[h]?.global, h + " (global)", "#/hooks"); checkList(hooks[h]?.["per-player"]?.actions, h + " (per player)", "#/hooks"); }
  for (const [w, v] of Object.entries(hooks.warnings || {})) { if (isNaN(parseDuration(v?.before))) add("error", `Warning ${w} has no valid 'before'.`, "#/hooks"); checkList(v?.global, "Warning " + w, "#/hooks"); }
  // Shop
  const { size: shopSize, buttons } = menuSlots("shop.yml", "gui");
  for (const [t, T2] of Object.entries(S.get("shop.yml", "tiers") || {})) {
    const used = {};
    for (const [id, e] of Object.entries(T2?.entries || {})) {
      if (!isObj(e)) continue;
      checkList(e.actions, `Shop item ${t}.${id}`, "#/shop/" + t);
      const k = (e.page || 1) + ":" + (e.slot ?? 0);
      if (used[k]) add("error", `Shop items ${used[k]} and ${id} (tier ${t}) are on the same slot ${e.slot ?? 0}, page ${e.page || 1}.`, "#/shop/" + t);
      used[k] = id;
      if ((e.slot ?? 0) >= shopSize) add("error", `Shop item ${t}.${id} is on slot ${e.slot}, outside the ${shopSize}-slot menu.`, "#/shop/" + t);
      else if (buttons[e.slot ?? 0]) add("warn", `Shop item ${t}.${id} shares slot ${e.slot} with the ${buttons[e.slot].id} button.`, "#/shop/" + t);
      for (const ti of e.requires?.titles || []) if (!titles.has(String(ti))) add("warn", `Shop item ${t}.${id} requires the title "${ti}", which doesn't exist.`, "#/shop/" + t);
    }
  }
  // Integrations
  for (const k of ["pvp", "placeholders", "economy", "permissions", "moderation", "discord", "bedrock", "player-state"]) if ((S.get("integrations.yml", k + ".provider") ?? "") === "auto") add("warn", `integrations.yml ${k}.provider is "auto", which 1.1 replaced; pick one.`, "#/integrations");
  if (!zoneInfo(S.get("config.yml", "cycle.time-zone") ?? "UTC")) add("error", `The time zone "${S.get("config.yml", "cycle.time-zone")}" isn't one Headcount knows; it would use UTC.`, "#/cycle", "config.yml#cycle.time-zone");
  const f = compileFormula(String(S.get("scoring.yml", "formula") ?? ""));
  if (!f.ok) add("error", "The score formula has an error: " + f.error, "#/scoring");
  // Durations anywhere in config.yml / trade.yml / integrations.yml
  const walk = (file, v, p) => {
    if (isObj(v)) { for (const [k, x] of Object.entries(v)) walk(file, x, p ? p + "." + k : k); return; }
    const k = p.split(".").pop();
    if (p === "pvp.included.messages.countdown") return; // a display mode (actionbar | bossbar | none), not a duration
    if (DURATION_KEYS.has(k) && typeof v === "string" && !["cycle", "death", "permanent", "manual", "instant"].includes(v) && !v.startsWith("cycles:") && isNaN(parseDuration(v))) add("error", `${file} ${p}: "${v}" isn't a duration (30s, 10m, 2h, 3d…).`, null);
  };
  for (const file of ["config.yml", "trade.yml", "integrations.yml"]) walk(file, S.doc(file)?.data, "");
  // Menus
  for (const [file, base, name] of [["config.yml", "vault.gui", "vault"], ["config.yml", "history.gui", "history"], ["trade.yml", "gui", "trade"], ["shop.yml", "gui", "shop"], ["shop.yml", "confirm-gui", "shop confirmation"], ["titles.yml", "gui", "titles"]]) {
    const L = S.get(file, base);
    if (!isObj(L)) continue;
    const size = (Math.max(1, Math.min(6, +L.rows || 6))) * 9;
    for (const [id, b] of Object.entries(L.buttons || {})) if (isObj(b) && b.slot !== undefined && +b.slot >= size) add("error", `The ${name} menu's ${id} button is on slot ${b.slot}, outside its ${L.rows || 6} rows.`, "#/menus");
  }
  return out;
}
function problems(root) {
  const list = runChecks();
  root.append(pageHead("Problems", "Checked as you edit, before the server ever sees the files. /hca selftest on the server checks even more."));
  if (!list.length) root.append(el("div", { class: "note good" }, "No problems found. ", rconReady() ? el("a", { href: "#", onclick: (e) => { e.preventDefault(); rcon("hca selftest"); } }, "Run selftest on the server") : null));
  for (const p of list) root.append(el("div", { class: "note " + (p.level === "error" ? "bad" : "warn"), style: { margin: "8px 0", cursor: p.go ? "pointer" : "default" }, onclick: () => p.go && go(p.go, p.key) },
    el("div", { class: "row" }, badge(p.level === "error" ? "error" : "warning", p.level === "error" ? "bad" : "warn"), el("span", { class: "grow" }, p.text), p.go ? el("span", { class: "small" }, "go →") : null)));
}

// ============================================================================================
//  Review & save, backups, import and export
// ============================================================================================
export function diffView(a, b) {
  const { hunks, added, removed } = diffHunks(a, b, 3);
  const box = el("div", { class: "diff" });
  box.append(el("div", { class: "gap" }, `+${added} −${removed} lines`));
  hunks.forEach((h, i) => {
    if (i) box.append(el("div", { class: "gap" }, "…"));
    for (const d of h) box.append(el("div", { class: "dl " + (d.op === "+" ? "add" : d.op === "-" ? "del" : "") }, el("span", { class: "n" }, d.op === "+" ? "+" : d.op === "-" ? "−" : d.b), el("span", {}, d.text || " ")));
  });
  return box;
}
function review(root) {
  const dirty = S.dirtyNames();
  root.append(pageHead("Review & save", "Every change you've made, line by line. Nothing reaches the server until you save; each save keeps a backup.",
    dirty.length ? btn("Save all (" + dirty.length + ")", () => saveAll(dirty), "primary") : null));
  const errs = runChecks().filter((x) => x.level === "error");
  if (errs.length && dirty.length) root.append(el("div", { class: "note warn", style: { marginBottom: "12px" } }, `${errs.length} problem(s) found. You can still save; `, link("see problems", "#/problems"), "."));
  if (!dirty.length) { root.append(el("div", { class: "empty" }, "No unsaved changes.")); return; }
  for (const n of dirty) {
    const d = S.doc(n);
    root.append(el("div", { class: "panel", style: { marginBottom: "14px" } }, el("div", { class: "ph" }, el("h3", { class: "grow" }, n), d.error ? badge("not valid YAML", "bad") : null,
      btn("Revert", async () => { if (await confirmDialog("Revert " + n + "?", "Your unsaved changes to this file are thrown away (undo with Ctrl+Z).", { ok: "Revert", danger: true })) { S.revert(n); window.studio.refresh(); } }, "small"),
      btn("Save", () => saveAll([n]), "small primary")), el("div", { class: "pb" }, diffView(d.saved, d.text))));
  }
}
async function saveAll(names) {
  const done = [];
  for (const n of names) {
    try { const r = await S.save(n); done.push(n + (r.backup ? " (backup: " + r.backup + ")" : "")); }
    catch (e) {
      if (e.status === 409 && (await confirmDialog(n + " changed on disk", e.message + " Overwrite it with your version?", { ok: "Overwrite", danger: true }))) { await S.save(n, true); done.push(n); }
      else toast("Couldn't save " + n + ": " + e.message, "err");
    }
  }
  window.studio.refresh();
  if (!done.length) return;
  toast("Saved " + done.join(", "));
  if (rconReady() && (await confirmDialog("Reload the server?", "Run /hca reload so the server uses the new files, then /hca selftest.", { ok: "Reload" }))) {
    await rcon("hca reload", { quiet: true });
    const r = await rcon("hca selftest", { quiet: true });
    if (r !== null) import("./pages-live.js").then((m) => m.showReply("Reloaded · selftest", r));
  }
}
async function backups(root) {
  root.append(pageHead("Backups", "Every copy Studio and the hard resets have kept. Compare any with the current file, and restore it (as a change you review and save)."));
  let list = [];
  try { list = await S.api("/api/backups"); } catch (e) { root.append(el("div", { class: "note bad" }, e.message)); return; }
  if (!list.length) { root.append(el("div", { class: "empty" }, "No backups yet. Saving from Studio makes one each time.")); return; }
  const t = el("table", { class: "t" }, el("tr", {}, ["Backup", "File", "When", ""].map((h) => el("th", {}, h))));
  for (const b of list) t.append(el("tr", {}, el("td", {}, el("code", { class: "small" }, b.path)), el("td", {}, b.file ? badge(b.file, "plain") : el("span", { class: "faint" }, "—")), el("td", { class: "small muted" }, new Date(b.mtime * 1000).toLocaleString()),
    el("td", {}, b.file ? btn("Compare & restore", async () => {
      const r = await S.api("/api/backup?path=" + encodeURIComponent(b.path));
      drawer("Backup of " + b.file, el("div", {}, el("div", { class: "small muted", style: { marginBottom: "8px" } }, "Lines removed (−) are in your current file; added (+) are in the backup."), diffView(S.doc(b.file).text, r.text)),
        { footer: el("div", { class: "row" }, btn("Close", () => closeDrawer()), btn("Restore this version", () => { S.setText(b.file, r.text, null); closeDrawer(); toast("Restored as an unsaved change. Review & save to keep it."); }, "primary")) });
    }, "small") : null)));
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)));
}
function importExport(root) {
  root.append(pageHead("Import & export", "Take your whole setup somewhere else, or bring one in."));
  const file = el("input", { type: "file", accept: ".zip" });
  root.append(el("div", { class: "grid g2" },
    el("div", { class: "card" }, el("h3", {}, "Export"), el("p", { class: "muted" }, "Every config file as saved on disk, in a .zip."), el("a", { class: "btn primary", href: "/api/export", download: "headcount-config.zip" }, "Download .zip")),
    el("div", { class: "card" }, el("h3", {}, "Import"), el("p", { class: "muted" }, "Load a .zip of config files. They arrive as unsaved changes you review first."), file,
      btn("Import", async () => {
        if (!file.files[0]) return;
        try {
          const r = await fetch("/api/import", { method: "POST", headers: { "X-Studio": "1" }, body: file.files[0] }).then((x) => x.json());
          if (r.error) throw new Error(r.error);
          const names = Object.keys(r.files || {});
          for (const n of names) S.setText(n, r.files[n], null);
          toast(names.length ? "Imported " + names.join(", ") + ". Review & save to keep." : "No Headcount config files in that zip.");
          if (names.length) go("#/review");
        } catch (e) { toast("Import failed: " + e.message, "err"); }
      }, "primary")),
    el("div", { class: "card" }, el("h3", {}, "Compare with the defaults"), el("p", { class: "muted" }, "See everything you've changed from the bundled defaults, file by file."),
      el("select", { onchange: (e) => { const n = e.target.value; if (!n) return; drawer(n + " vs. the default", el("div", {}, el("div", { class: "small muted", style: { marginBottom: "8px" } }, "− the default, + yours"), diffView(S.doc(n).defaultText, S.doc(n).text))); } },
        el("option", { value: "" }, "Pick a file…"), S.store.names.map((n) => el("option", { value: n }, n))))));
}

export const learnPages = [
  { id: "learn", group: "Learn", title: "How it works", icon: ICONS.learn, render: howItWorks },
  { id: "guides", group: "Learn", title: "Guides", icon: ICONS.guide, render: guides },
  { id: "recipes", group: "Learn", title: "Recipes", icon: ICONS.recipe, render: recipes },
  { id: "why", group: "Learn", title: "Why is it blocked?", icon: ICONS.why, render: why },
  { id: "glossary", group: "Learn", title: "Glossary", icon: ICONS.glossary, render: glossary },
  { id: "docs", group: "Learn", title: "Docs", icon: ICONS.docs, render: docs },
  { id: "ref-commands", group: "Reference", title: "Commands", icon: ICONS.cmd, render: commands },
  { id: "ref-placeholders", group: "Reference", title: "Placeholders", icon: ICONS.ph, render: placeholders },
  { id: "ref-actions", group: "Reference", title: "Action types", icon: ICONS.action, render: actionRef },
  { id: "ref-statuses", group: "Reference", title: "Status sources", icon: ICONS.status, render: statusRef },
  { id: "ref-formula", group: "Reference", title: "Formula", icon: ICONS.formula, render: formulaRef },
  { id: "ref-mini", group: "Reference", title: "MiniMessage", icon: ICONS.mini, render: miniRef },
  { id: "ref-options", group: "Reference", title: "All options", icon: ICONS.options, render: optionsRef },
  { id: "problems", group: "Tools", title: "Problems", icon: ICONS.problems, render: problems },
  { id: "review", group: "Tools", title: "Review & save", icon: ICONS.review, render: review },
  { id: "backups", group: "Tools", title: "Backups", icon: ICONS.backups, render: backups },
  { id: "io", group: "Tools", title: "Import & export", icon: ICONS.io, render: importExport },
];
