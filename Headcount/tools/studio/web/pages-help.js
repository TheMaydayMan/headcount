// Help pages: how a cycle works (with the glossary), recipes, why something is blocked, the reference and the docs.
import { el, btn, icon, iconBtn, seg, select, switchCtl, numberCtl, miniCtl, drawer, closeDrawer, toast, chest, tooltip, sprite, mcText, emptyBox, copyText, SAMPLE, ICONS, humanize } from "./ui.js";
import { renderMini, esc, isObj, readYaml, setValue, compileFormula, roundScore, parseRange, describeRange, stripMini, formatDuration, parseDuration } from "./core.js";
import * as S from "./store.js";
import { pageHead, C, layout, preview, mcName, outcomesFor, num } from "./pages-setup.js";
import { types as actionTypes, ACTION_SPRITES, ACTION_DESC } from "./actions.js";
import { SRC_SPRITE, statusSummary } from "./pages-setup2.js";
import { runCommand, showReply, players as knownPlayers } from "./pages-live.js";
import { diffView } from "./pages-files.js";

const go = (h, key) => window.studio.go(h, key);
const cat = () => S.store.catalog || {};
const V = (text, hash, key) => el("a", { class: "v", href: hash, onclick: (e) => { e.preventDefault(); go(hash, key); } }, String(text));
/** Markdown-ish inline text from the catalog: `code` becomes code, the rest is escaped. */
const rich = (s) => el("span", { html: esc(String(s ?? "")).replace(/`([^`]+)`/g, "<code>$1</code>") });

// ============================================================================================
//  Markdown, for the docs
// ============================================================================================
function inline(s) {
  return esc(s).replace(/`([^`]+)`/g, "<code>$1</code>").replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>").replace(/(^|[^*])\*([^*\s][^*]*)\*/g, "$1<i>$2</i>")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (m, t, u) => /^https?:/.test(u) ? `<a href="${u}" target="_blank" rel="noopener">${t}</a>` : /\.md(#|$)/.test(u) ? `<a href="#/docs/${u.split("/").pop().split("#")[0]}">${t}</a>` : u.startsWith("#") ? `<a href="javascript:void 0" data-anchor="${u.slice(1)}">${t}</a>` : t);
}
const slug = (t) => t.toLowerCase().replace(/<[^>]+>/g, "").replace(/[^a-z0-9 -]/g, "").replace(/ /g, "-");
export function markdown(md) {
  const out = [];
  const lines = md.replace(/\r/g, "").split("\n");
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (l.startsWith("```")) { const buf = []; i++; while (i < lines.length && !lines[i].startsWith("```")) buf.push(lines[i++]); out.push(`<pre><code>${esc(buf.join("\n"))}</code></pre>`); continue; }
    const h = /^(#{1,4})\s+(.*)$/.exec(l);
    if (h) { out.push(`<h${h[1].length} id="${slug(h[2])}">${inline(h[2])}</h${h[1].length}>`); continue; }
    if (l.startsWith("|") && lines[i + 1] && /^\|[\s:|-]+\|$/.test(lines[i + 1].trim())) {
      const row = (r) => r.trim().replace(/^\||\|$/g, "").split(/(?<!\\)\|/).map((c) => c.trim().replace(/\\\|/g, "|"));
      let html = "<table><tr>" + row(l).map((c) => `<th>${inline(c)}</th>`).join("") + "</tr>";
      i += 2;
      while (i < lines.length && lines[i].startsWith("|")) html += "<tr>" + row(lines[i++]).map((c) => `<td>${inline(c)}</td>`).join("") + "</tr>";
      i--; out.push(html + "</table>"); continue;
    }
    const listRe = /^(\s*)([-*]|\d+\.) /;
    if (listRe.test(l)) {
      const buf = [];
      const ordered = /^\s*\d+\. /.test(l);
      while (i < lines.length && (listRe.test(lines[i]) || (/^\s{2,}\S/.test(lines[i]) && buf.length))) {
        const m = listRe.exec(lines[i]);
        if (m) buf.push({ depth: m[1].length, text: lines[i].replace(listRe, "").replace(/^\[[ x]\] /, "") });
        else buf[buf.length - 1].text += " " + lines[i].trim();
        i++;
      }
      i--;
      let html = "", depth = -1;
      const stack = [];
      for (const it of buf) {
        if (it.depth > depth) { const tag = stack.length ? "ul" : ordered ? "ol" : "ul"; html += `<${tag}>`; stack.push(tag); depth = it.depth; }
        else { while (it.depth < depth && stack.length > 1) { html += `</li></${stack.pop()}>`; depth -= 2; } html += "</li>"; }
        html += `<li>${inline(it.text)}`;
      }
      while (stack.length) html += `</li></${stack.pop()}>`;
      out.push(html);
      continue;
    }
    if (l.startsWith("> ")) { out.push(`<blockquote class="note">${inline(l.slice(2))}</blockquote>`); continue; }
    if (l.trim()) {
      let p = l;
      while (i + 1 < lines.length && lines[i + 1].trim() && !/^(#|```|\||>|\s*([-*]|\d+\.) )/.test(lines[i + 1])) p += " " + lines[++i].trim();
      out.push(`<p>${inline(p)}</p>`);
    }
  }
  return out.join("\n");
}

// ============================================================================================
//  How it works, and the glossary
// ============================================================================================
const OTHERS = ["Alex", "Notch", "jeb_", "Dinnerbone", "Grumm"];
function howItWorks(root) {
  const size = +C("vault.size") || 27, start = Math.min(size, num(C("vault.starting-heads"), 18)), days = +C("cycle.length-days") || 14;
  const formula = String(S.get("scoring.yml", "formula") ?? S.def("scoring.yml", "formula"));
  const f = compileFormula(formula);
  const scoreOf = (u, a) => { if (!f.ok) return "?"; let s = roundScore(f.eval({ unique: u, active: a, total: start, own: Math.max(0, start - u), rank: 3, percentile: 50, players: a + 4, kills: 0, deaths: 0, trades: 0, streak: 0, points: 0, bonus: 0, vault_size: size, starting_heads: start }), S.get("scoring.yml", "rounding") ?? "floor"); return isFinite(s) ? s : "?"; };
  const rows = Math.min(6, Math.ceil(Math.min(size, 54) / 9));
  const head = (who) => ({ item: { material: "PLAYER_HEAD", head: who === "Steve" ? undefined : who, glint: C("heads.item.glint") }, tip: () => tooltip(C("heads.item.name"), C("heads.item.lore"), { owner: who, cycle: 3 }) });
  const vault = (fill, vars) => chest(rows, C("vault.gui.title"), (i) => (i >= Math.min(size, 54) ? { item: C("vault.gui.locked-slot.material") || "BLACK_STAINED_GLASS_PANE", cls: "dim" } : fill(i)), { vars: { owner: "Steve", size, ...vars } });
  const minPlay = parseDuration(String(C("activity.min-playtime") ?? "2h"));
  const rules = Object.entries(S.get("outcomes.yml", "rules") || {}).filter(([, r]) => isObj(r) && r.enabled !== false && parseRange(r.score).ok).sort((a, b) => parseRange(a[1].score).min - parseRange(b[1].score).min);
  const tiers = Object.entries(S.get("shop.yml", "tiers") || {}).filter(([, t]) => isObj(t));
  const traded = [3, 7, 11, 12, 16, 20];
  const step = (title, text, ...vis) => el("li", {}, el("div", {}, el("h2", {}, title), el("p", {}, text)), el("div", { class: "vis" }, vis));

  root.append(pageHead("How it works"));
  root.append(el("ol", { class: "walk" },
    step("A cycle starts and every vault is full",
      ["Cycles on this server last ", V(days + (+days === 1 ? " day" : " days"), "#/cycle", "config.yml#cycle.length-days"), " and roll over at ", V(C("cycle.end-time") + " " + C("cycle.time-zone"), "#/cycle", "config.yml#cycle.end-time"),
        ". At the start, each player's vault (", el("code", {}, "/hc"), ") gets ", V(start + " copies of their own head", "#/vault", "config.yml#vault.starting-heads"), ". It has ", V(size + " slots", "#/vault", "config.yml#vault.size"), "."],
      vault((i) => (i < start ? head("Steve") : null), { unique: 1, total: start, used: start, score: 0 })),
    step("Heads change hands",
      ["Players trade heads with ", el("code", {}, "/trade"), C("kills.enabled") !== false ? [", and a kill drops the victim's head", C("kills.once-per-victim-per-cycle") !== false ? ", once per victim each cycle" : ""] : "", ". Only heads inside a vault count, and every head is stamped with its cycle, so an old one is worth nothing."],
      vault((i) => (i >= start ? null : traded.includes(i) ? head(OTHERS[traded.indexOf(i) % OTHERS.length]) : head("Steve")), { unique: 6, total: start, used: start, score: scoreOf(6, 12) })),
    step("At the end, unique heads become a score",
      ["What counts is how many different players' heads are in the vault, your own included once. Only players who played ", V(minPlay ? "at least " + formatDuration(minPlay) : "at all", "#/cycle", "config.yml#activity.min-playtime"), " get a score. This server's ", V("formula", "#/scoring"), " is ", el("code", {}, formula), "."],
      f.ok ? el("div", { class: "sheet", style: { width: "100%" } }, el("table", { class: "t" }, el("tr", {}, el("th", {}, "Unique heads"), el("th", { class: "n" }, "Score, with 12 active players")),
        [1, 3, 5, 8, 12].map((u) => { const s = scoreOf(u, 12); return el("tr", {}, el("td", {}, u), el("td", { class: "n" }, el("b", { class: s > 0 ? "good-ink" : s < 0 ? "bad-ink" : "" }, s))); }))) : el("div", { class: "note bad" }, "The formula has an error, so there's no score to show.")),
    step("Scores bring outcomes and points",
      ["Each ", V("outcome", "#/outcomes"), " covers a range of scores and runs its actions on everyone in it. A score above 0 also earns ", V((+C("points.earn-multiplier") || 1) === 1 ? "a point per score point" : C("points.earn-multiplier") + " points per score point", "#/scoring", "config.yml#points.earn-multiplier"), ", and ", num(C("points.rollover-percent"), 50) > 0 ? [V(num(C("points.rollover-percent"), 50) + "% of unspent points", "#/scoring", "config.yml#points.rollover-percent"), " carry over."] : ["unspent points ", V("don't carry over", "#/scoring", "config.yml#points.rollover-percent"), "."]],
      rules.length ? el("div", { class: "items", style: { width: "100%" } }, rules.slice(0, 7).map(([id, r]) => el("div", { class: "item", onclick: () => go("#/outcomes/" + id) }, el("div", { class: "main" }, mcName(r.display || id)), el("div", { class: "end" }, describeRange(r.score)))))
        : emptyBox("totem_of_undying", "No outcomes yet", null)),
    step("Vaults empty out for the next cycle",
      ["Every vault is cleared and refilled. Heads left in chests, inventories or on the ground stay, but they're expired: worth nothing, and the vault won't take them."],
      el("div", { class: "row", style: { alignItems: "flex-start", flexWrap: "nowrap" } }, sprite({ material: "PLAYER_HEAD", head: "Alex" }, 44), tooltip(C("heads.item.expired-name"), C("heads.item.expired-lore"), { owner: "Alex", cycle: 2 }))),
    step("Points buy things in the shop",
      ["During the next cycle, points are spent in ", el("code", {}, "/hc shop"), ". The ", V(tiers.length + " tiers", "#/shop"), " open by last cycle's score."],
      el("div", { style: { display: "grid", gap: "8px", width: "100%" } }, tiers.slice(0, 4).map(([t, T]) => el("a", { href: "#/shop/" + t, class: "row", style: { textDecoration: "none", color: "inherit", flexWrap: "nowrap" } },
        el("span", { style: { minWidth: "110px" } }, mcName(T.name || t)), el("span", { class: "row gap-s", style: { flexWrap: "nowrap", overflow: "hidden" } }, Object.values(T.entries || {}).slice(0, 6).map((e) => sprite(e?.display || "PAPER", 26)))))))));
  root.append(glossarySection());
}

export const GLOSSARY = [
  ["Cycle", "One round of the game. It starts with full vaults and ends with scores, outcomes and empty vaults."],
  ["Run", "One go at a cycle. Starting a cycle over with /hca reset cycle keeps its number but begins a new run, and heads from the old run stop counting."],
  ["Economy head", "A player head made by Headcount and stamped with its owner, cycle, run and its own ID. Heads from /give or other plugins never count."],
  ["Vault", "Each player's head storage, opened with /hc. Only heads in it count toward the score."],
  ["Unique heads", "How many different players' heads are in a vault. Your own counts once."],
  ["Expired head", "A head from an earlier cycle. It's still a head, but it's worth nothing and the vault refuses it."],
  ["Defunct head", "A head from before a cycle was started over. Worthless, like an expired head."],
  ["Active player", "Someone who has played enough this cycle to get a score. How much is enough is set on the Cycle page."],
  ["Score", "What a vault is worth when the cycle ends, worked out by the formula in scoring.yml."],
  ["Points", "Headcount's own money. Scores above 0 earn them, and they're spent in the shop."],
  ["Streak", "How many cycles in a row a player scored above 0, or below it. The formula and conditions can use it."],
  ["Outcome", "A rule in outcomes.yml that runs actions for a range of scores, as a reward or a punishment."],
  ["Shield", "A shop item that blocks the next punishment."],
  ["Status", "A named state a player can be in, like in-pvp or afk, defined in statuses.yml. Statuses decide when heads can't move or trades are refused."],
  ["Gate", "Something a status can block: moving heads, opening the vault, sending or accepting a trade. Each gate has its own message and bypass permission."],
  ["Action", "One thing that happens to a player or the server: a message, a potion effect, points, a ban. Hooks, outcomes and the shop all run actions."],
  ["Action list", "A named list of actions in actions.yml that any hook, outcome or shop item can run by name."],
  ["Duration", "How long a timed action lasts. When it ends, Headcount undoes it."],
  ["Hook", "Actions that run at a moment: a cycle starting, a warning before the end, a player's first join."],
  ["Tier", "A level of the shop. Each opens at a score from the last cycle."],
  ["Bounty", "Points on a player's head. Whoever kills them collects."],
  ["Provider", "Who does a job such as PvP, permissions or bans: Headcount itself, another plugin, or nobody. Chosen under Integrations."],
  ["Selftest", "/hca selftest checks every item, menu, message, list and status. It also runs quietly on every start and reload."],
  ["Audit log", "A record of every head made, moved, traded or deleted, and of points, purchases and admin commands. Kept in plugins/Headcount/audit."],
];
function glossarySection() {
  const q = el("input", { type: "search", placeholder: "Find a word", "aria-label": "Find a word" });
  const box = el("dl", { class: "terms" });
  const draw = () => {
    box.innerHTML = "";
    const s = q.value.trim().toLowerCase();
    const hits = GLOSSARY.filter(([t, d]) => !s || (t + " " + d).toLowerCase().includes(s));
    for (const [t, d] of hits) box.append(el("dt", { id: "term-" + slug(t) }, t), el("dd", {}, d));
    if (!hits.length) box.append(el("dd", { style: { gridColumn: "1 / -1", padding: "14px 18px" } }, "No word like that here. The docs may have it."));
  };
  q.addEventListener("input", draw);
  draw();
  return el("section", { class: "group", id: "glossary", style: { marginTop: "36px" } }, el("header", {}, el("h2", {}, "Words Headcount uses"), el("div", { class: "side" }, q)), el("div", { class: "sheet" }, box));
}

// ============================================================================================
//  Recipes: ready-made setups to apply, and walkthroughs that point at the settings
// ============================================================================================
const WALKS = [
  ["Add a shop item", "emerald", [["Open the shop and pick the tier it belongs in.", "#/shop"], ["Click an empty slot in the chest and give the item an ID."], ["Set its price, then the item and name players see."],
    ["Under What buying it does, add the actions it runs, like a potion effect or an item."], ["Save, then reload Headcount."]]],
  ["Warn players before a cycle ends", "bell", [["Open Hooks and add a warning, with how long before the end it goes off.", "#/hooks"], ["Add a message, a title or a sound to it."], ["The warnings show on the timeline under Cycle.", "#/cycle"]]],
  ["Give everyone something when a cycle starts", "chest", [["Open Hooks and click Starts.", "#/hooks"], ["Switch to For each player, so every player gets their own copy."], ["Add a Give item action. Players who are offline get it when they next join."]]],
  ["Let players trade while their PvP is on", "bundle", [["Take in-pvp out of No requests while.", "#/trading", "trade.yml#statuses.block-request"], ["And out of No accepting while.", "#/trading", "trade.yml#statuses.block-accept"],
    ["And out of Cancel open trades when.", "#/trading", "trade.yml#statuses.cancel-open"], ["If heads should move between vaults too, take it out of Heads stay put while.", "#/vault", "config.yml#vault.block-movement.statuses"]]],
  ["Try a change and take it back", "feather", [["Change what you like. Problems shows anything that won't work.", "#/problems"], ["Review and save shows each changed line before it's written."], ["Reload Headcount and run selftest from This cycle.", "#/live"],
    ["If it's wrong, Backups has the file as it was before the save.", "#/backups"]]],
  ["Run a short test cycle", "clock", [["On This cycle, use End soon to end the cycle in 10 minutes.", "#/live"], ["Simulate it first to see who gets what."], ["To begin again with full vaults, use Start it over."]]],
];
const RECIPE_SPRITE = { "config.yml": "clock", "outcomes.yml": "totem_of_undying", "scoring.yml": "experience_bottle", "integrations.yml": "comparator", "titles.yml": "name_tag", "shop.yml": "emerald", "statuses.yml": "redstone_torch", "actions.yml": "firework_rocket", "trade.yml": "bundle" };
function recipesPage(root, params) {
  root.append(pageHead("Recipes"));
  const list = cat().recipes || [];
  const files = (r) => [...new Set(r.yaml.flatMap((b) => [...b.matchAll(/^#\s*([a-z_\/]+\.yml)/gm)].map((m) => m[1])))];
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Ready to apply")), el("div", { class: "items" }, list.map((r, i) => {
    const fs = files(r);
    return el("div", { class: "item", onclick: () => openRecipe(i) }, sprite(RECIPE_SPRITE[fs[0]] || "knowledge_book", 28), el("div", { class: "main" }, el("div", { class: "t1" }, r.title)), icon("right"));
  }))));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Step by step")), el("div", { class: "items" }, WALKS.map(([t, spr], i) => el("div", { class: "item", onclick: () => openWalk(i) }, sprite(spr, 28), el("div", { class: "main" }, el("div", { class: "t1" }, t)), icon("right"))))));
  if (params[0] !== undefined && list[+params[0]]) openRecipe(+params[0]);
}
function openWalk(i) {
  const [title, , steps] = WALKS[i];
  drawer(title, el("ol", { class: "steps", style: { margin: 0 } }, steps.map(([t, hash, key]) => el("li", {}, el("div", {}, t), hash ? el("a", { href: hash, class: "small", onclick: (e) => { e.preventDefault(); go(hash, key); } }, "Show me") : null))));
}
export function planRecipe(r) {
  const files = {};
  try {
    for (const block of r.yaml) {
      let file = null, buf = [];
      const flush = () => { if (file && buf.join("").trim()) (files[file] ||= []).push(readYaml(buf.join("\n"))); buf = []; };
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
export function openRecipe(i) {
  const r = (cat().recipes || [])[i];
  if (!r) return;
  const plan = planRecipe(r);
  const changed = plan.ok ? Object.entries(plan.texts).filter(([f, t]) => t !== S.doc(f).text) : [];
  const yaml = r.yaml.join("\n");
  const body = el("div", {}, r.text ? el("p", {}, rich(r.text)) : null);
  if (plan.ok && changed.length) for (const [file, t] of changed) body.append(el("h3", { style: { margin: "14px 0 8px", fontSize: "15px", fontFamily: "var(--mono)", fontWeight: 500 } }, file), diffView(S.doc(file).text, t));
  else if (plan.ok) body.append(emptyBox("lime_dye", "Already set up this way", "Your files already have every line this recipe would write."));
  else body.append(el("p", { class: "muted" }, "Studio can't merge this one into your files for you. Copy it into the file it names."), el("pre", { class: "diff", style: { padding: "10px 12px", whiteSpace: "pre" } }, yaml));
  if (plan.ok) body.append(el("details", { class: "more" }, el("summary", {}, "The recipe as written in the docs"), el("pre", { class: "diff", style: { padding: "10px 12px", whiteSpace: "pre" } }, yaml)));
  drawer(r.title, body, { wide: true, hash: "#/recipes/" + i, footer: el("div", { class: "row", style: { width: "100%" } }, el("span", { class: "grow" }), btn("Cancel", () => closeDrawer(), "ghost"),
    plan.ok ? btn("Apply", () => { S.setTexts(Object.fromEntries(changed)); closeDrawer(); toast("Applied. It isn't saved yet.", "", { label: "Review and save", run: () => window.studio.review() }); window.studio.refresh(); }, "primary") : btn("Copy it", () => copyText(yaml, "the recipe"), "primary")) });
}

// ============================================================================================
//  Why is it blocked?
// ============================================================================================
const GATES = [
  ["vault-move", "Move a head in or out of their vault", "config.yml", "vault.block-movement.statuses", "#/vault"],
  ["vault-open", "Open their vault", "config.yml", "vault.block-movement.statuses", "#/vault"],
  ["trade-send", "Send a trade request", "trade.yml", "statuses.block-request", "#/trading"],
  ["trade-accept", "Accept a trade", "trade.yml", "statuses.block-accept", "#/trading"],
  ["trade-cancel", "Keep a trade open", "trade.yml", "statuses.cancel-open", "#/trading"],
];
function whyPage(root) {
  root.append(pageHead("Why is it blocked?"));
  let gate = GATES[0];
  const truths = {};
  const left = el("div"), right = el("div");
  const draw = () => {
    left.innerHTML = ""; right.innerHTML = "";
    const [gid, , file, path, hash] = gate;
    const opening = gid === "vault-open";
    const lockOn = (S.get("config.yml", "vault.block-movement.block-opening") ?? S.def("config.yml", "vault.block-movement.block-opening")) === true;
    const list = opening && !lockOn ? [] : (S.get(file, path) ?? S.def(file, path) ?? []).map(String);
    const names = [...new Set(list.map((x) => x.replace(/^!/, "")))];
    const st = el("div", { class: "sheet" });
    for (const n of names) {
      const neg = list.includes("!" + n);
      st.append(el("div", { class: "field inline" }, el("div", { class: "lab" }, el("label", { class: "name" }, n), el("div", { class: "hint" }, neg ? "Blocks while it's off" : S.get("statuses.yml", "statuses." + n) ? statusSummary(S.get("statuses.yml", "statuses." + n)) : "Not in statuses.yml")),
        el("div", { class: "ctl" }, switchCtl(!!truths[n], (c) => { truths[n] = c; draw(); }, n))));
    }
    left.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "The player tries to")), select(GATES.map((g) => [g[0], g[1]]), gid, (v) => { gate = GATES.find((g) => g[0] === v); draw(); })),
      el("section", { class: "group" }, el("header", {}, el("h2", {}, "Which of these are true for them?")),
        names.length ? st : emptyBox("lime_dye", opening && !lockOn ? "Opening the vault is never blocked" : "No status blocks this", opening && !lockOn ? "Statuses only lock the vault when the Vault page is set that way." : null,
          btn("Change it", () => go(hash, (opening ? "config.yml#vault.block-movement.block-opening" : file + "#" + path)), "ghost"))));
    const hit = list.find((x) => (x.startsWith("!") ? !truths[x.slice(1)] : !!truths[x]));
    const res = el("div", { class: "sheet result" });
    if (!hit) res.append(el("div", { class: "verdict" }, sprite("lime_dye", 24), "Nothing stops them"), el("div", { class: "muted" }, names.length ? "Switch on a status to see what happens." : ""));
    else {
      const sname = hit.replace(/^!/, "");
      const key = hit.startsWith("!") ? "not-" + sname : sname;
      const msg = S.lang(`gates.${gid}.${key}`) ?? S.lang(`gates.${gid}.default`) ?? "";
      res.append(el("div", { class: "verdict" }, sprite("barrier", 24), "Blocked by ", el("code", {}, hit.startsWith("!") ? "not " + sname : sname)),
        el("div", { class: "mcscene", style: { minHeight: "0", padding: "12px" } }, msg ? el("div", { class: "mc chatline", html: renderMini(msg, { vars: { ...SAMPLE, status: sname, target: "Alex" }, prefix: S.lang("prefix") }) }) : el("span", { class: "faint" }, "They see no message.")),
        el("div", {}, el("div", { class: "small muted", style: { marginBottom: "6px" } }, "To let them through anyway, give them"),
          el("div", { class: "cmdline" }, el("span", {}, `headcount.bypass.${gid}.${key}`), iconBtn("copy", "Copy", () => copyText(`headcount.bypass.${gid}.${key}`), "sm"))),
        el("div", { class: "row" }, btn("Edit the message", () => go("#/messages/gates", "lang/en_US.yml#gates." + gid + (S.lang(`gates.${gid}.${key}`) !== undefined ? "." + key : ".default")), "ghost small"),
          btn("Change what blocks it", () => go(hash, file + "#" + path), "ghost small")));
    }
    right.append(preview("What happens", res));
  };
  draw();
  root.append(layout(left, right));
}

// ============================================================================================
//  Reference
// ============================================================================================
const TABS = [["commands", "Commands"], ["permissions", "Permissions"], ["placeholders", "Placeholders"], ["actions", "Actions"], ["statuses", "Status sources"], ["formula", "Formula"], ["text", "Text format"], ["settings", "All settings"]];
function referencePage(root, params) {
  const tab = TABS.find((t) => t[0] === params[0])?.[0] || "commands";
  root.append(pageHead("Reference"), el("div", { class: "tabs reftabs", role: "tablist" }, TABS.map(([k, n]) => el("button", { role: "tab", "aria-selected": String(k === tab), class: k === tab ? "on" : "", onclick: () => go("#/reference/" + k) }, n))),
    el("div", { class: "docpick" }, select(TABS, tab, (v) => go("#/reference/" + v))));
  ({ commands: refCommands, permissions: refPermissions, placeholders: refPlaceholders, actions: refActions, statuses: refStatuses, formula: refFormula, text: refText, settings: refSettings })[tab](root);
}
const searchBox = (ph, onInput) => { const q = el("input", { type: "search", placeholder: ph, "aria-label": ph }); q.addEventListener("input", onInput); return q; };
const permsBy = () => Object.fromEntries((cat().permissions || []).map((p) => [p.name, p]));

function refCommands(root) {
  const perms = permsBy();
  const body = el("div");
  const q = searchBox("Find a command", () => draw());
  root.append(el("div", { class: "toolbar" }, q), body);
  const consoleCmd = (s) => s.replace(/^\//, "").replace(/\s*[<\[].*$/, " ").replace(/\s(give\|take\|set|vault\|points\S*)\s.*$/, " ");
  const draw = () => {
    body.innerHTML = "";
    const s = q.value.trim().toLowerCase();
    for (const [g, title, usual] of [["player", "For players", "true"], ["admin", "For admins", "op"]]) {
      const cmds = (cat().commands || []).filter((c) => c.group === g && (!s || (c.syntax.join(" ") + " " + c.does + " " + c.permissionText).toLowerCase().includes(s)));
      const seen = new Set();
      const t = el("table", { class: "t stack-s" });
      for (const c of cmds) {
        if (seen.has(c.syntax.join())) continue;
        seen.add(c.syntax.join());
        const odd = c.permission.filter((p) => perms[p] && perms[p].default !== usual);
        t.append(el("tr", {}, el("td", { style: { width: "36%" } }, c.syntax.map((x) => el("div", { class: "cmd" }, x))),
          el("td", {}, el("div", {}, rich(c.does)), el("div", { class: "small faint", style: { marginTop: "2px" } }, c.permission.map((p) => el("code", { style: { marginRight: "8px" } }, p)),
            odd.map((p) => el("span", { class: "badge warn" }, perms[p].default === "false" ? "Nobody has it by default" : perms[p].default === "true" ? "Everyone has it" : "Ops only")))),
          el("td", { class: "n", style: { width: "84px" } }, iconBtn("copy", "Copy", () => copyText(c.syntax[0]), "sm"), g === "admin" ? iconBtn("terminal", "Open in the console", () => go("#/console/" + encodeURIComponent(consoleCmd(c.syntax[0]))), "sm") : null)));
      }
      if (cmds.length) body.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, title), el("div", { class: "side faint small" }, usual === "true" ? "Everyone has these by default" : "Ops have these by default")), el("div", { class: "sheet" }, t)));
    }
    if (!body.children.length) body.append(el("div", { class: "empty" }, "No command matches."));
    if (!s) body.prepend(builder());
  };
  draw();
}
function builder() {
  const names = knownPlayers().map((p) => p.name);
  const pl = { v: names[0] || "Steve" };
  const playerCtl = () => (names.length ? select(names, pl.v, (v) => { pl.v = v; upd(); }) : (() => { const i = el("input", { type: "text", value: pl.v, spellcheck: "false", style: { maxWidth: "200px" }, "aria-label": "Player" }); i.addEventListener("input", () => { pl.v = i.value; upd(); }); return i; })());
  const st = { op: "give", amount: 10, next: false, heads: 3, owner: "self", where: "inventory", what: "vault", terms: "", type: "" };
  const FORMS = {
    points: ["Points", () => [playerCtl(), seg([["give", "Give"], ["take", "Take"], ["set", "Set to"]], st.op, (v) => { st.op = v; upd(); }), numberCtl(st.amount, (v) => { st.amount = v; upd(); }, { min: 0, step: 1, unit: "points" }),
      el("label", { class: "switchrow" }, switchCtl(st.next, (v) => { st.next = v; upd(); }, "Next cycle"), "Next cycle")], () => `/hca points ${pl.v} ${st.op} ${st.amount}${st.next ? " next" : ""}`],
    heads: ["Heads", () => [playerCtl(), numberCtl(st.heads, (v) => { st.heads = v; upd(); }, { min: 1, step: 1, unit: "heads" }), select([["self", "Their own"], ...names.map((n) => [n, n + "'s"])], st.owner, (v) => { st.owner = v; upd(); }),
      seg([["inventory", "To inventory"], ["vault", "To vault"]], st.where, (v) => { st.where = v; upd(); })], () => `/hca giveheads ${pl.v} ${st.heads} ${st.owner} current ${st.where}`],
    reset: ["Reset a player", () => [playerCtl(), select([["vault", "Vault"], ["points", "Points"], ["titles", "Titles"], ["streak", "Streak"], ["effects", "Effects"], ["history", "History"], ["all", "All of it"]], st.what, (v) => { st.what = v; upd(); })],
      () => `/hca reset ${pl.v} ${st.what} confirm`],
    audit: ["Audit search", () => { const i = el("input", { type: "text", value: st.terms, placeholder: "A name, head ID or word", spellcheck: "false", style: { maxWidth: "260px" } }); i.addEventListener("input", () => { st.terms = i.value; upd(); });
      return [i, select([["", "Any kind"], "head-create", "vault-in", "vault-out", "trade-complete", "kill-drop", "shop-purchase", "points", "admin"].map((x) => (Array.isArray(x) ? x : [x, humanize(x)])), st.type, (v) => { st.type = v; upd(); })]; },
      () => `/hca audit ${st.terms || "Steve"}${st.type ? " type:" + st.type : ""}`],
  };
  let which = "points";
  const out = el("span");
  const ctls = el("div", { class: "row", style: { padding: "12px 18px" } });
  const upd = () => { out.textContent = FORMS[which][2](); };
  const drawForm = () => { ctls.innerHTML = ""; ctls.append(...FORMS[which][1]()); upd(); };
  const run = btn("Run", () => runCommand(out.textContent, { confirm: "It runs as the console.", danger: which === "reset" }), "small primary");
  drawForm();
  return el("section", { class: "group" }, el("header", {}, el("h2", {}, "Build a command"), el("div", { class: "side" }, seg(Object.entries(FORMS).map(([k, f]) => [k, f[0]]), which, (v) => { which = v; drawForm(); }))),
    el("div", { class: "sheet" }, ctls, el("div", { style: { padding: "0 18px 14px" } }, el("div", { class: "cmdline" }, out, iconBtn("copy", "Copy", () => copyText(out.textContent), "sm"), run))));
}
function refPermissions(root) {
  const perms = permsBy();
  const seen = new Set();
  const who = (p) => (p.default === "true" ? "Everyone" : p.default === "false" ? "Nobody" : "Ops");
  const node = (name, depth) => {
    const p = perms[name];
    if (!p || seen.has(name)) return [];
    seen.add(name);
    return [el("tr", {}, el("td", {}, el("code", { style: { marginLeft: depth * 18 + "px", display: "inline-block" } }, name)), el("td", { class: "muted" }, p.description), el("td", { class: "faint" }, who(p))),
      ...(p.children || []).flatMap((c) => node(c, depth + 1))];
  };
  const rows = ["headcount.*", ...Object.keys(perms)].flatMap((n) => node(n, 0));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Permissions")), el("div", { class: "sheet" }, el("table", { class: "t stack-s" }, el("tr", {}, el("th", {}, "Node"), el("th", {}, "Gives"), el("th", {}, "By default")), rows))),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Bypasses")), el("div", { class: "sheet" }, el("table", { class: "t stack-s" }, (cat().bypass || []).map((b) => el("tr", {}, el("td", { style: { width: "46%" } }, b.permission.map((p) => el("div", {}, el("code", {}, p)))), el("td", { class: "muted" }, rich(b.bypasses))))))));
}
function refPlaceholders(root) {
  const names = knownPlayers().map((p) => p.name);
  let who = names[0] || "";
  const pick = names.length ? select(names, who, (v) => { who = v; }) : el("input", { type: "text", placeholder: "Player name", spellcheck: "false", style: { maxWidth: "200px" }, oninput: (e) => { who = e.target.value; } });
  const show = btn("Show their values", async () => { if (!who) return; const r = await runCommand("hca placeholders " + who, { quiet: true }); if (r !== null) showReply("Placeholders for " + who, r); }, "ghost");
  const body = el("div");
  const q = searchBox("Find a placeholder", () => draw());
  root.append(el("div", { class: "toolbar" }, q, el("span", { class: "grow" }), pick, show), body);
  const draw = () => {
    body.innerHTML = "";
    const s = q.value.trim().toLowerCase();
    const list = (cat().placeholders || []).filter((p) => !s || (p.name + " " + p.description).toLowerCase().includes(s));
    body.append(list.length ? el("div", { class: "sheet" }, el("table", { class: "t stack-s" }, list.map((ph) => el("tr", {}, el("td", { style: { width: "40%" } }, el("code", {}, `%headcount_${ph.name}%`)), el("td", { class: "muted" }, rich(ph.description)),
      el("td", { class: "n" }, iconBtn("copy", "Copy", () => copyText(`%headcount_${ph.name}%`), "sm")))))) : el("div", { class: "empty" }, "No placeholder matches."),
      el("p", { class: "muted small", style: { marginTop: "12px" } }, "Inside Headcount's own messages, write ", el("code", {}, "%headcount_points%"), ". Other plugins can use them when PlaceholderAPI is the placeholder provider."));
  };
  draw();
}
function refActions(root) {
  const body = el("div");
  const q = searchBox("Find an action", () => draw());
  root.append(el("div", { class: "toolbar" }, q, el("span", { class: "grow" }), el("span", { class: "faint small" }, "Any action can also be timed, delayed, repeated, left to chance or given conditions.")), body);
  const draw = () => {
    body.innerHTML = "";
    const s = q.value.trim().toLowerCase();
    const groups = {};
    for (const a of Object.values(actionTypes())) if (!s || (a.type + " " + (ACTION_DESC[a.type] || "") + " " + a.settingsText).toLowerCase().includes(s)) (groups[a.group] ||= []).push(a);
    for (const [g, as] of Object.entries(groups)) body.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, g)), el("div", { class: "items" }, as.map((a) => el("div", { class: "item static", style: { alignItems: "flex-start" } },
      sprite(ACTION_SPRITES[a.type] || "paper", 28), el("div", { class: "main" }, el("div", { class: "t1" }, ACTION_DESC[a.type] || humanize(a.type), el("code", { class: "faint", style: { marginLeft: "8px" } }, a.type)),
        el("div", { class: "t2 wrap" }, rich(a.settingsText)), a.undo && a.undo !== "-" ? el("div", { class: "t2 wrap" }, "When its time is up: ", rich(a.undo)) : null),
      el("div", { class: "end hide-s" }, a.online ? (String(a.online).startsWith("yes") ? "Waits until they're online" : "Works while they're offline") : ""))))));
    if (!body.children.length) body.append(el("div", { class: "empty" }, "No action matches."));
  };
  draw();
}
function refStatuses(root) {
  const groups = {};
  for (const s of cat().statuses || []) (groups[s.group] ||= []).push(s);
  for (const [g, ss] of Object.entries(groups)) root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, g)), el("div", { class: "items" }, ss.map((s) => el("div", { class: "item static", style: { alignItems: "flex-start" } },
    sprite(SRC_SPRITE[s.source] || "redstone_torch", 28), el("div", { class: "main" }, el("div", { class: "t1" }, humanize(s.source), el("code", { class: "faint", style: { marginLeft: "8px" } }, s.source)),
      el("div", { class: "t2 wrap", style: { color: "var(--ink-2)" } }, rich(s.trueWhen)), s.settingsText ? el("div", { class: "t2 wrap" }, rich(s.settingsText)) : null))))));
}
function refFormula(root) {
  const f = cat().formula || {};
  const vars = el("div", { class: "sheet" }, el("table", { class: "t stack-s" }, (f.variables || []).map((v) => el("tr", {}, el("td", { style: { width: "28%" } }, el("code", {}, v.name)), el("td", { class: "muted" }, rich(v.description))))));
  const ops = el("p", { class: "muted", style: { marginTop: "12px" } }, "Functions: ", (f.functions || []).map((x, i) => [i ? ", " : "", el("code", {}, x + "()")]), ". Operators: ", el("code", {}, "+ - * / % ^"),
    ", comparisons, ", el("code", {}, "&& || !"), " and ", el("code", {}, "a ? b : c"), ".");
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "What a formula can use"), el("div", { class: "side" }, btn("Try one in Scoring", () => go("#/scoring"), "ghost small"))), vars, ops));
  const ideas = el("div", { class: "sheet" }, el("table", { class: "t stack-s" }, (f.ideas || []).map((i) => el("tr", {}, el("td", { style: { width: "34%" } }, i.name), el("td", {}, el("code", {}, i.formula)),
    el("td", { class: "n" }, iconBtn("copy", "Copy", () => copyText(i.formula, "the formula"), "sm"))))));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Ideas")), ideas));
}
const MM = [
  ["Colors", [["<red>", "<red>red"], ["<gold>", "<gold>gold"], ["<green>", "<green>green"], ["<aqua>", "<aqua>aqua"], ["<yellow>", "<yellow>yellow"], ["<gray>", "<gray>gray"], ["<dark_gray>", "<dark_gray>dark gray"], ["<white>", "<white>white"], ["<#ff8800>", "<#ff8800>any hex color"]]],
  ["Gradients", [["<gradient:gold:yellow>[Legend]</gradient>", "<gradient:gold:yellow>[Legend]</gradient>"], ["<rainbow>rainbow text</rainbow>", "<rainbow>rainbow text</rainbow>"]]],
  ["Styles", [["<bold> or <b>", "<bold>bold"], ["<italic> or <i>", "<italic>italic"], ["<underlined> or <u>", "<underlined>underlined"], ["<strikethrough> or <st>", "<st>struck"], ["<!italic>", "<!italic>italic off, useful in item lore"], ["<reset>", "<red><b>red bold<reset> back to plain"]]],
  ["Lines", [["<newline>", "one<newline>two"], ["\\<", "a literal \\<"]]],
  ["In chat only", [["<click:run_command:'/hc shop'>[Shop]</click>", "<click:run_command:'/hc shop'><yellow>[Shop]"], ["<hover:show_text:'Hi'>hover me</hover>", "<hover:show_text:'Hi'>hover me"]]],
  ["From Headcount", [["<prefix>", "<prefix>the prefix from the language file"], ["<player>, <cycle>, <score>", "<player> in cycle <cycle> scored <score>"], ["%headcount_points%", "%headcount_points% points"], ["<score:green:red:gray>", "vault title score: <score:green:red:gray>"]]],
];
function refText(root) {
  const demo = "<prefix><gold><bold>Cycle <cycle></bold> <gray>ends in <white>%headcount_time_left%</white>. <gradient:aqua:light_purple>Good luck!</gradient>";
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Try it")), el("div", { class: "sheet", style: { padding: "14px 18px" } }, miniCtl(demo, () => {}, { multiline: true }))));
  for (const [g, rows] of MM) root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, g)), el("div", { class: "sheet" }, el("table", { class: "t stack-s" }, rows.map(([c, ex]) => el("tr", {}, el("td", { style: { width: "44%" } }, el("code", {}, c)),
    el("td", {}, el("span", { class: "mcbox" }, el("span", { class: "mcline", html: renderMini(ex, { vars: SAMPLE, prefix: S.lang("prefix") }) })))))))));
  root.append(el("p", { class: "muted" }, "Headcount writes text in MiniMessage. Tags stay on until they're closed, like ", el("code", {}, "</red>"), ", or until ", el("code", {}, "<reset>"), ". The full format: ",
    el("a", { href: "https://docs.advntr.dev/minimessage/format", target: "_blank", rel: "noopener" }, "docs.advntr.dev/minimessage/format"), "."));
}
function refSettings(root) {
  const q = searchBox("Find a setting", () => draw());
  const file = select([["", "Every file"], ...[...new Set((cat().options || []).map((o) => o.file))].map((f) => [f, f])], "", () => draw());
  const body = el("div");
  root.append(el("div", { class: "toolbar" }, q, file), body);
  const show = (v) => (v === undefined ? "" : isObj(v) ? "" : Array.isArray(v) ? v.join(", ") : String(v));
  const draw = () => {
    body.innerHTML = "";
    const s = q.value.trim().toLowerCase();
    const t = el("table", { class: "t stack-s" }, el("tr", {}, el("th", {}, "Setting"), el("th", { class: "hide-s" }, "Default"), el("th", {}, "Yours"), el("th", { class: "hide-s" }, "What it does")));
    let n = 0;
    for (const o of cat().options || []) {
      if (file.value && o.file !== file.value) continue;
      if (o.default === "(section)") continue;
      if (s && !(o.path + " " + o.help + " " + o.file).toLowerCase().includes(s)) continue;
      if (++n > 250) continue;
      const v = show(S.get(o.file, o.path)), d = String(o.default).replace(/^"|"$/g, "");
      const same = v === d || v === "";
      t.append(el("tr", { class: "click", onclick: () => window.studio.goToOption(o.file, o.path) }, el("td", {}, el("code", {}, o.path), el("div", { class: "small faint" }, o.file)),
        el("td", { class: "small hide-s" }, el("code", {}, d.slice(0, 40))), el("td", { class: "small" }, same ? el("span", { class: "faint" }, "Default") : el("code", { style: { color: "var(--changed)" } }, v.slice(0, 40))),
        el("td", { class: "small muted hide-s" }, o.help.split(/(?<=\.)\s/)[0].slice(0, 160))));
    }
    body.append(n ? el("div", { class: "sheet" }, t) : el("div", { class: "empty" }, "No setting matches."), n > 250 ? el("p", { class: "muted small", style: { marginTop: "10px" } }, "The first 250 of " + n + " are shown. Search to find the rest.") : null);
  };
  draw();
}

// ============================================================================================
//  Docs
// ============================================================================================
async function docsPage(root, params) {
  const list = [{ name: "README", title: "Headcount" }, ...(cat().docs || [])];
  const name = params[0] || "README";
  const nav = el("nav", { class: "msgnav", "aria-label": "Docs" }, list.map((d) => el("button", { class: d.name === name ? "on" : "", onclick: () => go("#/docs/" + d.name) }, d.title)));
  const pick = el("div", { class: "docpick" }, select(list.map((d) => [d.name, d.title]), name, (v) => go("#/docs/" + v)));
  const body = el("article", { class: "md" }, el("p", { class: "muted" }, "Opening the page"));
  root.append(pageHead(list.find((d) => d.name === name)?.title || "Docs"), pick, el("div", { class: "docs" }, nav, body));
  try {
    const md = await S.api("/api/doc?name=" + encodeURIComponent(name));
    body.innerHTML = markdown(md.text.replace(/^#\s+.*\n/, ""));
    body.addEventListener("click", (e) => { const a = e.target.closest("[data-anchor]"); if (a) { e.preventDefault(); body.querySelector("#" + CSS.escape(a.dataset.anchor))?.scrollIntoView({ behavior: "smooth" }); } });
  } catch (e) { body.innerHTML = ""; body.append(emptyBox("bookshelf", "This page isn't there", e.message)); }
}

export const helpPages = [
  { id: "learn", group: "Help", title: "How it works", icon: ICONS.learn, render: howItWorks },
  { id: "recipes", group: "Help", title: "Recipes", icon: ICONS.recipe, render: recipesPage },
  { id: "why", group: "Help", title: "Why is it blocked?", icon: ICONS.why, render: whyPage },
  { id: "reference", group: "Help", title: "Reference", icon: ICONS.reference, render: referencePage },
  { id: "docs", group: "Help", title: "Docs", icon: ICONS.docs, render: docsPage },
];
export const helpAliases = { guides: "recipes", glossary: "learn", "ref-commands": "reference/commands", "ref-placeholders": "reference/placeholders", "ref-actions": "reference/actions",
  "ref-statuses": "reference/statuses", "ref-formula": "reference/formula", "ref-mini": "reference/text", "ref-options": "reference/settings" };
export const REF_TABS = TABS;
export const WALK_TITLES = WALKS.map((w) => w[0]);
export { openWalk };
