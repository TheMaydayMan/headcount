// Set up pages, part 2: Shop, Titles, Trading, Statuses, Integrations, Hooks, Action lists, Sidebar, Menus, Messages and General.
import { plain, el, btn, icon, iconBtn, field, group, subhead, allFields, mcText, miniInline, tooltip, chest, seg, select, switchCtl, chips, drawer, closeDrawer, toast,
  promptDialog, confirmDialog, hoverTip, sprite, SAMPLE, ICONS, humanize, toYamlText, fromYamlText, miniCtl, numberCtl, materialCtl, statusPicker, highlight, rowTools, emptyBox } from "./ui.js";
import { isObj, clone, expandSlots, compressSlots, renderMini, stripMini, parseDuration, formatDuration, prettyMaterial, setValue, moveEntry } from "./core.js";
import * as S from "./store.js";
import { actionList, editActions, summarize, listUsages, listNames, conditionsCtl, actionSprite, usageText, usagePlaces, renameList, freeListName } from "./actions.js";
import { pageHead, C, layout, preview, mcName } from "./pages-setup.js";
import { playSound } from "./sprites.js";

const go = (h, key) => window.studio.go(h, key);
const idCheck = (exists) => (v) => !/^[a-z0-9][a-z0-9_-]*$/i.test(v) ? "Use letters, numbers, dashes and underscores." : exists(v) ? "That ID is taken." : null;
const doneFooter = (...left) => el("div", { class: "row", style: { width: "100%" } }, ...left, el("span", { class: "grow" }), btn("Done", () => closeDrawer(), "primary"));
/** A free ID next to an existing one: steak-2 beside steak. */
export function freeId(base, taken) {
  if (!taken(base)) return base;
  const stem = base.replace(/-\d+$/, "");
  let n = 2;
  while (taken(stem + "-" + n)) n++;
  return stem + "-" + n;
}
/** Copies a map entry to a new key right after the original, as one undo step. */
export function duplicateEntry(file, path, newKey, change) {
  const parent = path.split(".").slice(0, -1).join(".");
  const to = (parent ? parent + "." : "") + newKey;
  const value = clone(S.get(file, path));
  change?.(value);
  S.setText(file, moveEntry(setValue(S.doc(file).text, to, value), to, path, false), null);
  return to;
}
/** Moves a map entry one place up or down among its siblings (order matters for tiers, titles and outcome rules). */
export function moveSibling(file, path, keys, from, to) {
  if (to < 0 || to >= keys.length || from === to) return;
  const parent = path.split(".").slice(0, -1).join(".");
  S.move(file, path, (parent ? parent + "." : "") + keys[to], to < from);
}
const dupBtn = (fn) => btn([icon("copy"), "Duplicate"], fn, "ghost");

// ============================================================================================
//  Menus: shared by the menu editor and every page that previews a menu
// ============================================================================================
function buttonItem(b, state) {
  if (!isObj(b)) return null;
  if (state && isObj(b[state])) return { ...b, ...b[state] };
  for (const st of ["unconfirmed", "idle", "owned"]) if (isObj(b[st])) return { ...b, ...b[st] };
  return b;
}
export function menuSlots(file, base) {
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
const isFiller = (fill, rows, i) => fill === "empty" ? true : fill === "border" ? (Math.floor(i / 9) === 0 || Math.floor(i / 9) === rows - 1 || i % 9 === 0 || i % 9 === 8) : fill === "none" ? false : fill.includes(i);
/** A chest preview of a menu. extra(i) can put items in slots (entries, offers). */
export function menuChest(file, base, { extra, vars, onButton, onEmpty, onDrop, paint, paintEnd, numbers, overlay, dragButtons = true } = {}) {
  const { L, rows, buttons, fill } = menuSlots(file, base);
  const filler = isObj(L.filler) ? L.filler : { material: "GRAY_STAINED_GLASS_PANE" };
  return chest(rows, L.title ?? "", (i) => {
    const o = overlay?.(i) || "";
    const x = extra?.(i);
    if (x) return { ...x, cls: (x.cls || "") + " " + o };
    if (buttons[i]) { const { id, b } = buttons[i]; return { item: b, title: id, cls: o, tip: () => tooltip(b.name ?? id, b.lore, vars), onclick: () => onButton?.(id, i), draggable: !!onDrop && dragButtons && b.slot !== undefined }; }
    if (isFiller(fill, rows, i) && !(fill === "empty" && extra)) return { item: filler, cls: "dim " + o, onclick: () => onEmpty?.(i) };
    return { cls: o, onclick: () => onEmpty?.(i) };
  }, { vars, onDrop, paint, paintEnd, numbers });
}

const MENUS = [
  { id: "vault", name: "Vault", file: "config.yml", base: "vault.gui", icon: "ender_chest", vars: { owner: "Steve", unique: 7, total: 21, size: 27, used: 21, score: 4, page: 1, pages: 1 } },
  { id: "history", name: "History", file: "config.yml", base: "history.gui", icon: "book", areas: [["entry-slots", "Cycles", "paint-area"]], vars: { player: "Steve", page: 1, pages: 2 } },
  { id: "shop", name: "Shop", file: "shop.yml", base: "gui", icon: "emerald", vars: { tier: "<white>Common", points: 12, tier_number: 1, tiers: 4, last_score: 6, target_tier: "Uncommon", threshold: 3, needed: 0 } },
  { id: "confirm", name: "Purchase", file: "shop.yml", base: "confirm-gui", icon: "lime_concrete", vars: { name: "Speed for 30 minutes", price: 3, points: 12 } },
  { id: "trade", name: "Trade", file: "trade.yml", base: "gui", icon: "bundle", areas: [["own-slots", "Your offer", "paint-own"], ["other-slots", "Their offer", "paint-other"]], vars: { other: "Alex", my_heads: 2, max_heads: 24, my_items: 1, max_items: 24, their_heads: 1, their_items: 0, seconds: 3 } },
  { id: "titles", name: "Titles", file: "titles.yml", base: "gui", icon: "name_tag", areas: [["title-slots", "Titles", "paint-area"]], vars: { page: 1, pages: 1 } },
];
const BUTTON_STATES = { confirm: ["unconfirmed", "confirmed"], "other-status": ["unconfirmed", "confirmed"], countdown: ["idle", "running"] };

function menusPage(root, params) {
  const m = MENUS.find((x) => x.id === params[0]) || MENUS[0];
  root.append(pageHead("Menus"));
  root.append(el("div", { class: "tabs", role: "tablist" }, MENUS.map((x) => el("button", { role: "tab", class: x.id === m.id ? "on" : "", onclick: () => go("#/menus/" + x.id) }, sprite(x.icon, 18), x.name))));
  let tool = "select", selected = params[1] || null, numbers = false;
  const left = el("div");
  const aside = el("div", { style: { display: "grid", gap: "22px" } });
  const { file, base } = m;
  const drawChest = () => {
    left.innerHTML = "";
    const { fill } = menuSlots(file, base);
    const tools = [["select", "Buttons"], ["filler", "Filler"], ...(m.areas || []).map(([k, n]) => [k, n])];
    const areaSets = Object.fromEntries((m.areas || []).map(([k]) => [k, new Set(expandSlots(S.get(file, base + "." + k) ?? S.def(file, base + "." + k), 54) === "empty" ? [] : expandSlots(S.get(file, base + "." + k) ?? S.def(file, base + "." + k), 54))]));
    const overlay = (i) => (m.areas || []).filter(([k]) => areaSets[k].has(i)).map(([, , c]) => c).join(" ") + (selected && menuSlots(file, base).buttons[i]?.id === selected ? " sel" : "");
    let paintTo = null;
    const paint = tool === "select" ? null : (i, cell, cont) => {
      if (tool === "filler") {
        const cur = menuSlots(file, base).fill;
        if (!Array.isArray(cur)) { toast("The filler covers " + (cur === "empty" ? "every empty slot" : cur === "border" ? "the border" : "nothing") + ". Pick \"Painted slots\" under Filler to paint it."); return; }
        const set = new Set(cur);
        if (!cont) paintTo = !set.has(i);
        paintTo ? set.add(i) : set.delete(i);
        S.set(file, base + ".filler-slots", compressSlots([...set]), file + base + "#paint");
      } else {
        const set = areaSets[tool];
        if (!cont) paintTo = !set.has(i);
        paintTo ? set.add(i) : set.delete(i);
        S.set(file, base + "." + tool, compressSlots([...set]), file + base + "#paint" + tool);
      }
      drawChest();
    };
    const extra = (i) => {
      if (m.id === "trade") { if (areaSets["own-slots"].has(i) && i === [...areaSets["own-slots"]].sort((a, b) => a - b)[0]) return { item: "PLAYER_HEAD", count: 2 }; if (areaSets["other-slots"].has(i) && i === [...areaSets["other-slots"]].sort((a, b) => a - b)[0]) return { item: "DIAMOND", count: 3 }; if (areaSets["own-slots"].has(i) || areaSets["other-slots"].has(i)) return { onclick: () => {} }; }
      if (m.id === "titles" && areaSets["title-slots"].has(i)) { const t = Object.entries(S.get("titles.yml", "titles") || {})[[...areaSets["title-slots"]].sort((a, b) => a - b).indexOf(i)]; return t ? { item: t[1]?.icon || "NAME_TAG", tip: () => tooltip(t[1]?.display, t[1]?.description) } : { onclick: () => {} }; }
      if (m.id === "history" && areaSets["entry-slots"].has(i)) { const k = [...areaSets["entry-slots"]].sort((a, b) => a - b).indexOf(i); const e = S.get(file, base + ".entry") || {}; return k < 3 ? { item: { ...e, ...(e[["positive", "negative", "zero"][k]] || {}) }, tip: () => tooltip(e.name, e.lore, { cycle: 3 - k, ended: "2026-11-01", unique: 7, score: [4, -2, 0][k], rank: 2, earned: 4, spent: 2, streak: 1, outcomes: "none" }) } : { onclick: () => {} }; }
      if (m.id === "confirm" && menuSlots(file, base).buttons[i]?.id === "display") return { item: "SUGAR", tip: () => tooltip("<aqua>Speed for 30 minutes", []), onclick: () => pick(i) };
      return null;
    };
    const pick = (i) => { selected = menuSlots(file, base).buttons[i]?.id || null; drawChest(); drawAside(); };
    left.append(el("div", { class: "row", style: { marginBottom: "12px" } }, el("span", { class: "muted" }, "Click to"), seg(tools.map(([k, n]) => [k, k === "select" ? "Pick buttons" : "Paint " + n.toLowerCase()]), tool, (v) => { tool = v; drawChest(); }),
      el("span", { class: "grow" }), el("label", { class: "switchrow" }, switchCtl(numbers, (v) => { numbers = v; drawChest(); }, "Slot numbers"), "Slot numbers")),
      menuChest(file, base, { vars: m.vars, extra, overlay, numbers, paint, paintEnd: () => drawAside(),
        onButton: (id) => { selected = id; drawChest(); drawAside(); },
        onEmpty: () => { selected = null; drawChest(); drawAside(); },
        onDrop: tool === "select" ? (from, to) => {
          const { buttons: bs } = menuSlots(file, base);
          const b = bs[from], other = bs[to];
          if (!b) return;
          if (other && S.get(file, base + ".buttons." + other.id + ".slot") === undefined) { toast(humanize(other.id) + " covers several slots, so it can't swap places. Change its slots instead."); return; }
          const g = "drag" + Date.now();
          S.set(file, base + ".buttons." + b.id + ".slot", to, g);
          if (other && other.id !== b.id) S.set(file, base + ".buttons." + other.id + ".slot", from, g);
          selected = b.id; drawChest(); drawAside();
        } : null }),
      el("div", { class: "small faint", style: { marginTop: "8px" } }, tool === "select" ? "Drag a button to move it." : "Drag across slots to paint them."));
    const btns = el("div", { class: "items", style: { marginTop: "22px" } });
    for (const [id, b] of Object.entries(S.get(file, base + ".buttons") || {})) {
      const it = buttonItem(b);
      btns.append(el("div", { class: "item" + (id === selected ? " sel" : "") + (b?.enabled === false ? " off" : ""), onclick: () => { selected = id; drawChest(); drawAside(); } }, it?.material ? sprite(it, 28) : sprite("structure_void", 28),
        el("div", { class: "main" }, el("div", { class: "t1" }, humanize(id)), el("div", { class: "t2" }, plain(it?.name ?? "") || "Shows the item for sale")),
        el("div", { class: "end" }, b?.slots ? "Slots " + [].concat(b.slots).join(", ") : b?.slot !== undefined ? "Slot " + b.slot : "", b?.enabled === false ? el("div", {}, "Hidden") : null)));
    }
    left.append(el("section", { class: "group", style: { marginTop: "26px" } }, el("header", {}, el("h2", {}, "Buttons")), btns));
  };
  const drawAside = () => {
    aside.innerHTML = "";
    if (selected && S.get(file, base + ".buttons." + selected) !== undefined) aside.append(buttonEditor(file, base + ".buttons." + selected, selected, () => drawChest()));
    const L = S.get(file, base) || {};
    const mode = Array.isArray(L["filler-slots"]) ? "list" : L["filler-slots"] ?? "empty";
    aside.append(group("Menu", field(file, base + ".title", { vars: m.vars, onChange: drawChest }), field(file, base + ".rows", { onChange: () => { drawChest(); } }),
      el("div", { class: "field", "data-key": file + "#" + base + ".filler-slots" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Filler")), el("div", { class: "ctl" },
        seg([["list", "Painted slots"], ["empty", "Empty slots"], ["border", "Border"], ["none", "None"]], mode, (v) => { S.set(file, base + ".filler-slots", v === "list" ? [] : v); if (v === "list") tool = "filler"; drawChest(); drawAside(); }))),
      field(file, base + ".filler.material", { label: "Filler item", onChange: drawChest }), field(file, base + ".filler.name", { label: "Filler name" })));
    if (m.id === "vault") aside.append(group("Score in the title", field(file, base + ".score-colors.positive", { onChange: drawChest }), field(file, base + ".score-colors.negative", { onChange: drawChest }), field(file, base + ".score-colors.zero", { onChange: drawChest })),
      group("Slots beyond the vault size", field(file, base + ".locked-slot.material", { onChange: drawChest }), field(file, base + ".locked-slot.name")));
    if (m.id === "history") aside.append(group("Each cycle", field(file, base + ".entry.material", { onChange: drawChest }), field(file, base + ".entry.name", { onChange: drawChest, vars: { cycle: 3 } }), field(file, base + ".entry.lore", { vars: { cycle: 3, ended: "2026-11-01", unique: 7, score: 4, rank: 2, earned: 4, spent: 2, streak: 1, outcomes: "none" } }),
      field(file, base + ".entry.positive.material", { label: "Positive score", onChange: drawChest }), field(file, base + ".entry.negative.material", { label: "Negative score", onChange: drawChest }), field(file, base + ".entry.zero.material", { label: "Zero", onChange: drawChest }),
      field(file, base + ".entry.inactive.material", { label: "Not active", onChange: drawChest }), field(file, base + ".entry.inactive.lore", { label: "Not active lore" })));
    if (m.id === "titles") aside.append(titleItemEditor(drawChest));
    const known = ["title", "rows", "filler", "filler-slots", "buttons", "score-colors", "locked-slot", "entry", "entry-slots", "own-slots", "other-slots", "title-slots", "title-item"];
    const rest = allFields(file, base, { skip: known.map((k) => base + "." + k) });
    if (rest.children.length) aside.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Other")), rest));
  };
  drawChest(); drawAside();
  root.append(layout(left, aside, true));
}
function buttonEditor(file, base, id, redraw) {
  const b = S.get(file, base) || {};
  const states = BUTTON_STATES[id]?.filter((s) => isObj(b[s])) || [];
  let state = states[0] || "";
  const body = el("div", { class: "sheet" });
  const draw = () => {
    body.innerHTML = "";
    body.append(field(file, base + ".enabled", { label: "Shown", type: "bool", onChange: redraw }));
    if (Array.isArray(b.slots)) body.append(field(file, base + ".slots", { label: "Slots", type: "list", onChange: redraw }));
    else if (b.slot !== undefined) body.append(field(file, base + ".slot", { onChange: redraw }));
    const ib = state ? base + "." + state : base;
    if (id === "display" && !b.material) { body.append(el("div", { class: "pad", style: { padding: "12px 18px" } }, el("span", { class: "muted" }, "Shows the item being bought."))); return; }
    body.append(field(file, ib + ".material", { onChange: () => { redraw(); draw(); } }), field(file, ib + ".name", { onChange: redraw }), field(file, ib + ".lore"));
    const mat = S.get(file, ib + ".material") ?? b.material;
    if (String(mat).toUpperCase() === "PLAYER_HEAD") body.append(field(file, ib + ".head", { hint: "Use viewer for the player's own head." }));
    body.append(field(file, ib + ".glint", { onChange: redraw }));
  };
  draw();
  return el("section", { class: "group" }, el("header", {}, el("h2", {}, humanize(id)), states.length ? el("div", { class: "side" }, seg(states.map((s) => [s, humanize(s)]), state, (v) => { state = v; draw(); })) : null), body);
}
function titleItemEditor(redraw) {
  const base = "gui.title-item";
  let state = "owned";
  const body = el("div", { class: "sheet" });
  const draw = () => {
    body.innerHTML = "";
    body.append(field("titles.yml", "gui.show-unowned"), field("titles.yml", base + ".material", { label: "Item", onChange: redraw }), field("titles.yml", base + ".name", { label: "Name", vars: { title: "<gold>[Champion]" } }));
    body.append(subhead(humanize(state) + " titles"), field("titles.yml", base + "." + state + ".material", { label: "Item instead" }), field("titles.yml", base + "." + state + ".glint"), field("titles.yml", base + "." + state + ".lore"));
  };
  draw();
  return el("section", { class: "group" }, el("header", {}, el("h2", {}, "Each title"), el("div", { class: "side" }, seg([["owned", "Owned"], ["equipped", "Equipped"], ["forced", "Forced"], ["locked", "Locked"]], state, (v) => { state = v; draw(); }))), body);
}
const layoutBtn = (id) => btn([sprite("chest", 18), "Menu layout"], () => go("#/menus/" + id), "ghost");

// ============================================================================================
//  Shop
// ============================================================================================
const STATES = [["affordable", "Can buy"], ["too-expensive", "Too expensive"], ["sold-out", "Sold out"], ["limit-reached", "Limit reached"], ["cooldown", "Cooling down"], ["unavailable", "Requirement not met"], ["locked", "Tier locked"]];
function shopPage(root, params) {
  const tiers = () => Object.keys(S.get("shop.yml", "tiers") || {});
  let tier = params[0] && tiers().includes(params[0]) ? params[0] : tiers()[0];
  let page = 1, state = "affordable";
  const newTier = async () => {
    const id = await promptDialog("New tier", "A short ID, like epic:", "epic", { check: idCheck((v) => tiers().includes(v)) });
    if (!id) return;
    // The next step up from the highest tier, keeping the same spacing (3, 6, 9 gives 12).
    const steps = Object.values(S.get("shop.yml", "tiers") || {}).map((T) => +T?.threshold).filter((n) => isFinite(n)).sort((a, b) => a - b);
    const gap = steps.length > 1 ? steps[steps.length - 1] - steps[steps.length - 2] || 3 : 3;
    S.set("shop.yml", "tiers." + id, { name: "<light_purple>" + humanize(id), threshold: steps.length ? steps[steps.length - 1] + gap : 3, entries: {} });
    tier = id; draw();
  };
  root.append(pageHead("Shop", null, layoutBtn("shop")));
  const tabs = el("div", { class: "tabs", role: "tablist" });
  const body = el("div");
  root.append(tabs, body);
  const thr = (T) => (T?.threshold === "none" || T?.threshold === undefined ? "Always open" : "Score " + T.threshold + " or more");
  const draw = () => {
    tabs.innerHTML = "";
    for (const t of tiers()) {
      const T = S.get("shop.yml", "tiers." + t) || {};
      const first = Object.values(T.entries || {})[0];
      tabs.append(el("button", { role: "tab", class: t === tier ? "on" : "", onclick: () => { tier = t; page = 1; draw(); } }, first?.display ? sprite(first.display, 20) : null, stripMini(T.name || t), el("small", {}, thr(T))));
    }
    tabs.append(el("button", { onclick: newTier, title: "New tier" }, icon("plus"), "Tier"));
    body.innerHTML = "";
    if (!tier) { body.append(el("div", { class: "empty" }, "No tiers yet. ", btn("Add one", newTier, "primary"))); return; }
    const base = "tiers." + tier;
    const T = S.get("shop.yml", base) || {};
    const entries = Object.entries(T.entries || {});
    const pages = Math.max(+T.pages || 1, ...entries.map(([, e]) => +e?.page || 1));
    const { size } = menuSlots("shop.yml", "gui");
    const at = {};
    for (const [id, e] of entries) if ((+e?.page || 1) === page) at[+e?.slot || 0] = [id, e];
    const ds = S.get("shop.yml", "default-states") || {};
    const vars = (e) => ({ name: e?.name || stripMini(e?.display?.name || ""), price: e?.price ?? 0, points: 12, missing: Math.max(0, (e?.price ?? 0) - 12), stock: e?.stock === undefined || e?.stock < 0 ? "unlimited" : e.stock,
      purchases_left: e?.["limit-per-cycle"] ? e["limit-per-cycle"] : "unlimited", limit: e?.["limit-per-cycle"] ?? "unlimited", bought: 0, cooldown: e?.cooldown ?? "0s", tier: T.name, threshold: T.threshold, tier_number: tiers().indexOf(tier) + 1, tiers: tiers().length, page, pages, last_score: 6, needed: 0 });
    const look = (e) => {
      const o = { ...(isObj(e?.display) ? e.display : {}), ...(isObj(ds[state]) ? ds[state] : {}), ...(isObj(e?.["display-states"]?.[state]) ? e["display-states"][state] : {}) };
      o.lore = [...[].concat(e?.display?.lore || []), ...[].concat((isObj(e?.["display-states"]?.[state]) && e["display-states"][state].lore) || ds[state]?.lore || [])];
      o.material ||= e?.display?.material || "PAPER";
      return o;
    };
    const grid = menuChest("shop.yml", "gui", { vars: vars({}),
      extra: (i) => { const hit = at[i]; if (!hit) return null; const [id, e] = hit; const L = look(e); return { item: L, draggable: true, title: id, tip: () => tooltip(L.name ?? id, L.lore, vars(e)), onclick: () => editEntry(id), cls: e?.enabled === false ? "dim" : "" }; },
      onEmpty: (i) => addEntry(i), onButton: () => go("#/menus/shop"), dragButtons: false,
      onDrop: (from, to) => { const a = at[from]; if (!a) return; const b = at[to];
        if (!b && menuSlots("shop.yml", "gui").buttons[to]) { toast("That slot holds the menu's " + humanize(menuSlots("shop.yml", "gui").buttons[to].id) + " button. Pick a free slot."); return; } const g = "drag" + Date.now(); S.set("shop.yml", `${base}.entries.${a[0]}.slot`, to, g); if (b) S.set("shop.yml", `${base}.entries.${b[0]}.slot`, from, g); draw(); } });
    const left = el("div", {},
      el("div", { class: "row", style: { marginBottom: "12px" } }, el("span", { class: "muted" }, "Show items as"), select(STATES, state, (v) => { state = v; draw(); }), el("span", { class: "grow" }),
        pages > 1 ? el("span", { class: "row gap-s" }, el("span", { class: "muted" }, "Page"), seg([...Array(pages).keys()].map((n) => [n + 1, String(n + 1)]), page, (n) => { page = +n; draw(); })) : null,
        btn([icon("plus"), "Page"], () => { S.set("shop.yml", base + ".pages", pages + 1); page = pages + 1; draw(); }, "ghost small")),
      grid, el("div", { class: "small faint", style: { marginTop: "8px" } }, "Drag items to move them. Click an empty slot to add one."));
    const list = el("div", { class: "items" });
    for (const [id, e] of entries) list.append(el("div", { class: "item" + (e?.enabled === false ? " off" : ""), onclick: () => editEntry(id) }, sprite(e?.display || "PAPER", 28),
      el("div", { class: "main" }, el("div", { class: "t1" }, stripMini(e?.display?.name || id)), el("div", { class: "t2" }, (Array.isArray(e?.actions) ? e.actions : e?.actions ? [e.actions] : []).map(summarize).join(", "))),
      el("div", { class: "end" }, (e?.price ?? 0) + (+(e?.price ?? 0) === 1 ? " point" : " points"), el("div", {}, "Page " + (e?.page || 1) + ", slot " + (e?.slot ?? 0)))));
    list.append(el("button", { class: "addrow", onclick: () => { const free = [...Array(size).keys()].find((i) => !at[i] && !menuSlots("shop.yml", "gui").buttons[i] && !isFiller(menuSlots("shop.yml", "gui").fill, menuSlots("shop.yml", "gui").rows, i)); addEntry(free ?? 0); } }, icon("plus"), "New item"));
    left.append(el("section", { class: "group", style: { marginTop: "26px" } }, el("header", {}, el("h2", {}, "Items in " + stripMini(T.name || tier))), list));
    const aside = el("div", { style: { display: "grid", gap: "4px" } },
      group(["Tier", rowTools(tiers().indexOf(tier), tiers().length, { horizontal: true, what: "this tier", move: (from, to) => { moveSibling("shop.yml", base, tiers(), from, to); draw(); } }), iconBtn("trash", "Delete this tier", async () => { if (await confirmDialog("Delete the " + stripMini(T.name || tier) + " tier?", entries.length ? `Its ${entries.length === 1 ? "item goes" : entries.length + " items go"} with it.` : null, { ok: "Delete", danger: true })) { S.remove("shop.yml", base); tier = tiers()[0]; draw(); } }, "sm")],
        field("shop.yml", base + ".name", { label: "Name", onChange: draw }), thresholdRow(base, draw), field("shop.yml", base + ".enabled", { label: "Open", type: "bool" })),
      group("Shop", field("shop.yml", "settings.confirm-at-price"), field("shop.yml", "settings.new-player-score"), field("shop.yml", "settings.stock-reset")),
      statesEditor());
    body.append(layout(left, aside, true));
  };
  const addEntry = async (slot) => {
    const id = await promptDialog("New shop item", "A short ID, like golden-apple:", "new-item", { check: idCheck((v) => S.get("shop.yml", `tiers.${tier}.entries.${v}`)) });
    if (!id) return;
    S.set("shop.yml", `tiers.${tier}.entries.${id}`, { page, slot, price: 5, display: { material: "PAPER", name: "<white>" + humanize(id) }, actions: [{ message: "<green>Thanks for buying!" }] });
    draw(); editEntry(id);
  };
  const editEntry = (id, t = tier) => entryEditor(t, id, draw);
  draw();
  if (params[1] && S.get("shop.yml", `tiers.${tier}.entries.${params[1]}`)) editEntry(params[1]);
}
function thresholdRow(base, redraw) {
  const v = S.get("shop.yml", base + ".threshold");
  const always = v === undefined || v === "none";
  const row = el("div", { class: "field", "data-key": "shop.yml#" + base + ".threshold" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Opens for")),
    el("div", { class: "ctl" }, seg([["none", "Everyone"], ["score", "By score"]], always ? "none" : "score", (m) => { S.set("shop.yml", base + ".threshold", m === "none" ? "none" : 10); redraw(); }),
      always ? null : numberCtl(+v, (n) => S.set("shop.yml", base + ".threshold", n), { unit: "or more", step: 1 })));
  row.classList.toggle("unsaved", JSON.stringify(v) !== JSON.stringify(S.savedValue("shop.yml", base + ".threshold")));
  return row;
}
function statesEditor() {
  let st = "affordable";
  const body = el("div", { class: "sheet" });
  const draw = () => {
    body.innerHTML = "";
    body.append(field("shop.yml", "default-states." + st + ".material", { label: "Item instead", hint: "Leave it empty to keep the item's own." }), field("shop.yml", "default-states." + st + ".glint"),
      field("shop.yml", "default-states." + st + ".lore", { label: "Lore added", vars: { price: 3, stock: "unlimited", purchases_left: 5, missing: 2, limit: 5, cooldown: "2h", threshold: 10 } }));
  };
  draw();
  return el("section", { class: "group" }, el("header", {}, el("h2", {}, "Item lore by state")), el("div", { style: { marginBottom: "10px" } }, select(STATES, st, (v) => { st = v; draw(); })), body);
}
function entryEditor(tier, id, redraw) {
  const base = `tiers.${tier}.entries.${id}`;
  const titles = Object.keys(S.get("titles.yml", "titles") || {});
  const allEntries = Object.entries(S.get("shop.yml", "tiers") || {}).flatMap(([, T]) => Object.keys(T?.entries || {}));
  const prev = el("div");
  const drawPrev = () => {
    const e = S.get("shop.yml", base) || {};
    prev.innerHTML = "";
    prev.append(el("div", { class: "mcscene", style: { minHeight: "0", gap: "14px", alignItems: "flex-start", marginBottom: "20px" } }, sprite(e.display || "PAPER", 48),
      tooltip(e.display?.name ?? id, [...[].concat(e.display?.lore || []), ...[].concat(S.get("shop.yml", "default-states.affordable.lore") || [])], { price: e.price ?? 0, stock: e.stock === undefined || e.stock < 0 ? "unlimited" : e.stock, purchases_left: e["limit-per-cycle"] || "unlimited" })));
  };
  drawPrev();
  const e0 = S.get("shop.yml", base) || {};
  const stockRow = el("div", { class: "field", "data-key": "shop.yml#" + base + ".stock" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Stock"), el("div", { class: "hint" }, "Shared by everyone.")),
    el("div", { class: "ctl" }, el("div", { class: "row" }, seg([["inf", "Unlimited"], ["n", "Limited"]], e0.stock === undefined || e0.stock < 0 ? "inf" : "n", (m) => { S.set("shop.yml", base + ".stock", m === "inf" ? (e0.stock === undefined ? undefined : -1) : 10); entryEditor(tier, id, redraw); }),
      e0.stock !== undefined && e0.stock >= 0 ? numberCtl(e0.stock, (n) => S.set("shop.yml", base + ".stock", n), { min: 0, step: 1 }) : null)));
  const limitRow = el("div", { class: "field", "data-key": "shop.yml#" + base + ".limit-per-cycle" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Per player each cycle")),
    el("div", { class: "ctl" }, el("div", { class: "row" }, seg([["0", "No limit"], ["n", "Up to"]], e0["limit-per-cycle"] ? "n" : "0", (m) => { S.set("shop.yml", base + ".limit-per-cycle", m === "0" ? (e0["limit-per-cycle"] === undefined ? undefined : 0) : 1); entryEditor(tier, id, redraw); }),
      e0["limit-per-cycle"] ? numberCtl(e0["limit-per-cycle"], (n) => S.set("shop.yml", base + ".limit-per-cycle", n), { min: 1, step: 1 }) : null)));
  const confirmV = e0.confirm === undefined ? "" : String(e0.confirm);
  const body = el("div", {}, prev,
    group("Price", field("shop.yml", base + ".price", { label: "Price", type: "number", unit: "points", min: 0, step: 1, onChange: drawPrev }), limitRow, stockRow,
      field("shop.yml", base + ".cooldown", { label: "Wait between purchases", type: "duration", zero: "None" }),
      el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Ask to confirm")), el("div", { class: "ctl" }, seg([["", "From the shop's price"], ["true", "Always"], ["false", "Never"]], confirmV, (v) => S.set("shop.yml", base + ".confirm", v === "" ? undefined : v === "true"))))),
    group("Item", field("shop.yml", base + ".display.material", { onChange: () => { drawPrev(); entryEditor(tier, id, redraw); } }), field("shop.yml", base + ".display.name", { onChange: drawPrev, vars: { price: e0.price } }),
      field("shop.yml", base + ".display.lore", { onChange: drawPrev }), field("shop.yml", base + ".display.glint", { onChange: drawPrev }),
      String(e0.display?.material).toUpperCase() === "PLAYER_HEAD" ? field("shop.yml", base + ".display.head", { hint: "Use viewer for the buyer's own head." }) : null,
      field("shop.yml", base + ".name", { label: "Name in messages", type: "text", placeholder: stripMini(e0.display?.name || id) }), field("shop.yml", base + ".enabled", { label: "For sale", type: "bool" })),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "What buying it does")), actionList("shop.yml", base + ".actions", { vars: { price: e0.price } })),
    group("Who can buy it", field("shop.yml", base + ".requires.titles", { label: "Owns these titles", type: "list", suggestions: titles }), field("shop.yml", base + ".requires.purchases", { label: "Bought before", type: "list", suggestions: allEntries }),
      field("shop.yml", base + ".requires.purchases-this-cycle", { label: "Bought this cycle", type: "list", suggestions: allEntries }), field("shop.yml", base + ".requires.permissions", { label: "Has permissions", type: "list" }),
      el("div", { class: "field stack" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Only when")), el("div", { class: "ctl" }, conditionsCtl("shop.yml", base + ".conditions")))),
    group("Place", field("shop.yml", base + ".page", { label: "Page", type: "number", min: 1, step: 1 }), field("shop.yml", base + ".slot", { onChange: redraw })));
  const rest = allFields("shop.yml", base, { skip: ["actions", "display.material", "display.name", "display.lore", "display.glint", "display.head", "requires", "price", "limit-per-cycle", "stock", "cooldown", "confirm", "conditions", "page", "slot", "name", "enabled"].map((k) => base + "." + k) });
  if (rest.children.length) body.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Other settings")), rest));
  drawer(stripMini(e0.display?.name || id), body, { subtitle: stripMini(S.get("shop.yml", "tiers." + tier + ".name") || humanize(tier)) + " tier", wide: true, onClose: redraw, hash: "#/shop/" + tier + "/" + id,
    footer: doneFooter(
      btn([icon("edit"), "Rename"], async () => { const n = await promptDialog("Rename", "New ID:", id, { check: idCheck(() => false) }); if (n && n !== id) { S.rename("shop.yml", base, n); entryEditor(tier, n, redraw); } }, "ghost"),
      dupBtn(() => {
        // The copy goes in the next free slot on the same page, so it doesn't sit on top of the original.
        const T = S.get("shop.yml", "tiers." + tier) || {};
        const taken = new Set(Object.values(T.entries || {}).filter((x) => (+x?.page || 1) === (+e0.page || 1)).map((x) => +x?.slot || 0));
        const { size, buttons, fill, rows } = menuSlots("shop.yml", "gui");
        const slot = [...Array(size).keys()].find((i) => !taken.has(i) && !buttons[i] && !isFiller(fill, rows, i));
        const nid = freeId(id, (v) => allEntries.includes(v));
        duplicateEntry("shop.yml", base, nid, (v) => { if (slot !== undefined && isObj(v)) v.slot = slot; });
        toast("Made a copy, " + nid + (slot === undefined ? ". This page has no free slot, so it shares one with the original." : "."));
        entryEditor(tier, nid, redraw);
      }),
      btn([icon("trash"), "Delete"], async () => { if (await confirmDialog("Delete " + stripMini(e0.display?.name || id) + "?", null, { ok: "Delete", danger: true })) { S.remove("shop.yml", base); closeDrawer(); } }, "ghost danger")) });
}

// ============================================================================================
//  Titles
// ============================================================================================
function titlesPage(root, params) {
  const add = async () => {
    const id = await promptDialog("New title", "A short ID, like hero:", "hero", { check: idCheck((v) => S.get("titles.yml", "titles." + v)) });
    if (!id) return;
    S.set("titles.yml", "titles." + id, { display: "<aqua>[" + humanize(id) + "]", description: ["<gray>How to earn it."], icon: { material: "NAME_TAG" } });
    draw(); edit(id);
  };
  root.append(pageHead("Titles", null, layoutBtn("titles")));
  const listBox = el("div");
  const chatBox = el("div");
  const drawChat = () => {
    chatBox.innerHTML = "";
    const fmt = S.get("titles.yml", "settings.format") ?? "<title> ";
    const first = Object.values(S.get("titles.yml", "titles") || {})[0];
    chatBox.append(preview("In chat", el("div", { class: "mcscene", style: { minHeight: "120px", alignItems: "flex-end" } }, el("div", { style: { display: "grid", gap: "2px" } },
      el("div", { class: "mc chatline", html: renderMini(fmt.replace("<title>", first?.display || "") + "<white>Steve<gray>: anyone want to trade?", { vars: SAMPLE }) }),
      el("div", { class: "mc chatline", html: renderMini((S.get("titles.yml", "settings.none-text") || "") + "<white>Alex<gray>: sure", { vars: SAMPLE }) })))));
  };
  const draw = () => {
    listBox.innerHTML = "";
    const items = el("div", { class: "items" });
    // The titles menu lists them in this order, so they can be moved.
    const ids = Object.keys(S.get("titles.yml", "titles") || {});
    ids.forEach((id, i) => { const t = S.get("titles.yml", "titles." + id); items.append(el("div", { class: "item" + (t?.enabled === false ? " off" : ""), onclick: () => edit(id) }, sprite(t?.icon || "NAME_TAG", 28),
      el("div", { class: "main" }, el("div", { class: "t1" }, mcName(t?.display || id)), el("div", { class: "t2" }, [].concat(t?.description || []).map(stripMini).join(" "))), t?.enabled === false ? el("div", { class: "end" }, "Not in use") : null,
      rowTools(i, ids.length, { what: "title", move: (from, to) => { moveSibling("titles.yml", "titles." + id, ids, from, to); draw(); } }))); });
    items.append(el("button", { class: "addrow", onclick: add }, icon("plus"), "New title"));
    listBox.append(items);
    drawChat();
  };
  const edit = (id) => {
    const base = "titles." + id;
    const prev = el("div");
    const drawPrev = () => { const t = S.get("titles.yml", base) || {}; prev.innerHTML = ""; prev.append(el("div", { class: "mcscene", style: { minHeight: "0", gap: "14px", alignItems: "flex-start", marginBottom: "20px" } }, sprite(t.icon || "NAME_TAG", 48), tooltip(t.display || id, t.description))); };
    drawPrev();
    drawer(stripMini(S.get("titles.yml", base + ".display") || id), el("div", {}, prev, group(null, field("titles.yml", base + ".display", { onChange: () => { drawPrev(); draw(); } }), field("titles.yml", base + ".description", { label: "Description", onChange: drawPrev }),
      field("titles.yml", base + ".icon.material", { label: "Menu item", onChange: drawPrev }), field("titles.yml", base + ".icon.glint", { onChange: drawPrev }), field("titles.yml", base + ".enabled", { label: "In use", type: "bool" }))),
    { onClose: draw, hash: "#/titles/" + id, footer: doneFooter(dupBtn(() => { const n = freeId(id, (v) => S.get("titles.yml", "titles." + v) !== undefined); duplicateEntry("titles.yml", base, n); toast("Made a copy, " + n + "."); edit(n); }), btn([icon("trash"), "Delete"], async () => { if (await confirmDialog("Delete " + id + "?", "Players who have it keep it in the database, but it stops showing anywhere.", { ok: "Delete", danger: true })) { S.remove("titles.yml", base); closeDrawer(); } }, "ghost danger")) });
  };
  draw();
  root.append(layout([el("section", { class: "group" }, listBox),
    group("Showing titles", field("titles.yml", "settings.format", { onChange: drawChat, vars: { title: "<gold>[Champion]" } }), field("titles.yml", "settings.none-text", { onChange: drawChat }), field("titles.yml", "settings.auto-equip-first")),
    group("Prefix in your permissions plugin", field("titles.yml", "settings.permission-prefix.enabled"), field("titles.yml", "settings.permission-prefix.type"), field("titles.yml", "settings.permission-prefix.priority", { hint: "Pick one no other prefix uses." }), field("titles.yml", "settings.permission-prefix.format"))],
  chatBox));
  if (params[0] && S.get("titles.yml", "titles." + params[0])) edit(params[0]);
}

// ============================================================================================
//  Trading
// ============================================================================================
function tradingPage(root) {
  root.append(pageHead("Trading", null, layoutBtn("trade")));
  const r = S.get("trade.yml", "range.blocks") ?? -1;
  const rangeRow = el("div", { class: "field", "data-key": "trade.yml#range.blocks" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Distance")),
    el("div", { class: "ctl" }, el("div", { class: "row" }, seg([["any", "Any"], ["n", "Within"]], r < 0 ? "any" : "n", (m) => { S.set("trade.yml", "range.blocks", m === "any" ? -1 : 32); window.studio.refresh(); }),
      r >= 0 ? numberCtl(r, (n) => S.set("trade.yml", "range.blocks", n), { unit: "blocks", min: 0, step: 1 }) : null)));
  rangeRow.classList.toggle("unsaved", r !== S.savedValue("trade.yml", "range.blocks"));
  const own = new Set(expandSlots(S.get("trade.yml", "gui.own-slots") ?? S.def("trade.yml", "gui.own-slots")));
  const other = new Set(expandSlots(S.get("trade.yml", "gui.other-slots") ?? S.def("trade.yml", "gui.other-slots")));
  const first = (set) => [...set].sort((a, b) => a - b)[0];
  const screen = menuChest("trade.yml", "gui", { vars: MENUS.find((m) => m.id === "trade").vars,
    extra: (i) => i === first(own) ? { item: "PLAYER_HEAD", count: 2 } : i === first(own) + 1 ? { item: "DIAMOND", count: 3 } : i === first(other) ? { item: { material: "PLAYER_HEAD" } } : own.has(i) || other.has(i) ? { onclick: () => {} } : null,
    onButton: () => go("#/menus/trade") });
  root.append(layout([
    group("Requests", field("trade.yml", "request.timeout"), field("trade.yml", "request.vanished-status"), field("trade.yml", "cooldown.duration"), field("trade.yml", "cooldown.after-cancel"), rangeRow, field("trade.yml", "range.same-world")),
    group("What can be traded", field("trade.yml", "allowed-types.heads-for-heads"), field("trade.yml", "allowed-types.items-for-heads"), field("trade.yml", "allowed-types.items-for-items"), field("trade.yml", "allowed-types.gifts"),
      field("trade.yml", "limits.max-heads"), field("trade.yml", "limits.max-items"), field("trade.yml", "limits.only-own-heads"), field("trade.yml", "limits.blocked-items")),
    group("Blocked by statuses", field("trade.yml", "statuses.block-request"), field("trade.yml", "statuses.block-accept"), field("trade.yml", "statuses.cancel-open")),
    group("Confirming and cancelling", field("trade.yml", "confirm.countdown"), field("trade.yml", "cancel.on-teleport"), field("trade.yml", "cancel.teleport-min-distance"), field("trade.yml", "cancel.on-gamemode-change"),
      field("trade.yml", "cancel.check-interval"), field("trade.yml", "save-player-data-after-trade")),
  ], preview("The trade screen", screen)));
}

// ============================================================================================
//  Statuses
// ============================================================================================
export function statusUses(name) {
  const out = [];
  const lists = [["config.yml", "vault.block-movement.statuses", "Vault"], ["config.yml", "kills.killer-blocked-statuses", "Kills"], ["trade.yml", "statuses.block-request", "Trade requests"], ["trade.yml", "statuses.block-accept", "Accepting trades"], ["trade.yml", "statuses.cancel-open", "Open trades"]];
  for (const [f, p, what] of lists) if ((S.get(f, p) || []).some((x) => String(x).replace(/^!/, "") === name)) out.push(what);
  if (S.get("trade.yml", "request.vanished-status") === name) out.push("Trade requests");
  for (const [n, s] of Object.entries(S.get("statuses.yml", "statuses") || {})) {
    if (!isObj(s)) continue;
    if ([...(s.statuses || []), s.status, s.fallback].some((x) => String(x ?? "").replace(/^!/, "") === name)) out.push(n);
  }
  const scan = (file, v, where) => { if (Array.isArray(v)) v.forEach((x) => scan(file, x, where)); else if (isObj(v)) for (const [k, x] of Object.entries(v)) { if ((k === "statuses" || k === "any-statuses") && Array.isArray(x) && x.some((y) => String(y).replace(/^!/, "") === name)) out.push(where); scan(file, x, where); } };
  scan("outcomes.yml", S.doc("outcomes.yml")?.data, "Outcomes"); scan("shop.yml", S.doc("shop.yml")?.data, "Shop"); scan("actions.yml", S.doc("actions.yml")?.data, "Action lists"); scan("config.yml", S.get("config.yml", "hooks"), "Hooks");
  return [...new Set(out)];
}
export const SRC_SPRITE = { pvp: "iron_sword", "player-state": "clock", muted: "white_wool", bedrock: "bedrock", placeholder: "item_frame", "scoreboard-tag": "name_tag", permission: "trial_key", metadata: "paper",
  flag: "redstone", headcount: "player_head", "in-trade": "bundle", "pvp-damage": "iron_sword", "recent-damage": "redstone", "mob-damage": "zombie_head", "nearby-player": "player_head",
  "nearby-hostile": "creeper_head", "targeted-by-mob": "skeleton_skull", health: "glistering_melon_slice", food: "cooked_beef", "on-fire": "blaze_powder", "in-lava": "lava_bucket", "in-water": "water_bucket",
  falling: "feather", gliding: "elytra", flying: "elytra", swimming: "water_bucket", sneaking: "leather_boots", sprinting: "sugar", sleeping: "red_bed", dead: "bone", invisible: "glass",
  op: "command_block", riding: "saddle", gamemode: "grass_block", world: "filled_map", "joined-recently": "clock", potion: "potion", "y-level": "ladder", biome: "oak_sapling", holding: "diamond_sword",
  "world-time": "clock", "all-of": "comparator", "any-of": "repeater", not: "redstone_torch", always: "lime_dye", never: "gray_dye" };
function srcSettings(src) {
  const out = {};
  const text = src?.settingsText || "";
  for (const m of text.matchAll(/`([a-z0-9-]+)`:\s*((?:`[^`]+`(?:,\s*|\s+or\s+|,\s*or\s+)?)+)/g)) out[m[1]] = { options: [...m[2].matchAll(/`([^`]+)`/g)].map((x) => x[1]) };
  for (const m of text.matchAll(/`([a-z0-9-]+):\s*([^`]+)`/g)) if (!out[m[1]]) out[m[1]] = { def: m[2] };
  return out;
}
/** What Headcount uses for a status setting with fixed choices when the file leaves it out. */
const SRC_DEFAULTS = { direction: "either", hand: "any" };
const CHECKS = { "pvp-enabled": "PvP is on", "pvp-disabled": "PvP is off", "combat-tagged": "In combat", newbie: "New-player protection", "respawn-protected": "Just respawned", override: "PvP forced by a world",
  afk: "AFK", vanished: "Vanished", god: "In god mode", jailed: "Jailed", muted: "Muted" };
const list = (a) => { a = [].concat(a || []).map(String); return a.length > 1 ? a.slice(0, -1).join(", ") + " or " + a[a.length - 1] : a[0] || ""; };
export function statusSummary(s) {
  if (!isObj(s)) return "";
  const src = s.source;
  const d = {
    pvp: () => CHECKS[s.check] || s.check, "player-state": () => CHECKS[s.check] || s.check, gamemode: () => "Game mode is " + list(s.modes), "any-of": () => "Any of " + list(s.statuses),
    "all-of": () => "All of " + [].concat(s.statuses || []).join(", "), not: () => "Not " + s.status, health: () => s.below !== undefined ? "Health below " + s.below : "Health " + (s.range ?? ""),
    food: () => s.below !== undefined ? "Food below " + s.below : "Food " + (s.range ?? ""), falling: () => "Falling more than " + (s.distance ?? 4) + " blocks",
    "pvp-damage": () => ({ dealt: "Hit a player", took: "Hit by a player", either: "Hit or was hit by a player" }[s.direction || "either"] || "PvP damage") + " in the last " + (s.seconds ?? 15) + "s",
    "recent-damage": () => "Took damage in the last " + (s.seconds ?? 10) + "s", "mob-damage": () => "Hurt by a mob in the last " + (s.seconds ?? 10) + "s",
    metadata: () => "Has the " + s.key + " flag from a plugin", permission: () => "Has " + s.permission, "scoreboard-tag": () => "Has the tag " + s.tag, flag: () => "Has the flag " + s.flag,
    world: () => "In " + list(s.worlds), biome: () => "In " + list(s.biomes), holding: () => "Holding " + list((s.materials || []).map(prettyMaterial)), potion: () => "Has " + humanize(s.effect || "an effect"),
    "nearby-player": () => "A player within " + (s.radius ?? 16) + " blocks", "nearby-hostile": () => "A hostile mob within " + (s.radius ?? 8) + " blocks", placeholder: () => s.placeholder + " " + (s.operator ?? "") + " " + (s.value ?? ""),
  }[src];
  return d ? d() : humanize(src || "?");
}
function statusesPage(root, params) {
  const sources = S.store.catalog?.statuses || [];
  const add = async () => {
    const id = await promptDialog("New status", "A short name, like low-food:", "my-status", { check: idCheck((v) => S.get("statuses.yml", "statuses." + v)) });
    if (!id) return;
    S.set("statuses.yml", "statuses." + id, { source: "health", below: 6 });
    draw(); edit(id);
  };
  root.append(pageHead("Statuses", null, btn([icon("plus"), "New status"], add)));
  const listBox = el("div");
  const draw = () => {
    listBox.innerHTML = "";
    const items = el("div", { class: "items" });
    for (const [name, s] of Object.entries(S.get("statuses.yml", "statuses") || {})) {
      const uses = statusUses(name);
      items.append(el("div", { class: "item", onclick: () => edit(name) }, sprite(SRC_SPRITE[s?.source] || "redstone_torch", 28),
        el("div", { class: "main" }, el("div", { class: "t1" }, name), el("div", { class: "t2" }, statusSummary(s))),
        el("div", { class: "end" }, uses.length ? "Used by " + uses.slice(0, 2).join(", ") + (uses.length > 2 ? " and " + (uses.length - 2) + " more" : "") : "Not used")));
    }
    items.append(el("button", { class: "addrow", onclick: add }, icon("plus"), "New status"));
    listBox.append(items);
  };
  const edit = (name) => {
    const base = "statuses." + name;
    const body = el("div");
    const drawE = () => {
      body.innerHTML = "";
      const s = S.get("statuses.yml", base) || {};
      const src = sources.find((x) => x.source === s.source);
      const opts = srcSettings(src);
      const names = Object.keys(S.get("statuses.yml", "statuses") || {}).filter((x) => x !== name);
      const grouped = Object.entries(sources.reduce((g, x) => ((g[x.group] ||= []).push(x), g), {})).map(([g, xs]) => ({ group: g, items: xs.map((x) => [x.source, humanize(x.source)]) }));
      const rows = [el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, "True when")), el("div", { class: "ctl" },
        select(grouped, s.source || "", (v) => { S.set("statuses.yml", base + ".source", v); drawE(); }), src?.trueWhen ? el("div", { class: "note-inline" }, src.trueWhen.replace(/`/g, "")) : null))];
      const keys = [...new Set([...(src?.settings || []), ...Object.keys(opts), ...Object.keys(s).filter((k) => !["source", "fallback", "unavailable", "description"].includes(k))])];
      for (const k of keys) {
        const o = opts[k] || {};
        const p = base + "." + k;
        let row;
        if (k === "statuses") row = field("statuses.yml", p, { label: "Statuses", type: "statuses" });
        else if (k === "status") row = field("statuses.yml", p, { label: "Status", type: "select", options: names });
        else if (k === "modes") row = field("statuses.yml", p, { label: "Game modes", type: "list", suggestions: ["survival", "creative", "adventure", "spectator"] });
        else if (k === "materials") row = field("statuses.yml", p, { label: "Items", type: "materials" });
        else if (o.options) row = field("statuses.yml", p, { label: humanize(k), type: o.options.length <= 4 ? "seg" : "select", options: o.options.map((x) => [x, humanize(x)]), default: SRC_DEFAULTS[k] });
        else if (o.def !== undefined && /^-?\d+(\.\d+)?$/.test(o.def) && S.get("statuses.yml", p) === undefined) row = field("statuses.yml", p, { label: humanize(k), type: "number", placeholder: o.def });
        else if (o.def === "true" || o.def === "false") row = field("statuses.yml", p, { label: humanize(k), type: "bool" });
        else row = field("statuses.yml", p, { label: humanize(k) });
        rows.push(row);
      }
      body.append(group(null, ...rows),
        group("When it can't tell", field("statuses.yml", base + ".fallback", { label: "Ask another status", type: "select", options: [["", "None"], ...names.map((n) => [n, n])] }),
          field("statuses.yml", base + ".unavailable", { label: "Otherwise count it as", type: "seg", options: [[false, "Inactive"], [true, "Active"]], default: false })),
        group(null, field("statuses.yml", base + ".description", { label: "Note", type: "text", placeholder: "For yourself" })),
        el("p", { class: "muted" }, "Used by: " + (statusUses(name).join(", ") || "nothing yet") + ". Placeholder: ", el("code", {}, "%headcount_status_" + name + "%")));
    };
    drawE();
    drawer(name, body, { subtitle: statusSummary(S.get("statuses.yml", base)), onClose: draw, hash: "#/statuses/" + name, footer: doneFooter(dupBtn(() => { const n = freeId(name, (v) => S.get("statuses.yml", "statuses." + v) !== undefined); duplicateEntry("statuses.yml", base, n); toast("Made a copy, " + n + "."); edit(n); }), btn([icon("trash"), "Delete"], async () => { if (await confirmDialog("Delete " + name + "?", statusUses(name).length ? "It's used by " + statusUses(name).join(", ") + ", which would then name a status that doesn't exist." : null, { ok: "Delete", danger: true })) { S.remove("statuses.yml", base); closeDrawer(); } }, "ghost danger")) });
  };
  draw();
  root.append(el("section", { class: "group" }, listBox), group("Placeholder text", field("statuses.yml", "settings.placeholder-true"), field("statuses.yml", "settings.placeholder-false")));
  if (params[0] && S.get("statuses.yml", "statuses." + params[0])) edit(params[0]);
}

// ============================================================================================
//  Integrations
// ============================================================================================
const INTEG = [
  ["pvp", "PvP", "iron_sword", ["included", "pvpmanager", "none"], { included: "Headcount", pvpmanager: "PvPManager", none: "Nobody" }, { pvpmanager: "PvPManager" }],
  ["permissions", "Permissions", "trial_key", ["included", "luckperms", "vault", "none"], { included: "Headcount", luckperms: "LuckPerms", vault: "Vault", none: "Nobody" }, { luckperms: "LuckPerms", vault: "Vault" }],
  ["moderation", "Bans, mutes and jail", "iron_bars", ["included", "libertybans", "litebans", "advancedban", "essentials", "commands", "none"], { included: "Headcount", libertybans: "LibertyBans", litebans: "LiteBans", advancedban: "AdvancedBan", essentials: "EssentialsX", commands: "Commands", none: "Nobody" }, { libertybans: "LibertyBans", litebans: "LiteBans", advancedban: "AdvancedBan", essentials: "Essentials" }],
  ["placeholders", "Placeholders", "item_frame", ["included", "placeholderapi", "none"], { included: "Headcount", placeholderapi: "PlaceholderAPI", none: "Nobody" }, { placeholderapi: "PlaceholderAPI" }],
  ["player-state", "AFK and vanish", "clock", ["included", "essentials", "none"], { included: "Headcount", essentials: "EssentialsX", none: "Nobody" }, { essentials: "Essentials" }],
  ["economy", "Money", "gold_nugget", ["vault", "none"], { vault: "Vault", none: "Nobody" }, { vault: "Vault" }],
  ["discord", "Discord", "amethyst_shard", ["discordsrv", "none"], { discordsrv: "DiscordSRV", none: "Nobody" }, { discordsrv: "DiscordSRV" }],
  ["bedrock", "Bedrock players", "bedrock", ["floodgate", "geyser", "none"], { floodgate: "Floodgate", geyser: "Geyser", none: "Nobody" }, { floodgate: "floodgate", geyser: "Geyser-Spigot" }],
];
const MSG_EVENTS = [["toggled", "Switching PvP", ["pvp.toggled-on", "pvp.toggled-off"]], ["tag-start", "Entering combat", ["pvp.tag-start"]], ["tag-end", "Leaving combat", ["pvp.tag-end"]],
  ["blocked-hit", "A hit is stopped", ["pvp.blocked-hit.target-off", "pvp.blocked-hit.self-off"]], ["blocked-action", "Something is refused", ["pvp.blocked-command", "pvp.cooldown"]]];
function integrationsPage(root) {
  root.append(pageHead("Integrations"), group(null, field("integrations.yml", "auto-switch")));
  const installed = S.store.info.plugins || null;
  const has = (pl) => installed && installed.some((p) => p.toLowerCase().startsWith(pl.toLowerCase()));
  for (const [key, title, spr, options, names, pluginOf] of INTEG) {
    const cur = S.get("integrations.yml", key + ".provider") ?? S.def("integrations.yml", key + ".provider");
    const bodyEl = el("div");
    const drawBody = (v) => {
      bodyEl.innerHTML = "";
      const rows = [];
      if (v === "auto") rows.push(el("div", { class: "pad note-inline", style: { padding: "12px 18px", color: "var(--warn)" } }, "Auto isn't an option any more. Pick one."));
      if (installed && pluginOf[v] && !has(pluginOf[v])) rows.push(el("div", { class: "pad", style: { padding: "12px 18px", color: "var(--warn)" } }, pluginOf[v] + " isn't in this server's plugins folder. Headcount would use its built-in version, or nothing if it has none."));
      if (key === "pvp" && v === "included") rows.push(...pvpIncluded());
      if (key === "pvp" && v === "pvpmanager") rows.push(field("integrations.yml", "pvp.pvpmanager.let-players-toggle"));
      if (key === "pvp" && v !== "none") rows.push(subhead("Pets"), field("integrations.yml", "pvp.pet-protection.enabled", { hint: "For pet vengeance instead, see the Hurts a pet hook." }),
        field("integrations.yml", "pvp.pet-protection.offline-owners"));
      if (key === "placeholders" && v === "placeholderapi") rows.push(field("integrations.yml", "placeholders.placeholderapi.register-expansion"), field("integrations.yml", "placeholders.placeholderapi.expansion-format"));
      if (key === "permissions" && v === "luckperms") rows.push(field("integrations.yml", "permissions.luckperms.use-expiry"), field("integrations.yml", "permissions.luckperms.server-context"));
      if (key === "moderation" && v !== "none") rows.push(...moderationRows(v));
      if (key === "discord" && v !== "none") rows.push(field("integrations.yml", "discord.default-channel"));
      if (key === "player-state" && v === "included") rows.push(field("integrations.yml", "player-state.included.afk.after"), field("integrations.yml", "player-state.included.afk.looking-counts"), field("integrations.yml", "player-state.included.afk.announce"));
      if (rows.length) bodyEl.append(el("div", { class: "sheet" }, rows));
    };
    const opts = options.map((o) => [o, el("span", { class: "row gap-s", title: pluginOf[o] && installed ? (has(pluginOf[o]) ? "Installed" : "Not on this server") : null }, names[o] || o,
      pluginOf[o] && installed ? el("span", { class: "led" + (has(pluginOf[o]) ? " on" : ""), style: { width: "6px", height: "6px" } }) : null)]);
    const sg = seg(opts, cur, (v) => { S.set("integrations.yml", key + ".provider", v); drawBody(v); });
    drawBody(cur);
    root.append(el("section", { class: "group", "data-key": "integrations.yml#" + key + ".provider" }, el("header", { style: { alignItems: "center", flexWrap: "wrap" } }, sprite(spr, 24), el("h2", {}, title), el("div", { class: "side" }, sg)), bodyEl));
  }
  root.append(group("Custom items", itemProviders()));
}
function itemProviders() {
  const cur = S.get("integrations.yml", "items.providers") ?? S.def("integrations.yml", "items.providers") ?? [];
  const row = el("div", { class: "field", "data-key": "integrations.yml#items.providers" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Item plugins to use")),
    el("div", { class: "ctl" }, ["itemsadder", "oraxen", "nexo"].map((p) => el("label", { class: "switchrow" }, switchCtl(cur.includes(p), (on) => {
      const now = (S.get("integrations.yml", "items.providers") ?? []).filter((x) => x !== p);
      if (on) now.push(p);
      S.set("integrations.yml", "items.providers", ["itemsadder", "oraxen", "nexo"].filter((x) => now.includes(x)).concat(now.filter((x) => !["itemsadder", "oraxen", "nexo"].includes(x))));
      row.classList.toggle("unsaved", JSON.stringify(S.get("integrations.yml", "items.providers")) !== JSON.stringify(S.savedValue("integrations.yml", "items.providers")));
    }, p), { itemsadder: "ItemsAdder", oraxen: "Oraxen", nexo: "Nexo" }[p]))));
  return row;
}
function pvpIncluded() {
  const P = "pvp.included.";
  const f = (k, o) => field("integrations.yml", P + k, o);
  const msgs = el("div");
  for (const [ev, name, keys] of MSG_EVENTS) {
    const base = P + "messages." + ev;
    const show = el("div", { style: { marginTop: "10px" } });
    const drawShow = () => {
      show.innerHTML = "";
      const ch = S.get("integrations.yml", base + ".channel") ?? "chat";
      const text = S.lang(keys[0]) || "";
      const html = renderMini(text, { vars: SAMPLE, prefix: S.lang("prefix") });
      const scene = el("div", { class: "mcscene", style: { minHeight: "110px", flexDirection: "column", justifyContent: ch === "chat" ? "flex-end" : ch === "title" ? "center" : "flex-end", alignItems: ch === "chat" ? "flex-start" : "center" } });
      if (ch === "none") scene.append(el("div", { class: "faint" }, "Not shown"));
      else if (ch === "title") scene.append(el("div", { class: "titlecard" }, el("div", { class: "mc t", html })));
      else scene.append(el("div", { class: "mc" + (ch === "chat" ? " chatline" : " actionbar"), style: ch === "actionbar" ? { marginBottom: "30px" } : null, html }));
      show.append(scene);
    };
    msgs.append(el("div", { class: "sheet", style: { marginBottom: "14px" } }, subhead(name),
      field("integrations.yml", base + ".channel", { onChange: drawShow }),
      field("integrations.yml", base + ".sound", { pitch: () => S.get("integrations.yml", base + ".pitch") ?? 1, volume: () => S.get("integrations.yml", base + ".volume") ?? 1 }),
      S.get("integrations.yml", base + ".pitch") !== undefined ? field("integrations.yml", base + ".pitch") : null,
      field("lang/en_US.yml", keys[0], { label: "Text", type: "mini", onChange: drawShow, render: () => show })));
    drawShow();
  }
  const sec = (title, ...rows) => el("details", { class: "more", style: { padding: "0 18px", borderTop: "1px solid var(--line)" } }, el("summary", {}, title), el("div", { class: "rows", style: { margin: "0 -18px" } }, rows));
  return [
    f("toggle.default"), f("toggle.allow-toggle"), f("toggle.cooldown"), f("toggle.reset-on-join"), f("toggle.force-on-worlds"), f("toggle.force-off-worlds"),
    sec("New players and respawns", f("newbie-protection.duration"), f("newbie-protection.can-end-early"), f("respawn-protection.duration")),
    sec("Combat tag", f("combat-tag.enabled"), f("combat-tag.duration"), f("combat-tag.tag-attacker"), f("combat-tag.tag-victim"), f("combat-tag.untag-on-death"),
      subhead("While tagged"), f("combat-tag.block-pvp-toggle"), f("combat-tag.block-flight"), f("combat-tag.block-elytra"), f("combat-tag.block-ender-pearls"), f("combat-tag.block-teleport"), f("combat-tag.blocked-commands")),
    sec("Leaving during a fight", f("combat-log.enabled"), f("combat-log.punishment"), f("combat-log.credit-last-attacker"), f("combat-log.broadcast"),
      el("div", { class: "field stack" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Also run")), el("div", { class: "ctl", style: { alignItems: "stretch" } }, actionList("integrations.yml", P + "combat-log.actions", { nested: true })))),
    sec("Messages", el("div", { style: { padding: "4px 18px 4px" } }, msgs), f("messages.countdown"), f("messages.countdown-bossbar-color")),
  ];
}
function moderationRows(v) {
  const kinds = ["ban", "mute", "kick", "warn", "jail"];
  const names = { included: "Headcount", libertybans: "LibertyBans", litebans: "LiteBans", advancedban: "AdvancedBan", essentials: "EssentialsX", commands: "Commands", none: "Nobody" };
  const kindRow = el("div", { class: "field", "data-key": "integrations.yml#moderation.kinds" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Per kind"), el("div", { class: "hint" }, "Hand one kind to another plugin.")),
    el("div", { class: "ctl" }, el("div", { style: { display: "grid", gridTemplateColumns: "auto 1fr", gap: "6px 12px", alignItems: "center" } }, kinds.flatMap((k) => [el("span", {}, humanize(k)),
      select([["", "Same as above"], ...Object.entries(names)], S.get("integrations.yml", "moderation.kinds." + k) ?? "", (x) => S.set("integrations.yml", "moderation.kinds." + k, x || undefined))]))));
  const usesIncluded = v === "included" || Object.values(S.get("integrations.yml", "moderation.kinds") || {}).includes("included") || ["libertybans", "litebans"].includes(v);
  const out = [field("integrations.yml", "moderation.operator-name"), field("integrations.yml", "moderation.silent"), kindRow];
  if (usesIncluded) out.push(subhead("Headcount's jail and mute"), field("integrations.yml", "moderation.included-jail.location"), field("integrations.yml", "moderation.included-jail.radius"),
    field("integrations.yml", "moderation.included-jail.release-to"), field("integrations.yml", "moderation.included-jail.allowed-commands"), field("integrations.yml", "moderation.included-mute.blocked-commands"));
  if (v === "litebans") out.push(subhead("LiteBans commands"), ...["ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn"].map((k) => field("integrations.yml", "moderation.litebans-commands." + k, { label: humanize(k), type: "mono" })));
  if (v === "commands") out.push(subhead("Commands"), ...["ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn", "jail", "tempjail", "unjail"].map((k) => field("integrations.yml", "moderation.commands." + k, { label: humanize(k), type: "mono", placeholder: "Headcount does it" })));
  return out;
}

// ============================================================================================
//  Hooks
// ============================================================================================
const PLAYER_HOOKS = [["first-join", "First join", "The first time a player joins the server", "oak_door"], ["first-join-cycle", "First join this cycle", "A player's first join in each cycle", "clock"],
  ["became-active", "Becomes active", "When a player has played enough to count as active", "experience_bottle"], ["join", "Every join", "Each time a player joins", "player_head"]];
/** Hooks for things a player does. They can fire many times a minute, so each has a cooldown under hooks.cooldowns. */
const ACT_HOOKS = [["hurt-pet", "Hurts a pet", "A player hurts another player's tamed animal", "bone"], ["kill-pet", "Kills a pet", "A player kills another player's tamed animal", "lead"],
  ["open-trapped-chest", "Opens a trapped chest", "A player opens any trapped chest, their own included", "trapped_chest"]];
const PET_VARS = { owner: "Alex", owner_uuid: "853c80ef-3c37-49fd-aa49-938b674adae6", pet: "Rex", pet_type: "wolf", damage: 4, world: "world", x: 120, y: 64, z: -38 };
const HOOK_VARS = { "hurt-pet": PET_VARS, "kill-pet": (({ damage, ...v }) => v)(PET_VARS), "open-trapped-chest": { world: "world", x: 120, y: 64, z: -38 } };
function hooksPage(root, params) {
  root.append(pageHead("Hooks"));
  const warnings = Object.entries(C("hooks.warnings") || {}).sort((a, b) => parseDuration(b[1]?.before) - parseDuration(a[1]?.before));
  const stop = (label, sub, spr, onclick, edge) => el("button", { class: "stop" + (edge ? " edge" : ""), onclick }, el("span", { class: "pt" }, sprite(spr, 20)), el("b", {}, label), el("small", {}, sub));
  const count = (v) => { const n = Array.isArray(v) ? v.length : typeof v === "string" ? 1 : 0; return n ? n + " action" + (n === 1 ? "" : "s") : "Nothing yet"; };
  const addWarning = async () => {
    const id = await promptDialog("New warning", "How long before the end, like 30m, 6h or 2d:", "30m", { check: (v) => isNaN(parseDuration(v)) ? "That isn't a duration." : C("hooks.warnings." + v) ? "There's one already." : null });
    if (!id) return;
    S.set("config.yml", "hooks.warnings." + id, { before: id, global: [{ message: "<gold>Cycle <cycle> ends in <white>" + formatDuration(parseDuration(id)) + "</white>." }] });
    window.studio.refresh(); cycleHook("hooks.warnings." + id, "Warning");
  };
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "A cycle"), el("div", { class: "side" }, btn([icon("plus"), "Warning"], addWarning, "small ghost"))),
    el("div", { class: "sheet" }, el("div", { class: "track" },
      stop("Starts", count(C("hooks.cycle-start.global")), "lime_dye", () => cycleHook("hooks.cycle-start", "When a cycle starts"), true),
      warnings.map(([id, w]) => stop(formatDuration(parseDuration(w?.before)) + " left", w?.enabled === false ? "Off" : count(w?.global), "bell", () => cycleHook("hooks.warnings." + id, "Warning"))),
      stop("Ends", count(C("hooks.cycle-end.global")) + ", plus player reports", "red_dye", () => cycleHook("hooks.cycle-end", "When a cycle ends"), true)))));
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "A player")), el("div", { class: "sheet" }, el("div", { class: "track" },
    PLAYER_HOOKS.map(([h, label, when, spr]) => stop(label, count(C("hooks.player." + h)), spr, () => playerHook(h)))))));
  const every = (h) => { const ms = parseDuration(String(C("hooks.cooldowns." + h) ?? 0)); return ms > 0 ? ", at most every " + formatDuration(ms) : ""; };
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Something a player does")), el("div", { class: "sheet" }, el("div", { class: "track" },
    ACT_HOOKS.map(([h, label, when, spr]) => stop(label, count(C("hooks.player." + h)) + (C("hooks.player." + h)?.length ? every(h) : ""), spr, () => playerHook(h)))))));
  root.append(group("Timing", field("config.yml", "hooks.join-delay"), field("config.yml", "hooks.missed-warning-grace"), field("config.yml", "hooks.top-count")));
  // Shared action lists have their own page; old links to a list here still open it.
  if (params[0] === "list" && params[1]) { history.replaceState(null, "", "#/lists/" + params[1]); window.studio.refresh(); return; }
  const playerHook = (h) => {
    const [, label, when] = [...PLAYER_HOOKS, ...ACT_HOOKS].find((x) => x[0] === h);
    const vars = HOOK_VARS[h];
    // The doing hooks show their cooldown and the extra variables their text can use.
    const before = vars ? el("div", {}, group(null, field("config.yml", "hooks.cooldowns." + h, { hint: "A run skipped by the cooldown does nothing, so a timed action here should last longer than this." })),
      el("p", { class: "small muted", style: { margin: "-18px 0 18px" } }, "Text here can also use ", Object.keys(vars).map((k, i) => [i ? " " : "", el("code", {}, "<" + k + ">")]),
        h === "open-trapped-chest" ? ". Minecraft doesn't record who placed a chest." : ". Not for creative or spectator players, or an owner hurting their own pet.")) : null;
    editActions("config.yml", "hooks.player." + h, { title: label, subtitle: when, before, opts: vars ? { vars } : {}, onClose: () => window.studio.refresh(), hash: "#/hooks/hook/hooks.player." + h });
  };
  const names = listNames();
  root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Action lists")), el("div", { class: "items" },
    el("a", { class: "item", href: "#/lists" }, sprite("repeater", 28), el("div", { class: "main" }, el("div", { class: "t1" }, names.length ? names.length + " shared action list" + (names.length === 1 ? "" : "s") : "No shared action lists yet"),
      el("div", { class: "t2" }, "Groups of actions any hook, outcome or shop item can run by name")), icon("right")))));
  const hp = params[0] === "hook" ? params[1] || "" : "";
  if (hp.startsWith("hooks.player.") && [...PLAYER_HOOKS, ...ACT_HOOKS].some((x) => "hooks.player." + x[0] === hp)) playerHook(hp.slice(13));
  else if (hp === "hooks.cycle-start") cycleHook(hp, "When a cycle starts");
  else if (hp === "hooks.cycle-end") cycleHook(hp, "When a cycle ends");
  else if (hp.startsWith("hooks.warnings.") && S.get("config.yml", hp) !== undefined) cycleHook(hp, "Warning");
}
function cycleHook(path, title) {
  const warn = path.includes("warnings.");
  const hasPer = S.get("config.yml", path + ".per-player") !== undefined || !warn;
  let part = "global";
  const body = el("div");
  const draw = () => {
    body.innerHTML = "";
    if (warn) body.append(group(null, field("config.yml", path + ".before", { label: "Before the end", type: "duration", units: ["m", "h", "d"] }), field("config.yml", path + ".enabled", { label: "On", type: "bool" })));
    if (hasPer) body.append(el("div", { style: { marginBottom: "16px" } }, seg([["global", "Once for the server"], ["per", "For each player"]], part, (v) => { part = v; draw(); })));
    if (part === "global") body.append(actionList("config.yml", path + ".global", { vars: "cycle" }));
    else {
      body.append(group(null, field("config.yml", path + ".per-player.players", { label: "Players", type: "seg", options: [["all", "All"], ["active", "Active"], ["inactive", "Inactive"], ["online", "Online"]] }),
        field("config.yml", path + ".per-player.offline", { label: "Offline players", type: "seg", options: [["deliver", "Get it when they join"], ["skip", "Miss it"]] }),
        field("config.yml", path + ".per-player.expire-after-cycles", { label: "Drop stored ones after", type: "number", unit: "cycles", min: 1, step: 1 })),
      actionList("config.yml", path + ".per-player.actions"));
    }
  };
  draw();
  const when = warn ? formatDuration(parseDuration(S.get("config.yml", path + ".before") ?? path.split(".").pop())) + " before a cycle ends" : path.endsWith("cycle-end") ? "After scores and outcomes, before the vaults empty" : null;
  drawer(title, body, { wide: true, subtitle: when, onClose: () => window.studio.refresh(), hash: "#/hooks/hook/" + path,
    footer: doneFooter(warn ? btn([icon("trash"), "Delete warning"], async () => { if (await confirmDialog("Delete this warning?", null, { ok: "Delete", danger: true })) { S.remove("config.yml", path); closeDrawer(); } }, "ghost danger") : null) });
}

// ============================================================================================
//  Action lists (the named lists in actions.yml)
// ============================================================================================
const listCheck = (taken) => (v) => !/^[a-z0-9][a-z0-9_-]*$/i.test(v) ? "Use letters, numbers, dashes and underscores." : taken(v) ? "There's a list with that name." : null;
function listsPage(root, params) {
  const names = listNames();
  const add = async () => {
    const id = await promptDialog("New action list", "A short name, like celebrate:", freeListName("my-list"), { check: listCheck((v) => listNames().includes(v)) });
    if (!id) return;
    S.set("actions.yml", "lists." + id, [{ message: "<green>Hello <player>!" }]);
    openList(id);
  };
  root.append(pageHead("Action lists", "A named group of actions. Outcomes, shop items, hooks and other lists run it by name, so one change here reaches all of them.", btn([icon("plus"), "New list"], add)));
  const openList = (n) => {
    const places = usagePlaces(n);
    const usedBy = el("div", { class: "note", style: { marginBottom: "16px" } }, places.length ? ["Runs in ", places.map((p, i) => [i ? (i === places.length - 1 ? " and " : ", ") : "", el("a", { href: p.hash }, p.label)]), ". A change here applies to all of them."]
      : "Nothing runs this list yet. Pick it under Add an action anywhere actions go.");
    editActions("actions.yml", "lists." + n, { title: n, subtitle: usageText(listUsages(n)), onClose: () => window.studio.refresh(), hash: "#/lists/" + n, before: usedBy,
      footer: [
        btn([icon("edit"), "Rename"], async () => {
          const nn = await promptDialog("Rename " + n, places.length ? "New name. The " + places.length + " place" + (places.length === 1 ? "" : "s") + " that run it change to the new name too." : "New name:", n, { check: listCheck((x) => x !== n && listNames().includes(x)) });
          if (nn && nn !== n) { renameList(n, nn); toast("Renamed " + n + " to " + nn + (places.length ? " everywhere it's used." : ".")); openList(nn); }
        }, "ghost"),
        dupBtn(() => { const nn = freeListName(n + "-2"); duplicateEntry("actions.yml", "lists." + n, nn); toast("Made a copy, " + nn + "."); openList(nn); }),
        btn([icon("trash"), "Delete"], async () => {
          if (await confirmDialog("Delete " + n + "?", places.length ? "It runs in " + places.length + " place" + (places.length === 1 ? "" : "s") + " (" + places.map((p) => p.label).join(", ") + "), which would then do nothing there." : "Nothing runs it.", { ok: "Delete", danger: true })) { S.remove("actions.yml", "lists." + n); closeDrawer(); }
        }, "ghost danger")] });
  };
  if (!names.length) root.append(emptyBox("repeater", "No action lists yet", "Make one here, or choose some actions anywhere actions go and pick Make a list from these.", btn([icon("plus"), "New list"], add, "primary")));
  else {
    const items = el("div", { class: "items" });
    for (const n of names) {
      const v = S.get("actions.yml", "lists." + n);
      const arr = Array.isArray(v) ? v : v === undefined || v === null ? [] : [v];
      items.append(el("div", { class: "item", onclick: () => openList(n) }, sprite(arr[0] !== undefined ? actionSprite(arr[0]) : "repeater", 28),
        el("div", { class: "main" }, el("div", { class: "t1" }, n), el("div", { class: "t2" }, arr.slice(0, 3).map(summarize).join(", ") || "No actions")),
        el("div", { class: "end" }, usageText(listUsages(n)))));
    }
    items.append(el("button", { class: "addrow", onclick: add }, icon("plus"), "New list"));
    root.append(el("section", { class: "group" }, items));
  }
  const rest = allFields("actions.yml", "", { skip: ["lists", "config-version"] });
  if (rest.children.length) root.append(el("section", { class: "group" }, el("header", {}, el("h2", {}, "Other")), rest));
  if (params[0] && names.includes(params[0])) openList(params[0]);
}

// ============================================================================================
//  Sidebar
// ============================================================================================
function sidebarPage(root) {
  root.append(pageHead("Sidebar"));
  const prev = el("div");
  const drawPrev = () => {
    prev.innerHTML = "";
    const lines = C("sidebar.lines") || [];
    const sb = el("div", { class: "scoreboard" }, el("div", { class: "sbt", html: renderMini(C("sidebar.title"), { vars: SAMPLE }) }));
    for (const l of lines) sb.append(el("div", { class: "sbl", html: renderMini(l, { vars: SAMPLE }) || " " }));
    prev.append(preview("In game", el("div", { class: "mcscene", style: { justifyContent: "flex-end", alignItems: "center", minHeight: "340px" } }, sb)),
      el("div", { class: "small faint", style: { marginTop: "8px" } }, `${lines.length} of 15 lines`));
  };
  const linesBox = el("div", { style: { display: "grid", gap: "6px", width: "100%", gridTemplateColumns: "minmax(0, 1fr)" } });
  const linesRow = el("div", { class: "field stack", "data-key": "config.yml#sidebar.lines" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Lines")), el("div", { class: "ctl", style: { alignItems: "stretch" } }, linesBox));
  const drawLines = () => {
    linesBox.innerHTML = "";
    const lines = [...(C("sidebar.lines") || [])];
    const write = () => { S.set("config.yml", "sidebar.lines", lines, "config.yml#sidebar.lines"); linesRow.classList.toggle("unsaved", JSON.stringify(lines) !== JSON.stringify(S.savedValue("config.yml", "sidebar.lines"))); drawPrev(); };
    lines.forEach((l, i) => {
      linesBox.append(el("div", { class: "row lineedit" }, el("div", { class: "grow" }, miniCtl(l, (v) => { lines[i] = v; write(); }, { preview: false })),
        rowTools(i, lines.length, { what: "line",
          move: (from, to) => { const [m] = lines.splice(from, 1); lines.splice(to, 0, m); write(); drawLines(); },
          duplicate: () => { lines.splice(i + 1, 0, lines[i]); write(); drawLines(); },
          remove: () => { lines.splice(i, 1); write(); drawLines(); } })));
    });
    linesBox.append(el("div", { class: "row" }, btn([icon("plus"), "Line"], () => { lines.push("<gray>New line"); write(); drawLines(); }, "small", ),
      select((S.store.catalog?.placeholders || []).map((p) => [p.name, p.name + ": " + p.description]), "", (v) => { if (v) { lines.push("<gray>" + humanize(v) + ": <white>%headcount_" + v + "%"); write(); drawLines(); } }, { placeholder: "Add a line with a placeholder" })));
    linesRow.classList.toggle("unsaved", JSON.stringify(lines) !== JSON.stringify(S.savedValue("config.yml", "sidebar.lines")));
  };
  drawLines();
  root.append(layout([group(null, field("config.yml", "sidebar.enabled"), field("config.yml", "sidebar.shown-for-new-players"), field("config.yml", "sidebar.update-interval")),
    group("Contents", field("config.yml", "sidebar.title", { onChange: drawPrev }), linesRow, field("config.yml", "sidebar.lines-before-start"))], prev));
  drawPrev();
}

// ============================================================================================
//  Messages (lang)
// ============================================================================================
function messagesPage(root, params) {
  const L = "lang/en_US.yml";
  const data = S.doc(L).data || {};
  const groups = Object.keys(data).filter((k) => k !== "config-version");
  let group0 = params[0] && groups.includes(params[0]) ? params[0] : "general" in data ? "general" : groups[0];
  let filter = "all", q = "";
  root.append(pageHead("Messages"));
  const nav = el("div", { class: "msgnav" });
  const body = el("div");
  const search = el("input", { type: "search", placeholder: "Search every message", "aria-label": "Search messages", style: { maxWidth: "340px" }, oninput: (e) => { q = e.target.value.toLowerCase(); drawList(); drawNav(); } });
  const flat = (obj, p = "") => Object.entries(obj || {}).flatMap(([k, v]) => isObj(v) ? flat(v, p + k + ".") : [[p + k, v]]);
  const drawNav = () => {
    nav.innerHTML = "";
    for (const g of groups) nav.append(el("button", { class: g === group0 && !q ? "on" : "", onclick: () => { group0 = g; q = ""; search.value = ""; drawList(); drawNav(); } }, humanize(g), el("small", {}, isObj(data[g]) ? flat(data[g]).length : 1)));
  };
  const listBox = el("div");
  const drawList = () => {
    listBox.innerHTML = "";
    const items = q ? flat(S.doc(L).data).filter(([k, v]) => (k + " " + v).toLowerCase().includes(q)) : isObj(data[group0]) ? flat(data[group0], group0 + ".") : [[group0, data[group0]]];
    const sheet = el("div", { class: "sheet" });
    let n = 0;
    for (const [k] of items) {
      const v = S.get(L, k), d = S.def(L, k);
      if (filter === "changed" && JSON.stringify(v) === JSON.stringify(d)) continue;
      if (filter === "empty" && v !== "") continue;
      if (++n > 150) break;
      const rel = q ? k : k.slice(group0.length + 1) || k;
      sheet.append(field(L, k, { label: rel.replace(/\./g, " / ").replace(/-/g, " "), type: Array.isArray(v) ? "lore" : "mini", stack: true }));
    }
    listBox.append(n ? sheet : el("div", { class: "empty" }, filter === "changed" ? "Nothing changed here." : filter === "empty" ? "No empty messages here." : "No message matches."));
    if (n > 150) listBox.append(el("p", { class: "muted" }, "Showing 150. Search to narrow it down."));
  };
  body.append(el("div", { class: "row", style: { marginBottom: "14px" } }, search, el("span", { class: "grow" }), seg([["all", "All"], ["changed", "Changed"], ["empty", "Empty"]], filter, (f) => { filter = f; drawList(); })), listBox);
  drawNav(); drawList();
  root.append(el("div", { class: "msgs" }, nav, body));
}

// ============================================================================================
//  General, and the per-file editor
// ============================================================================================
function generalPage(root) {
  root.append(pageHead("General"));
  const types = el("div", { style: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(210px, 1fr))", gap: "8px 18px", padding: "14px 18px" } });
  for (const k of Object.keys({ ...(S.def("config.yml", "audit.types") || {}), ...(C("audit.types") || {}) })) {
    const p = "audit.types." + k;
    const lab = el("label", { class: "switchrow", "data-key": "config.yml#" + p, style: { color: "var(--ink)" } }, switchCtl(C(p) !== false, (c) => { S.set("config.yml", p, c); lab.style.color = JSON.stringify(S.get("config.yml", p)) !== JSON.stringify(S.savedValue("config.yml", p)) ? "var(--changed)" : "var(--ink)"; }, k), humanize(k));
    types.append(lab);
  }
  root.append(layout([
    group("Text", field("config.yml", "general.language"), field("config.yml", "general.date-format"), field("config.yml", "general.time-units.shown"), field("config.yml", "placeholders.true-text"), field("config.yml", "placeholders.false-text")),
    group("Leaderboard", field("config.yml", "top.lines"), field("config.yml", "top.refresh-interval")),
    group("Resets", field("config.yml", "reset.delete-old-heads"), field("config.yml", "reset.hard.enabled"), field("config.yml", "reset.hard.confirmations"), field("config.yml", "reset.hard.confirm-timeout"),
      field("config.yml", "reset.hard.backup-database"), field("config.yml", "reset.hard.archive-audit-log")),
    group("Audit log", field("config.yml", "audit.enabled"), field("config.yml", "audit.folder"), field("config.yml", "audit.max-file-size-mb"), field("config.yml", "audit.keep-days"), field("config.yml", "audit.compress-old-files"),
      field("config.yml", "audit.also-log-to-console"), field("config.yml", "audit.log-vault-moves"), field("config.yml", "audit.log-each-cycle-start-head"), field("config.yml", "audit.search-page-size")),
    el("section", { class: "group" }, el("header", {}, el("h2", {}, "Events to log")), el("div", { class: "sheet" }, types)),
    group("Timing and storage", field("config.yml", "gates.message-cooldown"), field("config.yml", "gui.click-cooldown"), field("config.yml", "storage.file")),
  ], []));
}
export const setupPages2 = [
  { id: "shop", group: "Game", title: "Shop", icon: ICONS.shop, render: shopPage, owns: ["shop.yml"] },
  { id: "titles", group: "Game", title: "Titles", icon: ICONS.title, render: titlesPage, owns: ["titles.yml"] },
  { id: "trading", group: "Game", title: "Trading", icon: ICONS.trade, render: tradingPage, owns: ["trade.yml"] },
  { id: "statuses", group: "Game", title: "Statuses", icon: ICONS.status, render: statusesPage, owns: ["statuses.yml"] },
  { id: "integrations", group: "Server", title: "Integrations", icon: ICONS.plug, render: integrationsPage, owns: ["integrations.yml"] },
  { id: "hooks", group: "Server", title: "Hooks", icon: ICONS.hook, render: hooksPage, owns: ["config.yml:hooks"] },
  { id: "lists", group: "Server", title: "Action lists", icon: "repeater", render: listsPage, owns: ["actions.yml"] },
  { id: "sidebar", group: "Server", title: "Sidebar", icon: ICONS.sidebar, render: sidebarPage, owns: ["config.yml:sidebar"] },
  { id: "menus", group: "Server", title: "Menus", icon: ICONS.menu, render: menusPage, owns: ["config.yml:vault.gui", "config.yml:history", "shop.yml:gui", "shop.yml:confirm-gui", "trade.yml:gui", "titles.yml:gui"] },
  { id: "messages", group: "Server", title: "Messages", icon: ICONS.lang, render: messagesPage, owns: ["lang/en_US.yml"] },
  { id: "safety", group: "Server", title: "General", icon: ICONS.reset, render: generalPage, owns: ["config.yml:general", "config.yml:reset", "config.yml:audit", "config.yml:storage", "config.yml:gates", "config.yml:gui", "config.yml:placeholders", "config.yml:top"] },
];
