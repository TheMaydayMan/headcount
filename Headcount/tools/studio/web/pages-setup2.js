// Set up pages, part 2: Shop, Titles, Trading, Statuses, Integrations, Hooks & actions, Sidebar, Menus, Messages,
// Resets & safety, and the per-file editor.
import { el, btn, badge, field, allFields, sectionTitle, mcText, miniInline, tooltip, itemTile, chest, seg, switchCtl, chips, drawer, closeDrawer, toast,
  promptDialog, confirmDialog, hoverTip, SAMPLE, ICONS, humanize, toYamlText, fromYamlText } from "./ui.js";
import { isObj, clone, expandSlots, compressSlots, renderMini, stripMini, parseDuration, formatDuration } from "./core.js";
import * as S from "./store.js";
import { editActions, summarize, listUsages, listNames } from "./actions.js";
import { pageHead, C } from "./pages-setup.js";

const go = (h) => { location.hash = h; };
const link = (text, hash, key) => el("a", { href: hash, onclick: (e) => { e.preventDefault(); window.studio.go(hash, key); } }, text);
const idCheck = (exists) => (v) => !/^[a-z0-9][a-z0-9_-]*$/i.test(v) ? "Use letters, numbers, dashes and underscores." : exists(v) ? "That ID is taken." : null;

// ============================================================================================
//  Menus: a chest preview of any menu layout (rows, title, filler, buttons)
// ============================================================================================
function buttonItem(b) {
  if (!isObj(b)) return null;
  for (const st of ["unconfirmed", "idle", "owned"]) if (isObj(b[st])) return { ...b, ...b[st] };
  return b;
}
export function menuSlots(file, base, vars) {
  const L = S.get(file, base) || S.def(file, base) || {};
  const rows = Math.max(1, Math.min(6, +L.rows || 6));
  const size = rows * 9;
  const buttons = {};
  for (const [id, b] of Object.entries(L.buttons || {})) {
    if (!isObj(b) || b.enabled === false) continue;
    const slots = b.slots ? expandSlots(b.slots, size) : b.slot !== undefined ? [+b.slot] : [];
    for (const s of slots) buttons[s] = { id, b: buttonItem(b) };
  }
  const fill = expandSlots(L["filler-slots"], size);
  return { L, rows, size, buttons, fill };
}
/** A clickable chest preview of a menu. extra(i) can put items in slots (entries, offers). */
export function menuChest(file, base, { extra, vars, onButton, onFill, onEmpty, onDrop } = {}) {
  const { L, rows, size, buttons, fill } = menuSlots(file, base, vars);
  const filler = isObj(L.filler) ? L.filler : { material: "GRAY_STAINED_GLASS_PANE" };
  const isFill = (i) => fill === "empty" || fill === "border" ? (fill === "border" ? (Math.floor(i / 9) === 0 || Math.floor(i / 9) === rows - 1 || i % 9 === 0 || i % 9 === 8) : true) : fill === "none" ? false : fill.includes(i);
  return chest(rows, L.title ?? "", (i) => {
    const x = extra?.(i);
    if (x) return x;
    if (buttons[i]) { const { id, b } = buttons[i]; return { item: b, title: id, tip: () => tooltip(b.name ?? id, b.lore, vars), onclick: () => onButton?.(id) }; }
    if (isFill(i) && !(fill === "empty" && extra)) return { item: filler, cls: "dim", title: "filler (click to toggle)", onclick: () => onFill?.(i, true) };
    return { onclick: () => (onEmpty ? onEmpty(i) : onFill?.(i, false)) };
  }, { vars, onDrop });
}
function editMenu(file, base, title) {
  const body = el("div");
  const draw = () => {
    body.innerHTML = "";
    const L = S.get(file, base) || {};
    const prev = el("div", { style: { margin: "6px 0 14px" } });
    const redraw = () => { prev.innerHTML = ""; prev.append(menuChest(file, base, {
      onButton: (id) => editButton(id),
      onFill: (i, on) => {
        const { fill, size } = menuSlots(file, base);
        if (!Array.isArray(fill)) { toast("Filler slots are '" + fill + "'. Switch them to a list below to paint slots.", "info"); return; }
        const set = new Set(fill); on ? set.delete(i) : set.add(i);
        S.set(file, base + ".filler-slots", compressSlots([...set].filter((s) => s < size)));
        redraw();
      } })); };
    redraw();
    body.append(el("div", { class: "small muted" }, "Hover a slot to preview, click a button to edit it, click empty or filler slots to paint the filler."), prev,
      field(file, base + ".title", { label: "Title", type: "mini", onChange: redraw }), field(file, base + ".rows", { label: "Rows", type: "number", min: 1, max: 6, onChange: redraw }),
      el("div", { class: "field" }, el("div", {}, el("div", { class: "lbl" }, "Filler slots"), el("div", { class: "help" }, "Which slots show the filler item.")),
        el("div", { class: "ctl" }, seg([["list", "Painted slots"], ["empty", "Every empty slot"], ["border", "The border"], ["none", "None"]], Array.isArray(L["filler-slots"]) ? "list" : L["filler-slots"] ?? "empty",
          (m) => { S.set(file, base + ".filler-slots", m === "list" ? [] : m); draw(); }))),
      el("details", { class: "more" }, el("summary", {}, "Filler item"), allFields(file, base + ".filler")),
      sectionTitle("Buttons"), el("div", { class: "row" }, Object.keys(L.buttons || {}).map((id) => btn(id, () => editButton(id), "small"))),
      el("details", { class: "more" }, el("summary", {}, "Every option in this menu"), allFields(file, base, { skip: [base + ".buttons", base + ".title", base + ".rows", base + ".filler-slots", base + ".filler"] })));
  };
  const editButton = (id) => drawer("Button: " + id, el("div", {}, el("div", { class: "row", style: { marginBottom: "8px" } }, btn("← Back to " + title, () => editMenu(file, base, title))), allFields(file, base + ".buttons." + id)), { subtitle: file + " › " + base + ".buttons." + id });
  drawer(title, body, { subtitle: file + " › " + base });
  draw();
}

const MENUS = [["Vault", "config.yml", "vault.gui"], ["History", "config.yml", "history.gui"], ["Trade", "trade.yml", "gui"], ["Shop", "shop.yml", "gui"],
  ["Shop confirmation", "shop.yml", "confirm-gui"], ["Titles", "titles.yml", "gui"]];
function menusPage(root) {
  root.append(pageHead("Menus", "Every Headcount menu as it looks in game. Click one to edit its title, rows, filler and buttons."));
  const g = el("div", { class: "grid g2" });
  for (const [name, file, base] of MENUS) {
    g.append(el("div", { class: "card click", onclick: () => editMenu(file, base, name + " menu") }, el("h3", {}, name), el("div", { class: "small muted", style: { marginBottom: "10px" } }, file + " › " + base),
      el("div", { style: { transform: "scale(.82)", transformOrigin: "top left", height: (menuSlots(file, base).rows * 42 + 40) * 0.82 + "px" } }, menuChest(file, base, {}))));
  }
  root.append(g);
}

// ============================================================================================
//  Shop
// ============================================================================================
const STATES = ["affordable", "too-expensive", "sold-out", "limit-reached", "cooldown", "unavailable", "locked"];
function shopPage(root, params) {
  const tiers = () => Object.keys(S.get("shop.yml", "tiers") || {});
  let tier = params[0] && tiers().includes(params[0]) ? params[0] : tiers()[0];
  let page = 1, state = "affordable";
  root.append(pageHead("Shop", "Tiers unlock with last cycle's score. Drag items between slots, click one to edit it, click an empty slot to add one.",
    btn("＋ New tier", async () => {
      const id = await promptDialog("New tier", "Tier ID:", "epic", { check: idCheck((v) => tiers().includes(v)) });
      if (!id) return;
      S.set("shop.yml", "tiers." + id, { name: "<light_purple>" + humanize(id), threshold: 30, entries: {} });
      location.hash = "#/shop/" + id; window.studio.refresh();
    })));
  const tabs = el("div", { class: "row", style: { marginBottom: "12px" } });
  const body = el("div", { class: "split" });
  root.append(tabs, body);
  const draw = () => {
    tabs.innerHTML = "";
    for (const t of tiers()) {
      const T = S.get("shop.yml", "tiers." + t) || {};
      tabs.append(el("button", { class: "btn" + (t === tier ? " primary" : ""), onclick: () => { tier = t; page = 1; draw(); } }, miniInline(T.name || t), " ", el("span", { class: "small", style: { opacity: 0.7 } }, T.threshold === "none" || T.threshold === undefined ? "always" : "≥ " + T.threshold)));
    }
    body.innerHTML = "";
    if (!tier) { body.append(el("div", { class: "empty" }, "No tiers.")); return; }
    const T = S.get("shop.yml", "tiers." + tier) || {};
    const entries = Object.entries(T.entries || {});
    const pages = Math.max(+T.pages || 1, ...entries.map(([, e]) => +e?.page || 1));
    const { size } = menuSlots("shop.yml", "gui");
    const at = {};
    for (const [id, e] of entries) if ((+e?.page || 1) === page) at[+e?.slot || 0] = [id, e];
    const ds = S.get("shop.yml", "default-states") || {};
    const vars = (e) => ({ name: e?.name || stripMini(e?.display?.name || ""), price: e?.price ?? 0, points: 12, missing: Math.max(0, (e?.price ?? 0) - 12), stock: e?.stock ?? "∞",
      purchases_left: e?.["limit-per-cycle"] ? e["limit-per-cycle"] : "∞", limit: e?.["limit-per-cycle"] ?? "∞", bought: 0, cooldown: e?.cooldown ?? "0s", tier: T.name, threshold: T.threshold, tier_number: tiers().indexOf(tier) + 1, tiers: tiers().length, page, pages, last_score: 6, needed: 0 });
    const left = el("div");
    const ctl = el("div", { class: "row", style: { marginBottom: "10px" } }, el("span", { class: "small muted" }, "Show items as:"), el("select", { onchange: (e) => { state = e.target.value; draw(); } }, STATES.map((s) => el("option", { value: s, selected: s === state }, s))),
      pages > 1 || true ? el("span", { class: "row gap-s" }, el("span", { class: "small muted" }, "Page"), seg([...Array(pages + 1).keys()].slice(1).map((n) => [n, String(n)]), page, (n) => { page = +n; draw(); }),
        btn("＋ page", () => { S.set("shop.yml", "tiers." + tier + ".pages", pages + 1); page = pages + 1; draw(); }, "small ghost")) : null);
    const grid = menuChest("shop.yml", "gui", {
      vars: vars({}),
      extra: (i) => {
        const hit = at[i];
        if (!hit) return null;
        const [id, e] = hit;
        const look = { ...(isObj(e?.display) ? e.display : {}), ...(isObj(ds[state]) ? ds[state] : {}), ...(isObj(e?.["display-states"]?.[state]) ? e["display-states"][state] : {}) };
        const lore = [...[].concat(e?.display?.lore || []), ...[].concat((isObj(e?.["display-states"]?.[state]) && e["display-states"][state].lore) || ds[state]?.lore || [])];
        return { item: { ...look, material: look.material || e?.display?.material || "PAPER" }, draggable: true, title: id, tip: () => tooltip(look.name ?? e?.display?.name ?? id, lore, vars(e)), onclick: () => editEntry(id), cls: "sel" };
      },
      onEmpty: (i) => addEntry(i),
      onButton: () => editMenu("shop.yml", "gui", "Shop menu"),
      onDrop: (from, to) => {
        const a = at[from];
        if (!a) return;
        const b = at[to];
        S.set("shop.yml", `tiers.${tier}.entries.${a[0]}.slot`, to);
        if (b) S.set("shop.yml", `tiers.${tier}.entries.${b[0]}.slot`, from);
        draw();
      },
    });
    left.append(ctl, grid, el("div", { class: "small muted", style: { marginTop: "8px" } }, entries.length + " items in this tier. Buttons and filler come from the shop menu (", link("edit menu", "#/menus"), ")."));
    const right = el("div", { class: "sticky" }, el("div", { class: "card" }, el("h4", {}, "Tier"),
      field("shop.yml", `tiers.${tier}.name`, { label: "Name", type: "mini", onChange: () => draw() }),
      field("shop.yml", `tiers.${tier}.threshold`, { label: "Unlocks at last-cycle score", type: "text", help: "A score, or none (always unlocked)." }),
      el("div", { class: "row", style: { marginTop: "10px" } }, btn("Delete tier", async () => {
        if (await confirmDialog("Delete the tier " + tier + "?", `Its ${entries.length} items are removed too (undo with Ctrl+Z).`, { ok: "Delete", danger: true })) { S.remove("shop.yml", "tiers." + tier); tier = tiers()[0]; draw(); }
      }, "danger small"))),
      el("details", { class: "card more", style: { marginTop: "14px" } }, el("summary", {}, "Shop settings"), allFields("shop.yml", "settings")),
      el("details", { class: "card more", style: { marginTop: "14px" } }, el("summary", {}, "How states look (default-states)"), allFields("shop.yml", "default-states")));
    body.append(left, right);
  };
  const addEntry = async (slot) => {
    const id = await promptDialog("New shop item", `Item ID (slot ${slot}, page ${page}):`, "new-item", { check: idCheck((v) => S.get("shop.yml", `tiers.${tier}.entries.${v}`)) });
    if (!id) return;
    S.set("shop.yml", `tiers.${tier}.entries.${id}`, { page, slot, price: 5, display: { material: "PAPER", name: "<white>" + humanize(id) }, actions: [{ message: "<green>Thanks for buying!" }] });
    draw(); editEntry(id);
  };
  const editEntry = (id) => {
    const base = `tiers.${tier}.entries.${id}`;
    const titles = Object.keys(S.get("titles.yml", "titles") || {});
    const allEntries = Object.entries(S.get("shop.yml", "tiers") || {}).flatMap(([t, T]) => Object.keys(T?.entries || {}).map((e) => e));
    const prev = el("div");
    const drawPrev = () => { const e = S.get("shop.yml", base) || {}; prev.innerHTML = ""; prev.append(el("div", { class: "row", style: { alignItems: "flex-start" } }, itemTile(e.display || "PAPER", { size: 44 }), tooltip(e.display?.name ?? id, e.display?.lore, { price: e.price }))); };
    drawPrev();
    const body = el("div", {}, prev,
      el("div", { class: "row", style: { margin: "12px 0" } }, btn("⚡ Edit what it does", () => editActions("shop.yml", base + ".actions", { title: "Buying " + id, hint: "Runs on purchase. Variables: <player> <price> <points> <name> …" }), "primary"),
        btn("Rename", async () => { const n = await promptDialog("Rename", "New ID:", id); if (n && n !== id) { S.rename("shop.yml", base, n); closeDrawer(); draw(); } }),
        btn("Delete", async () => { if (await confirmDialog("Delete " + id + "?", "Undo with Ctrl+Z.", { ok: "Delete", danger: true })) { S.remove("shop.yml", base); closeDrawer(); draw(); } }, "danger")),
      sectionTitle("Price and limits"),
      field("shop.yml", base + ".price", { label: "Price", type: "number", suffix: "points", min: 0 }),
      field("shop.yml", base + ".limit-per-cycle", { label: "Limit per player per cycle", type: "number", min: 0, help: "0 = no limit." }),
      field("shop.yml", base + ".stock", { label: "Stock (everyone together)", type: "number", help: "-1 = unlimited." }),
      field("shop.yml", base + ".cooldown", { label: "Cooldown per player", type: "duration", placeholder: "e.g. 1d" }),
      field("shop.yml", base + ".confirm", { label: "Ask to confirm", type: "enum", options: [["", "price decides"], ["true", "always"], ["false", "never"]] }),
      sectionTitle("Looks"),
      field("shop.yml", base + ".display.material", { label: "Item", type: "material", onChange: drawPrev }),
      field("shop.yml", base + ".display.name", { label: "Name", type: "mini", onChange: drawPrev }),
      field("shop.yml", base + ".display.lore", { label: "Lore", type: "yaml", onChange: drawPrev }),
      field("shop.yml", base + ".display.glint", { label: "Glint", type: "bool", onChange: drawPrev }),
      sectionTitle("Requirements"),
      field("shop.yml", base + ".requires.titles", { label: "Owns titles", type: "list", suggestions: titles }),
      field("shop.yml", base + ".requires.purchases", { label: "Bought before", type: "list", suggestions: allEntries }),
      field("shop.yml", base + ".requires.purchases-this-cycle", { label: "Bought this cycle", type: "list", suggestions: allEntries }),
      field("shop.yml", base + ".requires.permissions", { label: "Has permissions", type: "list" }),
      el("details", { class: "more" }, el("summary", {}, "Everything else"), allFields("shop.yml", base, { skip: [base + ".actions", base + ".display", base + ".requires", base + ".price", base + ".limit-per-cycle", base + ".stock", base + ".cooldown", base + ".confirm"] })));
    drawer("Shop item: " + id, body, { subtitle: "shop.yml › " + base, onClose: () => draw() });
  };
  draw();
}

// ============================================================================================
//  Titles
// ============================================================================================
function titlesPage(root) {
  root.append(pageHead("Titles", "Titles players own and equip with /hc titles. They show through %headcount_title% and, if you like, as a LuckPerms prefix.",
    btn("＋ New title", async () => {
      const id = await promptDialog("New title", "Title ID:", "hero", { check: idCheck((v) => S.get("titles.yml", "titles." + v)) });
      if (!id) return;
      S.set("titles.yml", "titles." + id, { display: "<aqua>[" + humanize(id) + "]", description: ["<gray>How to earn it."], icon: { material: "NAME_TAG" } });
      window.studio.refresh();
    }, "primary")));
  const fmt = () => S.get("titles.yml", "settings.format") ?? "<title> ";
  const g = el("div", { class: "grid g3" });
  for (const [id, t] of Object.entries(S.get("titles.yml", "titles") || {})) {
    g.append(el("div", { class: "card click", onclick: () => edit(id) }, el("div", { class: "row" }, itemTile(t?.icon || "NAME_TAG"), el("div", { class: "grow" }, mcText(t?.display || id)), t?.enabled === false ? badge("off", "plain") : null),
      el("div", { class: "mc small", style: { marginTop: "8px" }, html: renderMini(fmt().replace("<title>", t?.display || id) + "<white>Steve<gray>: gg!", { vars: SAMPLE }) }),
      el("div", { class: "small muted", style: { marginTop: "6px" } }, [].concat(t?.description || []).map(stripMini).join(" "))));
  }
  const edit = (id) => {
    const base = "titles." + id;
    drawer("Title: " + id, el("div", {}, field("titles.yml", base + ".display", { label: "Shown as", type: "mini" }), field("titles.yml", base + ".description", { label: "Description", type: "yaml" }),
      field("titles.yml", base + ".icon.material", { label: "Menu icon", type: "material" }), field("titles.yml", base + ".enabled", { label: "Enabled", type: "bool" }),
      el("div", { class: "row", style: { marginTop: "12px" } }, btn("Delete", async () => { if (await confirmDialog("Delete " + id + "?", "Players who own it keep the ID until it's reset; it just stops showing.", { ok: "Delete", danger: true })) { S.remove("titles.yml", base); closeDrawer(); window.studio.refresh(); } }, "danger"))),
      { subtitle: "titles.yml › " + base, onClose: () => window.studio.refresh() });
  };
  root.append(g, sectionTitle("Settings"), el("div", { class: "panel" }, el("div", { class: "pb" }, allFields("titles.yml", "settings"))),
    el("div", { class: "row", style: { marginTop: "14px" } }, btn("Edit the titles menu", () => editMenu("titles.yml", "gui", "Titles menu"))));
}

// ============================================================================================
//  Trading
// ============================================================================================
function tradingPage(root) {
  root.append(pageHead("Trading", "/trade <player> rules: what can be traded, by whom, when trades are blocked or cancelled, and the trade screen."));
  const split = el("div", { class: "split" });
  const left = el("div");
  const sec = (t, base, skip) => el("div", { class: "panel", style: { marginBottom: "14px" } }, el("div", { class: "ph" }, el("h3", {}, t)), el("div", { class: "pb" }, allFields("trade.yml", base, { skip })));
  const sts = Object.keys(S.get("statuses.yml", "statuses") || {});
  left.append(sec("Requests", "request"), sec("What can be traded", "allowed-types"), sec("Limits", "limits"), sec("Confirming", "confirm"), sec("Cooldown", "cooldown"), sec("Range", "range"),
    el("div", { class: "panel", style: { marginBottom: "14px" } }, el("div", { class: "ph" }, el("h3", {}, "Statuses that block trades")), el("div", { class: "pb" },
      field("trade.yml", "statuses.block-request", { label: "Can't send a request", type: "list", suggestions: sts }), field("trade.yml", "statuses.block-accept", { label: "Can't accept", type: "list", suggestions: sts }),
      field("trade.yml", "statuses.cancel-open", { label: "Cancels an open trade", type: "list", suggestions: sts }), el("div", { class: "small muted" }, "Each has its own message (lang gates.trade-*) and bypass permission. ", link("Why is it blocked? →", "#/why")))),
    sec("Cancelling", "cancel"), el("div", { class: "panel" }, el("div", { class: "pb" }, field("trade.yml", "save-player-data-after-trade"))));
  const own = new Set(expandSlots(S.get("trade.yml", "gui.own-slots") ?? S.def("trade.yml", "gui.own-slots")));
  const other = new Set(expandSlots(S.get("trade.yml", "gui.other-slots") ?? S.def("trade.yml", "gui.other-slots")));
  const right = el("div", { class: "sticky" }, el("div", { class: "card" }, el("h4", {}, "The trade screen"),
    menuChest("trade.yml", "gui", { vars: { other: "Alex", my_heads: 2, max_heads: 24, my_items: 1, max_items: 24, their_heads: 1, their_items: 0, seconds: 3 },
      extra: (i) => own.has(i) ? (i === [...own][0] ? { item: "PLAYER_HEAD", title: "your offer" } : i === [...own][1] ? { item: "DIAMOND", count: 3 } : { cls: "", title: "your offer" })
        : other.has(i) ? (i === [...other][0] ? { item: "PLAYER_HEAD", title: "their offer", cls: "dim" } : { cls: "dim", title: "their offer (mirror)" }) : null,
      onButton: () => editMenu("trade.yml", "gui", "Trade screen") }),
    el("div", { class: "small muted", style: { marginTop: "8px" } }, "Left: your offer. Right (dimmed): theirs, mirrored. ", link("Edit the screen", "#/menus"))));
  split.append(left, right);
  root.append(split);
}

// ============================================================================================
//  Statuses
// ============================================================================================
function statusUses(name) {
  const out = [];
  const lists = [["config.yml", "vault.block-movement.statuses", "vault gate"], ["trade.yml", "statuses.block-request", "trade requests"], ["trade.yml", "statuses.block-accept", "trade accepts"], ["trade.yml", "statuses.cancel-open", "open trades"]];
  for (const [f, p, what] of lists) if ((S.get(f, p) || []).some((x) => String(x).replace(/^!/, "") === name)) out.push(what);
  for (const [n, s] of Object.entries(S.get("statuses.yml", "statuses") || {})) {
    if (!isObj(s)) continue;
    if ([...(s.statuses || []), s.status, s.fallback].some((x) => String(x ?? "").replace(/^!/, "") === name)) out.push("status " + n);
  }
  const scan = (file, v) => { if (Array.isArray(v)) v.forEach((x) => scan(file, x)); else if (isObj(v)) for (const [k, x] of Object.entries(v)) { if ((k === "statuses" || k === "any-statuses") && Array.isArray(x) && file !== "statuses.yml" && x.some((y) => String(y).replace(/^!/, "") === name)) out.push(file + " conditions"); scan(file, x); } };
  for (const f of ["outcomes.yml", "shop.yml", "actions.yml", "config.yml"]) scan(f, f === "config.yml" ? S.get(f, "hooks") : S.doc(f)?.data);
  return [...new Set(out)];
}
function statusesPage(root) {
  const sources = S.store.catalog?.statuses || [];
  root.append(pageHead("Statuses", "Named player states (in PvP, AFK, low health…) used by the vault and trade rules, conditions and deny lists. Every status is also %headcount_status_<name>%.",
    btn("＋ New status", async () => {
      const id = await promptDialog("New status", "Status name:", "my-status", { check: idCheck((v) => S.get("statuses.yml", "statuses." + v)) });
      if (!id) return;
      S.set("statuses.yml", "statuses." + id, { source: "health", below: 6 });
      window.studio.refresh(); edit(id);
    }, "primary")));
  const t = el("table", { class: "t" }, el("tr", {}, el("th", {}, "Status"), el("th", {}, "Source"), el("th", {}, "Settings"), el("th", {}, "Used by")));
  for (const [name, s] of Object.entries(S.get("statuses.yml", "statuses") || {})) {
    const settings = isObj(s) ? Object.entries(s).filter(([k]) => !["source", "description"].includes(k)).map(([k, v]) => k + ": " + (Array.isArray(v) ? v.join(", ") : v)).join(" · ") : "";
    const uses = statusUses(name);
    t.append(el("tr", { class: "click", onclick: () => edit(name) }, el("td", {}, el("b", {}, name)), el("td", {}, badge(s?.source || "?", "info")), el("td", { class: "small muted" }, settings),
      el("td", {}, uses.length ? uses.map((u) => badge(u, "plain")) : el("span", { class: "faint small" }, "—"))));
  }
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)), sectionTitle("Placeholder text"), el("div", { class: "panel" }, el("div", { class: "pb" }, allFields("statuses.yml", "settings"))));
  const edit = (name) => {
    const base = "statuses." + name;
    const body = el("div");
    const draw = () => {
      body.innerHTML = "";
      const s = S.get("statuses.yml", base) || {};
      const src = sources.find((x) => x.source === s.source);
      body.append(el("div", { class: "field" }, el("div", {}, el("div", { class: "lbl" }, "Source"), el("div", { class: "help" }, src ? src.trueWhen : "")),
        el("div", { class: "ctl" }, el("select", { onchange: (e) => { S.set("statuses.yml", base + ".source", e.target.value); draw(); } },
          Object.entries(sources.reduce((g, x) => ((g[x.group] ||= []).push(x), g), {})).map(([g, xs]) => el("optgroup", { label: g }, xs.map((x) => el("option", { value: x.source, selected: x.source === s.source }, x.source))))),
          src ? el("div", { class: "small muted", style: { marginTop: "4px" } }, "Settings: " + (src.settingsText || "none")) : null)));
      const keys = [...new Set([...(src?.settings || []), ...Object.keys(s).filter((k) => !["source", "fallback", "unavailable", "description"].includes(k))])];
      const names = Object.keys(S.get("statuses.yml", "statuses") || {});
      for (const k of keys) {
        const type = k === "statuses" ? "list" : k === "status" ? "enum" : undefined;
        body.append(field("statuses.yml", base + "." + k, { label: humanize(k), type, options: type === "enum" ? names : undefined, select: true, suggestions: [...names, ...names.map((x) => "!" + x)] }));
      }
      body.append(sectionTitle("When the source can't answer"), field("statuses.yml", base + ".fallback", { label: "Fall back to", type: "enum", options: ["", ...names.filter((x) => x !== name)], select: true }),
        field("statuses.yml", base + ".unavailable", { label: "Else read as", type: "bool" }), field("statuses.yml", base + ".description", { label: "Note", type: "text" }),
        el("div", { class: "small muted", style: { marginTop: "10px" } }, "Used by: " + (statusUses(name).join(", ") || "nothing yet")),
        el("div", { class: "row", style: { marginTop: "12px" } }, btn("Delete", async () => { if (await confirmDialog("Delete " + name + "?", "Rules that use it will report an unknown status.", { ok: "Delete", danger: true })) { S.remove("statuses.yml", base); closeDrawer(); window.studio.refresh(); } }, "danger")));
    };
    draw();
    drawer("Status: " + name, body, { subtitle: "statuses.yml › " + base, onClose: () => window.studio.refresh() });
  };
}

// ============================================================================================
//  Integrations
// ============================================================================================
const INTEG = [
  ["pvp", "PvP", "The PvP toggle, combat tag and new-player protection behind the in-pvp, combat-tagged and newbie-protected statuses and the pvp action.", ["included", "pvpmanager", "none"], { pvpmanager: "PvPManager" }],
  ["placeholders", "Placeholders", "%...% placeholders in Headcount's text, the formula and statuses.", ["included", "placeholderapi", "none"], { placeholderapi: "PlaceholderAPI" }],
  ["economy", "Money", "The money action (points need nothing).", ["vault", "none"], { vault: "Vault" }],
  ["permissions", "Permissions", "Permission, group, track and prefix actions, and title prefixes.", ["included", "luckperms", "vault", "none"], { luckperms: "LuckPerms", vault: "Vault" }],
  ["moderation", "Moderation", "Ban, mute, kick, warn and jail actions.", ["included", "libertybans", "litebans", "advancedban", "essentials", "commands", "none"], { libertybans: "LibertyBans", litebans: "LiteBans", advancedban: "AdvancedBan", essentials: "Essentials" }],
  ["player-state", "Player states", "afk, vanished, god, jailed and muted for statuses.", ["included", "essentials", "none"], { essentials: "Essentials" }],
  ["discord", "Discord", "The discord action.", ["discordsrv", "none"], { discordsrv: "DiscordSRV" }],
  ["bedrock", "Bedrock", "Telling Bedrock players apart (typed /trade accept, the bedrock status).", ["floodgate", "geyser", "none"], { floodgate: "floodgate", geyser: "Geyser-Spigot" }],
];
const MSG_EVENTS = [["toggled", ["pvp.toggled-on", "pvp.toggled-off"]], ["tag-start", ["pvp.tag-start"]], ["tag-end", ["pvp.tag-end"]], ["blocked-hit", ["pvp.blocked-hit.target-off", "pvp.blocked-hit.self-off"]],
  ["blocked-action", ["pvp.blocked-command", "pvp.cooldown"]]];
function integrationsPage(root) {
  root.append(pageHead("Integrations", "Who handles each job: Headcount's own included version, another plugin, or nobody. Switching /pvp between plugins needs a server restart."));
  const installed = S.store.info.plugins || null;
  for (const [key, title, desc, options, pluginOf] of INTEG) {
    const cur = S.get("integrations.yml", key + ".provider") ?? S.def("integrations.yml", key + ".provider");
    const card = el("div", { class: "panel", style: { marginBottom: "14px" } });
    const opts = options.map((o) => [o, el("span", {}, o, installed && pluginOf[o] ? el("span", { style: { marginLeft: "4px", fontSize: "10px", color: installed.some((p) => p.toLowerCase().startsWith(pluginOf[o].toLowerCase())) ? "var(--good)" : "var(--faint)" } }, installed.some((p) => p.toLowerCase().startsWith(pluginOf[o].toLowerCase())) ? "●" : "○") : null)]);
    const segEl = el("div", { class: "seg" });
    const drawSeg = (v) => { segEl.innerHTML = ""; for (const [o, lab] of opts) segEl.append(el("button", { class: o === v ? "on" : "", onclick: () => { S.set("integrations.yml", key + ".provider", o); drawSeg(o); drawBody(o); } }, lab)); };
    drawSeg(cur);
    const help = S.doc("integrations.yml").parsed.index.get(key + ".provider")?.help || "";
    const bodyEl = el("div", { class: "pb" });
    const drawBody = (v) => {
      bodyEl.innerHTML = "";
      bodyEl.append(el("div", { class: "small muted", style: { whiteSpace: "pre-wrap" } }, help));
      if (v === "auto") bodyEl.append(el("div", { class: "note warn" }, "'auto' is from 1.0 and no longer an option; pick one."));
      if (installed && pluginOf[v] && !installed.some((p) => p.toLowerCase().startsWith(pluginOf[v].toLowerCase()))) bodyEl.append(el("div", { class: "note warn", style: { marginTop: "8px" } }, pluginOf[v] + " doesn't look installed on this server; Headcount would fall back."));
      if (key === "pvp" && v === "included") bodyEl.append(pvpIncluded());
      else if (key === "moderation") bodyEl.append(moderationExtra());
      else bodyEl.append(el("details", { class: "more" }, el("summary", {}, "More settings"), allFields("integrations.yml", key, { skip: [key + ".provider", key + ".included"] })));
      if (key === "player-state" && v === "included") bodyEl.append(sectionTitle("Included AFK"), allFields("integrations.yml", "player-state.included"));
    };
    drawBody(cur);
    card.append(el("div", { class: "ph" }, el("div", { class: "grow" }, el("h3", {}, title), el("div", { class: "small muted" }, desc)), segEl), bodyEl);
    root.append(card);
  }
  root.append(el("div", { class: "panel" }, el("div", { class: "ph" }, el("h3", {}, "Custom items")), el("div", { class: "pb" }, field("integrations.yml", "items.providers", { label: "Item plugins", type: "list", suggestions: ["itemsadder", "oraxen", "nexo"] }))));
}
function pvpIncluded() {
  const box = el("div");
  const sec = (t, base) => el("details", { class: "more", open: base.endsWith("toggle") }, el("summary", {}, t), allFields("integrations.yml", base));
  box.append(sec("The /pvp toggle", "pvp.included.toggle"), sec("New-player protection", "pvp.included.newbie-protection"), sec("Respawn protection", "pvp.included.respawn-protection"),
    sec("Combat tag and what it blocks", "pvp.included.combat-tag"), sec("Combat logging", "pvp.included.combat-log"));
  const msgs = el("details", { class: "more" }, el("summary", {}, "Messages: how each reaches the player"));
  for (const [ev, keys] of MSG_EVENTS) {
    const base = "pvp.included.messages." + ev;
    const show = el("div", { style: { marginTop: "6px" } });
    const drawShow = () => {
      show.innerHTML = "";
      const ch = S.get("integrations.yml", base + ".channel") ?? "chat";
      const text = S.lang(keys[0]) || "";
      const scene = el("div", { class: "mcscene", style: { minHeight: "120px", justifyContent: "center", alignItems: ch === "title" ? "center" : "flex-end", padding: "10px" } });
      const msg = el("div", { class: "mc", style: { background: ch === "chat" ? "rgba(0,0,0,.45)" : "transparent", fontSize: ch === "title" ? "18px" : "13px", alignSelf: ch === "chat" ? "flex-end" : "auto", marginRight: ch === "chat" ? "auto" : "0" }, html: renderMini(text, { vars: SAMPLE, prefix: S.lang("prefix") }) });
      if (ch !== "none") scene.append(msg); else scene.append(el("div", { class: "mc", style: { background: "transparent", opacity: 0.6 } }, "(not shown)"));
      show.append(scene);
    };
    msgs.append(el("div", { class: "card", style: { margin: "8px 0" } }, el("b", {}, ev), el("span", { class: "small muted" }, "  lang: " + keys.join(", ")),
      field("integrations.yml", base + ".channel", { label: "Channel", type: "enum", options: ["chat", "actionbar", "title", "none"], onChange: drawShow }),
      field("integrations.yml", base + ".sound", { label: "Sound", type: "text", placeholder: "minecraft:block.note_block.bass" }),
      field("lang/en_US.yml", keys[0], { label: "Text", type: "mini", onChange: drawShow }), show));
    drawShow();
  }
  msgs.append(field("integrations.yml", "pvp.included.messages.countdown", { label: "Countdown while in combat", type: "enum", options: ["actionbar", "bossbar", "none"] }),
    field("integrations.yml", "pvp.included.messages.countdown-bossbar-color"));
  box.append(msgs);
  return box;
}
function moderationExtra() {
  const box = el("div");
  const kinds = ["ban", "mute", "kick", "warn", "jail"];
  const t = el("table", { class: "t" }, el("tr", {}, el("th", {}, "Kind"), el("th", {}, "Handled by")));
  for (const k of kinds) {
    const cur = S.get("integrations.yml", "moderation.kinds." + k) ?? "";
    t.append(el("tr", {}, el("td", {}, k), el("td", {}, el("select", { onchange: (e) => S.set("integrations.yml", "moderation.kinds." + k, e.target.value || undefined) },
      ["", "included", "libertybans", "litebans", "advancedban", "essentials", "commands", "none"].map((o) => el("option", { value: o, selected: o === cur }, o || "(the main provider)"))))));
  }
  box.append(el("details", { class: "more", open: true }, el("summary", {}, "Per kind"), t), el("details", { class: "more" }, el("summary", {}, "Included jail"), allFields("integrations.yml", "moderation.included-jail")),
    el("details", { class: "more" }, el("summary", {}, "Included mute"), allFields("integrations.yml", "moderation.included-mute")),
    el("details", { class: "more" }, el("summary", {}, "More (operator name, LiteBans and command templates)"), allFields("integrations.yml", "moderation", { skip: ["moderation.provider", "moderation.kinds", "moderation.included-jail", "moderation.included-mute"] })));
  return box;
}

// ============================================================================================
//  Hooks & actions
// ============================================================================================
function hooksPage(root) {
  root.append(pageHead("Hooks & actions", "Action lists that run at moments in a cycle and in a player's time on the server, and the named lists you can reuse anywhere."));
  const moment = (label, sub, onclick, color) => el("div", { class: "card click", style: { minWidth: "150px", borderTop: "3px solid " + color }, onclick }, el("b", {}, label), el("div", { class: "small muted" }, sub));
  const playerHooks = ["first-join", "first-join-cycle", "became-active", "join"];
  const hookCount = (p) => { const v = C(p); return Array.isArray(v) ? v.length : 0; };
  root.append(sectionTitle("A player's time"), el("div", { class: "row", style: { alignItems: "stretch" } },
    playerHooks.flatMap((h, i) => [i ? el("span", { class: "faint" }, "→") : null, moment(humanize(h), hookCount("hooks.player." + h) + " actions", () => editActions("config.yml", "hooks.player." + h, { title: "Hook: " + h, hint: "Variables: <player> <cycle> <time_left> <points> <streak> <streak_text>" }), "var(--info)")])));
  const warnings = Object.entries(C("hooks.warnings") || {}).sort((a, b) => parseDuration(b[1]?.before) - parseDuration(a[1]?.before));
  const cycleHook = (path, title) => {
    const body = el("div", {},
      el("div", { class: "row", style: { marginBottom: "10px" } }, btn("⚡ Global actions (once for the server)", () => editActions("config.yml", path + ".global", { title: title + ": global" }), "primary"),
        S.get("config.yml", path + ".per-player") !== undefined || path.includes("cycle-") ? btn("⚡ Per-player actions", () => editActions("config.yml", path + ".per-player.actions", { title: title + ": per player" })) : null),
      path.includes("warnings.") ? field("config.yml", path + ".before", { label: "Fires this long before the end", type: "duration" }) : null,
      path.includes("warnings.") ? field("config.yml", path + ".enabled", { label: "Enabled", type: "bool" }) : null,
      S.get("config.yml", path + ".per-player") ? el("div", {}, sectionTitle("Per player"), field("config.yml", path + ".per-player.players", { label: "Which players", type: "enum", options: ["all", "active", "inactive", "online"] }),
        field("config.yml", path + ".per-player.offline", { label: "Offline players", type: "enum", options: ["deliver", "skip"] }), field("config.yml", path + ".per-player.expire-after-cycles", { label: "Drop stored hooks after", suffix: "cycles", type: "number" })) : null,
      path.includes("warnings.") ? el("div", { class: "row", style: { marginTop: "12px" } }, btn("Delete warning", async () => { if (await confirmDialog("Delete this warning?", "", { ok: "Delete", danger: true })) { S.remove("config.yml", path); closeDrawer(); window.studio.refresh(); } }, "danger")) : null);
    drawer(title, body, { subtitle: "config.yml › " + path });
  };
  root.append(sectionTitle("A cycle"), el("div", { class: "row", style: { alignItems: "stretch" } },
    moment("Cycle start", "everyone's vault refills", () => cycleHook("hooks.cycle-start", "Cycle start"), "var(--good)"),
    warnings.map(([id, w]) => [el("span", { class: "faint" }, "→"), moment("Warning " + id, (w?.before ?? "?") + " before the end", () => cycleHook("hooks.warnings." + id, "Warning " + id), "var(--warn)")]),
    el("span", { class: "faint" }, "→"), moment("Cycle end", "scores, outcomes, report", () => cycleHook("hooks.cycle-end", "Cycle end"), "var(--bad)"),
    el("button", { class: "btn ghost", onclick: async () => {
      const id = await promptDialog("New warning", "How long before the end (also its ID), e.g. 30m, 6h, 2d:", "30m", { check: (v) => isNaN(parseDuration(v)) ? "Not a duration." : C("hooks.warnings." + v) ? "Exists." : null });
      if (!id) return;
      S.set("config.yml", "hooks.warnings." + id, { before: id, global: [{ message: "<gold>Cycle <cycle> ends in <white>" + id + "</white>!" }] });
      window.studio.refresh();
    } }, "＋ warning")));
  root.append(el("div", { class: "panel", style: { marginTop: "14px" } }, el("div", { class: "pb" }, allFields("config.yml", "hooks", { skip: ["hooks.cycle-start", "hooks.cycle-end", "hooks.warnings", "hooks.player"] }))));
  const names = listNames();
  root.append(sectionTitle("Named action lists", btn("＋ New list", async () => {
    const id = await promptDialog("New action list", "Name:", "my-list", { check: idCheck((v) => names.includes(v)) });
    if (!id) return;
    S.set("actions.yml", "lists." + id, [{ message: "<green>Hello <player>!" }]);
    editActions("actions.yml", "lists." + id, { title: "List: " + id });
  }, "small")));
  const t = el("table", { class: "t" }, el("tr", {}, el("th", {}, "List"), el("th", {}, "Does"), el("th", {}, "Used by"), el("th", {})));
  for (const n of names) {
    const v = S.get("actions.yml", "lists." + n);
    const uses = listUsages(n);
    t.append(el("tr", { class: "click", onclick: () => editActions("actions.yml", "lists." + n, { title: "List: " + n }) }, el("td", {}, el("b", {}, n)),
      el("td", { class: "small muted" }, (Array.isArray(v) ? v : [v]).slice(0, 3).map(summarize).join(" · ")),
      el("td", {}, uses.length ? uses.slice(0, 4).map((u) => badge(u.file.replace(".yml", "") + " " + u.path.split(".").slice(0, 3).join("."), "plain")) : el("span", { class: "faint small" }, "not used")),
      el("td", {}, el("button", { class: "iconbtn", title: "Rename", onclick: async (e) => { e.stopPropagation(); const nn = await promptDialog("Rename list", "New name (places that use it are not renamed):", n); if (nn && nn !== n) { S.rename("actions.yml", "lists." + n, nn); window.studio.refresh(); } } }, "✎"),
        el("button", { class: "iconbtn", title: "Delete", onclick: async (e) => { e.stopPropagation(); if (await confirmDialog("Delete " + n + "?", uses.length ? `It's used in ${uses.length} place(s), which would then fail.` : "Not used anywhere.", { ok: "Delete", danger: true })) { S.remove("actions.yml", "lists." + n); window.studio.refresh(); } } }, "🗑"))));
  }
  root.append(el("div", { class: "panel" }, el("div", { class: "pb" }, t)));
}

// ============================================================================================
//  Sidebar
// ============================================================================================
function sidebarPage(root) {
  root.append(pageHead("Sidebar", "The scoreboard on the right of the screen (/hc sidebar). Up to 15 lines; %headcount_...% placeholders update every interval."));
  const split = el("div", { class: "split" });
  const left = el("div", { class: "panel" }, el("div", { class: "pb" }));
  const prev = el("div", { class: "sticky" });
  const drawPrev = () => {
    prev.innerHTML = "";
    const lines = C("sidebar.lines") || [];
    const sb = el("div", { class: "scoreboard" }, el("div", { class: "sbt", html: renderMini(C("sidebar.title"), { vars: SAMPLE }) }));
    lines.forEach((l, i) => sb.append(el("div", { class: "row", style: { flexWrap: "nowrap", gap: "14px" } }, el("span", { class: "grow", html: renderMini(l, { vars: SAMPLE }) || "&nbsp;" }), el("span", { style: { color: "#FF5555", opacity: 0.0 } }, lines.length - i))));
    prev.append(el("div", { class: "mcscene" }, sb), el("div", { class: "small muted", style: { marginTop: "8px" } }, `${lines.length}/15 lines · sample values: cycle 3, 7 unique, score 4, 12 points`));
  };
  const linesBox = el("div");
  const drawLines = () => {
    linesBox.innerHTML = "";
    const lines = [...(C("sidebar.lines") || [])];
    lines.forEach((l, i) => {
      const inp = el("input", { type: "text", value: l, spellcheck: "false" });
      inp.addEventListener("input", () => { lines[i] = inp.value; S.set("config.yml", "sidebar.lines", lines); drawPrev(); });
      linesBox.append(el("div", { class: "row", style: { flexWrap: "nowrap", margin: "4px 0" } }, el("span", { class: "faint small", style: { width: "18px" } }, i + 1), inp,
        el("button", { class: "iconbtn", disabled: i === 0, onclick: () => { [lines[i - 1], lines[i]] = [lines[i], lines[i - 1]]; S.set("config.yml", "sidebar.lines", lines); drawLines(); drawPrev(); } }, "↑"),
        el("button", { class: "iconbtn", disabled: i === lines.length - 1, onclick: () => { [lines[i + 1], lines[i]] = [lines[i], lines[i + 1]]; S.set("config.yml", "sidebar.lines", lines); drawLines(); drawPrev(); } }, "↓"),
        el("button", { class: "iconbtn", onclick: () => { lines.splice(i, 1); S.set("config.yml", "sidebar.lines", lines); drawLines(); drawPrev(); } }, "✕")));
    });
    linesBox.append(el("div", { class: "row", style: { marginTop: "6px" } }, btn("＋ line", () => { lines.push("<gray>New line"); S.set("config.yml", "sidebar.lines", lines); drawLines(); drawPrev(); }, "small"),
      el("select", { onchange: (e) => { if (e.target.value) { lines.push("<gray>" + humanize(e.target.value) + ": <white>%headcount_" + e.target.value + "%"); S.set("config.yml", "sidebar.lines", lines); drawLines(); drawPrev(); } } },
        el("option", { value: "" }, "＋ line with a placeholder…"), (S.store.catalog?.placeholders || []).map((p) => el("option", { value: p.name }, p.name + " — " + p.description)))));
  };
  drawLines();
  left.firstChild.append(field("config.yml", "sidebar.enabled"), field("config.yml", "sidebar.shown-for-new-players"), field("config.yml", "sidebar.update-interval"),
    field("config.yml", "sidebar.title", { type: "mini", onChange: drawPrev }), el("div", { class: "field" }, el("div", {}, el("div", { class: "lbl" }, "Lines")), el("div", { class: "ctl" }, linesBox)),
    field("config.yml", "sidebar.lines-before-start", { type: "yaml" }));
  drawPrev();
  split.append(left, prev);
  root.append(split);
}

// ============================================================================================
//  Messages (lang)
// ============================================================================================
function messagesPage(root, params) {
  const L = "lang/en_US.yml";
  const data = S.doc(L).data || {};
  const groups = Object.keys(data).filter((k) => k !== "config-version");
  let group = params[0] && groups.includes(params[0]) ? params[0] : "general" in data ? "general" : groups[0];
  let filter = "all", q = "";
  root.append(pageHead("Messages", "Everything Headcount says (lang/en_US.yml), with previews. An empty message is never sent."));
  const split = el("div", { style: { display: "grid", gridTemplateColumns: "220px 1fr", gap: "16px", alignItems: "start" } });
  const nav = el("div", { class: "panel sticky", style: { padding: "6px", maxHeight: "80vh", overflow: "auto" } });
  const body = el("div");
  const search = el("input", { type: "search", placeholder: "Search every message…", oninput: (e) => { q = e.target.value.toLowerCase(); draw(); } });
  const flat = (obj, p = "") => Object.entries(obj || {}).flatMap(([k, v]) => isObj(v) ? flat(v, p + k + ".") : [[p + k, v]]);
  const draw = () => {
    nav.innerHTML = "";
    for (const g of groups) nav.append(el("button", { class: "navitem" + (g === group && !q ? " active" : ""), onclick: () => { group = g; q = ""; search.value = ""; draw(); } }, g, el("span", { class: "badge plain" }, isObj(data[g]) ? flat(data[g]).length : 1)));
    body.innerHTML = "";
    body.append(el("div", { class: "row", style: { marginBottom: "10px" } }, search, seg([["all", "All"], ["changed", "Changed"], ["empty", "Empty (off)"]], filter, (f) => { filter = f; draw(); })));
    const items = q ? flat(S.doc(L).data).filter(([k, v]) => (k + " " + v).toLowerCase().includes(q)) : isObj(data[group]) ? flat(data[group], group + ".") : [[group, data[group]]];
    const panel = el("div", { class: "panel" }, el("div", { class: "pb" }));
    let n = 0;
    for (const [k] of items) {
      const v = S.get(L, k), d = S.def(L, k);
      if (filter === "changed" && JSON.stringify(v) === JSON.stringify(d)) continue;
      if (filter === "empty" && v !== "") continue;
      if (++n > 150) break;
      panel.firstChild.append(field(L, k, { label: k, type: Array.isArray(v) ? "yaml" : "mini" }));
    }
    if (!n) panel.firstChild.append(el("div", { class: "empty" }, "Nothing here."));
    body.append(panel);
  };
  draw();
  split.append(nav, body);
  root.append(split);
}

// ============================================================================================
//  Resets & safety, and the per-file editor
// ============================================================================================
function safetyPage(root) {
  root.append(pageHead("Resets & safety", "Reset commands, the audit log, storage and general settings."));
  const sec = (t, f, base, skip) => el("div", { class: "panel", style: { marginBottom: "14px" } }, el("div", { class: "ph" }, el("h3", {}, t)), el("div", { class: "pb" }, allFields(f, base, { skip })));
  root.append(sec("Resets", "config.yml", "reset"), sec("Audit log", "config.yml", "audit", ["audit.types"]));
  const types = el("div", { class: "grid g3" });
  for (const [k, v] of Object.entries(C("audit.types") || {})) types.append(el("label", { class: "row", style: { flexWrap: "nowrap" } }, switchCtl(v !== false, (c) => S.set("config.yml", "audit.types." + k, c)), el("code", {}, k)));
  root.append(el("div", { class: "panel", style: { marginBottom: "14px" } }, el("div", { class: "ph" }, el("h3", {}, "Audit event types")), el("div", { class: "pb" }, types)),
    sec("General", "config.yml", "general"), sec("Storage", "config.yml", "storage"), sec("Small display settings", "config.yml", "gates"), sec("Menus", "config.yml", "gui"), sec("True/false text", "config.yml", "placeholders"), sec("Leaderboard", "config.yml", "top"));
}
function filesPage(root, params) {
  const name = params.join("/") || "config.yml";
  const d = S.doc(name);
  if (!d) { root.append(el("div", { class: "empty" }, "No file " + name)); return; }
  let mode = "options";
  root.append(pageHead(name, d.parsed.header.split("\n").slice(1, 3).join(" "), seg([["options", "Options"], ["text", "Text"]], mode, (m) => { mode = m; draw(); })));
  const body = el("div");
  root.append(body);
  const draw = () => {
    body.innerHTML = "";
    if (mode === "text" || !d.parsed.entries) {
      const ta = el("textarea", { spellcheck: "false", style: { minHeight: "72vh", fontSize: "12.5px" } });
      ta.value = S.doc(name).text;
      ta.addEventListener("input", () => S.setText(name, ta.value, name + "#raw"));
      body.append(el("details", { class: "more" }, el("summary", {}, "About this file"), el("pre", { class: "small muted", style: { whiteSpace: "pre-wrap" } }, d.parsed.header)), ta,
        S.doc(name).error ? el("div", { class: "note bad" }, S.doc(name).error) : null);
      return;
    }
    body.append(el("details", { class: "more" }, el("summary", {}, "About this file"), el("pre", { class: "small muted", style: { whiteSpace: "pre-wrap" } }, d.parsed.header)));
    for (const top of Object.keys({ ...(S.doc(name).defaults || {}), ...(S.doc(name).data || {}) })) {
      if (top === "config-version") continue;
      const v = S.get(name, top) ?? S.def(name, top);
      const help = d.parsed.index.get(top)?.help || d.defParsed.index.get(top)?.help || "";
      body.append(el("details", { class: "panel more", style: { padding: "0 16px", marginBottom: "10px" }, open: Object.keys(S.doc(name).data || {}).length < 6 },
        el("summary", {}, top, " ", el("span", { class: "small muted", style: { fontWeight: 400 } }, help.split("\n")[0])), isObj(v) ? allFields(name, top) : field(name, top)));
    }
  };
  draw();
}

export const setupPages2 = [
  { id: "shop", group: "Set up", title: "Shop", icon: ICONS.shop, render: shopPage, owns: ["shop.yml"] },
  { id: "titles", group: "Set up", title: "Titles", icon: ICONS.title, render: titlesPage, owns: ["titles.yml"] },
  { id: "trading", group: "Set up", title: "Trading", icon: ICONS.trade, render: tradingPage, owns: ["trade.yml"] },
  { id: "statuses", group: "Set up", title: "Statuses", icon: ICONS.status, render: statusesPage, owns: ["statuses.yml"] },
  { id: "integrations", group: "Set up", title: "Integrations", icon: ICONS.plug, render: integrationsPage, owns: ["integrations.yml"] },
  { id: "hooks", group: "Set up", title: "Hooks & actions", icon: ICONS.hook, render: hooksPage, owns: ["config.yml:hooks", "actions.yml"] },
  { id: "sidebar", group: "Set up", title: "Sidebar", icon: ICONS.sidebar, render: sidebarPage, owns: ["config.yml:sidebar"] },
  { id: "menus", group: "Set up", title: "Menus", icon: ICONS.menu, render: menusPage, owns: ["config.yml:history"] },
  { id: "messages", group: "Set up", title: "Messages", icon: ICONS.lang, render: messagesPage, owns: ["lang/en_US.yml"] },
  { id: "safety", group: "Set up", title: "Resets & safety", icon: ICONS.reset, render: safetyPage, owns: ["config.yml:reset", "config.yml:audit", "config.yml:general", "config.yml:storage", "config.yml:gates", "config.yml:gui", "config.yml:placeholders", "config.yml:top"] },
  { id: "files", group: "Tools", title: "Raw files", icon: ICONS.raw, render: filesPage, hidden: false },
];
