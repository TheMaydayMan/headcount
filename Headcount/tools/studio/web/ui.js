// Shared components: elements, icons, controls that fit each kind of setting, the settings row bound to a config
// path, Minecraft previews (text, tooltips, chests), drawers, dialogs, popovers and toasts.
import { renderMini, materialLook, prettyMaterial, parseDuration, formatDuration, dumpEntry, dumpList, readYaml, isObj, MC_COLORS, stripMini } from "./core.js";
import * as S from "./store.js";
import { spec as specFor } from "./schema.js";
import { sprite, face, knownItems, haveTextures, playSound, soundKeys } from "./sprites.js";
export { sprite, face };

export function el(tag, attrs = {}, ...kids) {
  // A node, text or list where the attributes go is content (el("div", child) works too).
  if (attrs !== null && attrs !== undefined && (attrs.nodeType || typeof attrs !== "object" || Array.isArray(attrs))) { kids.unshift(attrs); attrs = {}; }
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") n.className = v;
    else if (k === "html") n.innerHTML = v;
    else if (k === "style" && typeof v === "object") { for (const [sk, sv] of Object.entries(v)) if (sv !== null && sv !== undefined) sk.startsWith("--") ? n.style.setProperty(sk, sv) : (n.style[sk] = sv); }
    else if (k.startsWith("on") && typeof v === "function") n.addEventListener(k.slice(2), v);
    else n.setAttribute(k, v === true ? "" : v);
  }
  for (const k of kids.flat(Infinity)) if (k !== null && k !== undefined && k !== false) n.append(k.nodeType ? k : document.createTextNode(String(k)));
  return n;
}
export const frag = (...kids) => { const f = document.createDocumentFragment(); for (const k of kids.flat(Infinity)) if (k != null && k !== false) f.append(k.nodeType ? k : document.createTextNode(String(k))); return f; };

// ---------------------------------------------------------------- icons (interface only; game things use sprites)
const PATHS = {
  search: "M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14zM20 20l-4-4",
  undo: "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3",
  redo: "M15 14l5-5-5-5M20 9H10a6 6 0 0 0 0 12h3",
  close: "M6 6l12 12M18 6L6 18",
  plus: "M12 5v14M5 12h14",
  trash: "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
  copy: "M9 9h11v11H9zM5 15H4V4h11v1",
  grip: "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01",
  reset: "M4 12a8 8 0 1 0 3-6.3M4 4v4h4",
  info: "M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18zM12 11v6M12 7.5v.01",
  right: "M9 6l6 6-6 6",
  down: "M6 9l6 6 6-6",
  left: "M15 6l-6 6 6 6",
  sun: "M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
  moon: "M20 14.5A8 8 0 1 1 9.5 4 6.5 6.5 0 0 0 20 14.5z",
  auto: "M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18zM12 3v18",
  power: "M12 3v8M6.3 6.3a8 8 0 1 0 11.4 0",
  menu: "M4 7h16M4 12h16M4 17h16",
  play: "M8 5l11 7-11 7z",
  check: "M5 12l5 5 9-10",
  edit: "M4 20h4L19 9l-4-4L4 16v4zM14 6l4 4",
  up: "M12 19V5M6 11l6-6 6 6",
  dn: "M12 5v14M6 13l6 6 6-6",
  dots: "M5 12h.01M12 12h.01M19 12h.01",
  file: "M6 3h8l4 4v14H6zM14 3v4h4",
  link: "M10 14l4-4M8 12l-2 2a3 3 0 0 0 4 4l2-2M16 12l2-2a3 3 0 0 0-4-4l-2 2",
  paint: "M4 20l4-1 11-11-3-3L5 16zM14 6l3 3",
  terminal: "M4 5h16v14H4zM8 10l3 2-3 2M13 15h3",
  external: "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6",
  refresh: "M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5",
};
export function icon(name, cls = "") {
  const s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  s.setAttribute("viewBox", "0 0 24 24");
  s.setAttribute("class", "ic " + cls);
  s.setAttribute("aria-hidden", "true");
  const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
  p.setAttribute("d", PATHS[name] || "");
  if (name === "grip" || name === "dots") { p.setAttribute("stroke-width", "3.2"); }
  s.append(p);
  return s;
}
export const iconBtn = (name, title, onclick, cls = "") => el("button", { class: "iconbtn " + cls, title, "aria-label": title, onclick }, icon(name));

export const btn = (label, onclick, cls = "") => el("button", { class: "btn " + cls, onclick }, label);
export const badge = (text, cls = "") => el("span", { class: "badge " + cls }, text);
/** A titled group of settings: a heading and a sheet of rows. */
export function group(title, ...rows) {
  const [t, side] = Array.isArray(title) ? title : [title, null];
  return el("section", { class: "group" }, t ? el("header", {}, el("h2", {}, t), side ? el("div", { class: "side" }, side) : null) : null, el("div", { class: "sheet" }, rows));
}
export const subhead = (t) => el("div", { class: "sub-h" }, t);

/** Sample values used to preview text: variables (<player>) and placeholders (%headcount_x% as "%x"). */
export const SAMPLE = {
  player: "Steve", other: "Alex", target: "Alex", killer: "Alex", victim: "Steve", owner: "Steve", cycle: 3, current_cycle: 3, points: 12, points_next: 0,
  score: 4, unique: 7, total: 21, own: 9, size: 27, used: 21, rank: 2, percentile: 80, streak: 2, streak_text: "2 positive", time_left: "4d 6h",
  cycle_start: "2026-11-01 00:00", cycle_end: "2026-12-01 00:00", cycle_length: 30, active: 12, players: 18, seconds: 12, countdown: "58s",
  countdown_clock: "0:58", duration: "30m", remaining: "12m", price: 10, name: "Speed for 30 minutes", tier: "Uncommon", reason: "Testing",
  head_id_short: "3f9a1c2e", head_id: "3f9a1c2e-0b4d", created: "2026-11-10 14:02", source: "cycle-start", page: 1, pages: 2, amount: 3,
  title: "<gold>[Champion]", description: "Finished a cycle in the top 3.", status: "in-pvp", command: "/home", state: "on", tagged: "no",
  "%cycle": 3, "%unique": 7, "%total": 21, "%score": 4, "%points": 12, "%streak_text": "2 positive", "%time_left": "4d 6h", "%vault_size": 27,
  "%rank": 2, "%title": "[Champion]", "%points_next": 0, "%active": 12,
};
export const prefix = () => S.lang("prefix") ?? "";

/** Fills in variables whose sample is itself formatted text (like <tier> or <title>) before rendering. */
export const expandVars = (src, vars) => String(src ?? "").replace(/<([a-z_]+)>/g, (m, k) => (typeof vars?.[k] === "string" && vars[k].includes("<") ? vars[k] : m));
const mini = (src, vars, more = {}) => { const all = { ...SAMPLE, ...(vars || {}) }; return renderMini(expandVars(src, all), { vars: all, prefix: prefix(), ...more }); };
/** A Minecraft text preview of MiniMessage. */
export function mcText(src, opts = {}) {
  return el("div", { class: "mc " + (opts.class || ""), html: mini(src, opts.vars) });
}
export const miniInline = (src, vars) => el("span", { class: "mcline", html: renderMini(src ?? "", { vars: { ...SAMPLE, ...(vars || {}) }, prefix: prefix(), base: "#3f3f3f" }) });
/** Rendered text for light interface surfaces: the MiniMessage colours, without the pixel font. */
export const miniLabel = (src, vars) => el("span", { class: "mlabel", html: renderMini(src ?? "", { vars: { ...SAMPLE, ...(vars || {}) }, prefix: prefix(), base: "currentColor" }) });

/** Text without formatting, with sample values in place of variables. */
export const plain = (src) => stripMini(String(src ?? "").replace(/<([a-z_]+)>/g, (m, k) => (SAMPLE[k] !== undefined ? stripMini(String(SAMPLE[k])) : m)));

/** The in-game item tooltip: name and lore lines. */
export function tooltip(name, lore, vars) {
  const box = el("div", { class: "tooltip-mc" });
  box.append(el("div", { class: "ln", html: mini(name, vars, { base: "#FFFFFF" }) }));
  for (const l of [].concat(lore || [])) for (const part of String(l).split("\n")) box.append(el("div", { class: "ln", html: mini(part, vars, { base: "#AA00AA" }) || "&nbsp;" }));
  return box;
}

let tipEl = null;
/** Shows `content()` next to the mouse while hovering `target`. */
export function hoverTip(target, content) {
  target.addEventListener("mouseenter", () => { tipEl?.remove(); tipEl = el("div", { class: "floattip" }, content()); document.body.append(tipEl); });
  target.addEventListener("mousemove", (e) => {
    if (!tipEl) return;
    const w = tipEl.offsetWidth, h = tipEl.offsetHeight;
    tipEl.style.left = Math.max(4, Math.min(window.innerWidth - w - 8, e.clientX + 14)) + "px";
    tipEl.style.top = Math.max(4, Math.min(window.innerHeight - h - 8, e.clientY - 26)) + "px";
  });
  target.addEventListener("mouseleave", () => { tipEl?.remove(); tipEl = null; });
}
export const clearTip = () => { tipEl?.remove(); tipEl = null; };

/**
 * A chest screen. slots(i) returns {item, tip, onclick, cls, count, title, draggable} or null for an empty slot.
 * Optional drag between slots: onDrop(from, to). opts.paint(i, down) makes slots paintable by dragging the mouse.
 */
export function chest(rows, titleSrc, slots, opts = {}) {
  const grid = el("div", { class: "slots" });
  let dragFrom = null, painting = false;
  for (let i = 0; i < rows * 9; i++) {
    const s = slots(i) || {};
    const cell = el("div", { class: "slot " + (s.cls || ""), title: s.title || null, "data-slot": i });
    if (s.item) cell.append(sprite(s.item, 32));
    if (s.count > 1) cell.append(el("span", { class: "cnt" }, s.count));
    if (opts.numbers) cell.append(el("span", { class: "num" }, i));
    if (s.tip) hoverTip(cell, s.tip);
    if (opts.paint) {
      cell.addEventListener("mousedown", (e) => { e.preventDefault(); painting = true; opts.paint(i, cell); });
      cell.addEventListener("mouseenter", () => { if (painting) opts.paint(i, cell, true); });
    } else cell.addEventListener("click", () => { clearTip(); s.onclick ? s.onclick(i) : opts.onEmpty?.(i); });
    if (opts.onDrop) {
      if (s.draggable) { cell.draggable = true; cell.addEventListener("dragstart", (e) => { clearTip(); dragFrom = i; e.dataTransfer.effectAllowed = "move"; e.dataTransfer.setData("text/plain", String(i)); }); }
      cell.addEventListener("dragover", (e) => { if (dragFrom !== null) { e.preventDefault(); cell.classList.add("drop"); } });
      cell.addEventListener("dragleave", () => cell.classList.remove("drop"));
      cell.addEventListener("drop", (e) => { e.preventDefault(); cell.classList.remove("drop"); if (dragFrom !== null && dragFrom !== i) opts.onDrop(dragFrom, i); dragFrom = null; });
      cell.addEventListener("dragend", () => { dragFrom = null; });
    }
    grid.append(cell);
  }
  if (opts.paint) window.addEventListener("mouseup", () => { if (painting) { painting = false; opts.paintEnd?.(); } });
  const box = el("div", { class: "chest", style: { "--rows": rows } }, el("div", { class: "ctop" }), el("div", { class: "cbot" }),
    el("div", { class: "ctitle", html: mini(titleSrc, opts.vars, { base: "#404040" }) }), grid);
  return el("div", { class: "chest-wrap" }, box);
}

// ---------------------------------------------------------------- toasts, dialogs, drawers, popovers
export function toast(msg, kind = "", action) {
  let box = document.querySelector(".toasts");
  if (!box) { box = el("div", { class: "toasts", role: "status" }); document.body.append(box); }
  const t = el("div", { class: "toast " + kind }, el("span", {}, msg), action ? el("button", { onclick: () => { t.remove(); action.run(); } }, action.label) : null);
  box.append(t);
  setTimeout(() => t.remove(), kind === "err" ? 9000 : action ? 8000 : 4200);
}

export function confirmDialog(title, body, { ok = "OK", danger = false, cancel = "Cancel" } = {}) {
  return new Promise((resolve) => {
    const scrim = el("div", { class: "scrim" });
    const close = (v) => { scrim.remove(); m.remove(); document.removeEventListener("keydown", key, true); resolve(v); };
    const key = (e) => { if (e.key === "Escape") { e.stopPropagation(); close(false); } if (e.key === "Enter") { e.preventDefault(); close(true); } };
    const okBtn = btn(ok, () => close(true), danger ? "danger" : "primary");
    const m = el("div", { class: "modal", role: "dialog", "aria-modal": "true" }, el("h3", {}, title), body ? (typeof body === "string" ? el("p", { class: "muted" }, body) : body) : null,
      el("div", { class: "acts" }, btn(cancel, () => close(false)), okBtn));
    scrim.addEventListener("click", () => close(false));
    document.addEventListener("keydown", key, true);
    document.body.append(scrim, m);
    setTimeout(() => okBtn.focus(), 20);
  });
}

export function promptDialog(title, label, value = "", { ok = "OK", check } = {}) {
  return new Promise((resolve) => {
    const inp = el("input", { type: "text", value, spellcheck: "false" });
    const err = el("div", { class: "small", style: { color: "var(--bad)", minHeight: "20px", marginTop: "6px" } });
    const scrim = el("div", { class: "scrim" });
    const close = (v) => { scrim.remove(); m.remove(); resolve(v); };
    const done = () => { const v = inp.value.trim(); const problem = check?.(v); if (problem) { err.textContent = problem; return; } close(v); };
    inp.addEventListener("keydown", (e) => { if (e.key === "Enter") done(); if (e.key === "Escape") { e.stopPropagation(); close(null); } });
    const m = el("div", { class: "modal", role: "dialog", "aria-modal": "true" }, el("h3", {}, title), label ? el("label", { class: "muted", style: { display: "block", marginBottom: "8px" } }, label) : null, inp, err,
      el("div", { class: "acts", style: { marginTop: "8px" } }, btn("Cancel", () => close(null)), btn(ok, done, "primary")));
    scrim.addEventListener("click", () => close(null));
    document.body.append(scrim, m);
    setTimeout(() => { inp.focus(); inp.select(); }, 20);
  });
}

let openDrawer = null;
export function drawer(title, body, { footer, onClose, subtitle, wide } = {}) {
  closeDrawer(true);
  const scrim = el("div", { class: "scrim" });
  const d = el("div", { class: "drawer" + (wide ? " wide" : ""), role: "dialog", "aria-label": typeof title === "string" ? title : "Editor" },
    el("header", {}, el("div", { class: "grow" }, el("h2", {}, title), subtitle ? el("div", { class: "sub" }, subtitle) : null), iconBtn("close", "Close (Esc)", () => closeDrawer())),
    el("div", { class: "db" }, body), footer ? el("footer", {}, footer) : null);
  scrim.addEventListener("click", () => closeDrawer());
  document.body.append(scrim, d);
  openDrawer = { close: (silent) => { scrim.remove(); d.remove(); if (!silent) onClose?.(); }, el: d };
  return openDrawer;
}
/** Closes the open drawer. silent skips its onClose (when another drawer or page replaces it). */
export function closeDrawer(silent) { closePop(); if (openDrawer) { const o = openDrawer; openDrawer = null; o.close(silent === true); return true; } return false; }

let openPopEl = null, popCleanup = null;
/** A small panel next to an element. Closes on outside click or Escape. */
export function popover(anchor, content, { cls = "pop", width } = {}) {
  closePop();
  const p = el("div", { class: cls }, content);
  if (width) p.style.width = width;
  document.body.append(p);
  const r = anchor.getBoundingClientRect();
  const w = p.offsetWidth, h = p.offsetHeight;
  let x = Math.min(window.innerWidth - w - 12, Math.max(12, r.left));
  let y = r.bottom + 6;
  if (y + h > window.innerHeight - 12) y = Math.max(12, r.top - h - 6);
  p.style.left = x + "px"; p.style.top = y + "px";
  const away = (e) => { if (!p.contains(e.target) && !anchor.contains(e.target)) closePop(); };
  const esc = (e) => { if (e.key === "Escape") { e.stopPropagation(); closePop(); anchor.focus?.(); } };
  setTimeout(() => document.addEventListener("mousedown", away), 0);
  document.addEventListener("keydown", esc, true);
  openPopEl = p;
  popCleanup = () => { document.removeEventListener("mousedown", away); document.removeEventListener("keydown", esc, true); };
  return p;
}
export function closePop() { if (openPopEl) { openPopEl.remove(); openPopEl = null; popCleanup?.(); popCleanup = null; return true; } return false; }

// ---------------------------------------------------------------- basic controls
export function switchCtl(checked, onChange, label) {
  const cb = el("input", { type: "checkbox", role: "switch", "aria-label": label || null });
  cb.checked = !!checked;
  cb.addEventListener("change", () => onChange(cb.checked));
  return el("label", { class: "switch" }, cb, el("span"));
}
export function seg(options, value, onChange) {
  const box = el("div", { class: "seg", role: "radiogroup" });
  const draw = (v) => {
    box.innerHTML = "";
    for (const o of options) {
      const [val, label] = Array.isArray(o) ? o : [o, o];
      box.append(el("button", { type: "button", role: "radio", "aria-checked": String(String(val) === String(v)), class: String(val) === String(v) ? "on" : "", onclick: () => { draw(val); onChange(val); } }, label));
    }
  };
  draw(value);
  return box;
}
export function select(options, value, onChange, { placeholder } = {}) {
  const s = el("select", { onchange: (e) => onChange(e.target.value) });
  if (placeholder !== undefined) s.append(el("option", { value: "" }, placeholder));
  let found = false;
  for (const o of options) {
    if (o && o.group) { s.append(el("optgroup", { label: o.group }, o.items.map((x) => { const [v, l] = Array.isArray(x) ? x : [x, x]; if (String(v) === String(value)) found = true; return el("option", { value: v, selected: String(v) === String(value) }, l); }))); continue; }
    const [v, l] = Array.isArray(o) ? o : [o, o];
    if (String(v) === String(value)) found = true;
    s.append(el("option", { value: v, selected: String(v) === String(value) }, l));
  }
  if (!found && value !== undefined && value !== null && value !== "") s.append(el("option", { value, selected: true }, String(value)));
  return s;
}
/** A list of short values as removable chips. suggestions turn the input into a pick list. */
export function chips(items, onChange, { placeholder, suggestions, render } = {}) {
  const list = (items || []).map(String);
  const box = el("div", { class: "chips" });
  const dl = suggestions ? el("datalist", { id: "dl" + Math.random().toString(36).slice(2) }, suggestions.map((s) => el("option", { value: s }))) : null;
  const draw = () => {
    box.innerHTML = "";
    list.forEach((it, idx) => box.append(el("span", { class: "chip" }, render ? render(it) : it, el("button", { type: "button", title: "Remove " + it, "aria-label": "Remove " + it, onclick: () => { list.splice(idx, 1); onChange(list.slice()); draw(); } }, icon("close")))));
    const inp = el("input", { type: "text", placeholder: placeholder || (list.length ? "Add" : "Type and press Enter"), list: dl?.id, spellcheck: "false" });
    const add = () => { const v = inp.value.trim().replace(/,$/, ""); if (!v) return; list.push(v); onChange(list.slice()); draw(); box.querySelector("input").focus(); };
    inp.addEventListener("keydown", (ev) => {
      if ((ev.key === "Enter" || ev.key === ",") && inp.value.trim()) { ev.preventDefault(); add(); }
      if (ev.key === "Backspace" && !inp.value && list.length) { list.pop(); onChange(list.slice()); draw(); box.querySelector("input").focus(); }
    });
    inp.addEventListener("change", () => { if (suggestions?.includes(inp.value.trim())) add(); });
    inp.addEventListener("blur", () => { if (inp.value.trim()) add(); });
    box.append(inp);
    if (dl) box.append(dl);
  };
  draw();
  return box;
}

/** Status names as chips, picked from statuses.yml; each can be flipped to "not". */
export function statusPicker(items, onChange, { single = false } = {}) {
  const names = Object.keys(S.get("statuses.yml", "statuses") || {});
  const list = (items || []).map(String);
  const box = el("div", { class: "chips" });
  const draw = () => {
    box.innerHTML = "";
    list.forEach((it, idx) => {
      const neg = it.startsWith("!");
      const nm = it.replace(/^!/, "");
      const known = names.includes(nm) || ["always", "never"].includes(nm);
      box.append(el("span", { class: "chip" + (neg ? " neg" : ""), title: known ? null : "Not in statuses.yml" },
        el("button", { type: "button", title: neg ? "Matches when it is NOT active. Click for active." : "Matches when active. Click for not active.", onclick: () => { list[idx] = neg ? nm : "!" + nm; onChange(single ? list[0] : list.slice()); draw(); }, style: { padding: "0 2px", fontWeight: 600 } }, neg ? "not" : ""),
        el("span", { style: known ? null : { color: "var(--bad)" } }, nm),
        el("button", { type: "button", "aria-label": "Remove " + nm, onclick: () => { list.splice(idx, 1); onChange(single ? undefined : list.slice()); draw(); } }, icon("close"))));
    });
    if (single && list.length) return;
    const free = names.filter((n) => !list.some((x) => x.replace(/^!/, "") === n));
    box.append(select(free, "", (v) => { if (v) { list.push(v); onChange(single ? v : list.slice()); draw(); } }, { placeholder: list.length ? "Add a status" : "Pick a status" }));
  };
  draw();
  return box;
}

// ---------------------------------------------------------------- pickers
export const MATERIALS = ["PLAYER_HEAD", "SKELETON_SKULL", "DIAMOND", "DIAMOND_BLOCK", "DIAMOND_SWORD", "EMERALD", "GOLD_INGOT", "GOLD_BLOCK", "IRON_INGOT",
  "NETHERITE_INGOT", "NETHER_STAR", "TOTEM_OF_UNDYING", "SHIELD", "CHEST", "ENDER_CHEST", "BARREL", "SHULKER_BOX", "NAME_TAG", "PAPER", "BOOK", "WRITABLE_BOOK",
  "CLOCK", "COMPASS", "ARROW", "SPECTRAL_ARROW", "BARRIER", "FEATHER", "SUGAR", "COOKED_BEEF", "BREAD", "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "POTION",
  "EXPERIENCE_BOTTLE", "GOLDEN_PICKAXE", "DIAMOND_PICKAXE", "ELYTRA", "FIREWORK_ROCKET", "TNT", "CARVED_PUMPKIN", "GOLDEN_HELMET", "IRON_BARS", "BELL",
  "SUNFLOWER", "LIME_DYE", "RED_DYE", "GRAY_DYE", "LIME_CONCRETE", "RED_CONCRETE", "WHITE_STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE",
  "BLACK_STAINED_GLASS_PANE", "LIGHT_GRAY_STAINED_GLASS_PANE", "LIME_STAINED_GLASS_PANE", "RED_STAINED_GLASS_PANE", "YELLOW_STAINED_GLASS_PANE",
  "BLUE_STAINED_GLASS_PANE", "ORANGE_STAINED_GLASS_PANE", "PURPLE_STAINED_GLASS_PANE", "OAK_SIGN", "MAP", "FILLED_MAP", "BEACON", "ANVIL", "REDSTONE",
  "LAPIS_LAZULI", "EMERALD_BLOCK", "IRON_BLOCK", "COPPER_INGOT", "TRIDENT", "BOW", "CROSSBOW", "NETHERITE_SWORD", "BLAZE_POWDER", "GHAST_TEAR"];
const matList = el("datalist", { id: "materials" }, MATERIALS.map((m) => el("option", { value: m })));
document.body.append(matList);
const allMaterials = () => { const k = knownItems(); return k.length ? k.map((x) => x.toUpperCase()) : MATERIALS; };

/** Opens the item grid next to `anchor`; onPick(MATERIAL). */
export function pickMaterial(anchor, current, onPick) {
  const q = el("input", { type: "search", placeholder: "Search items", spellcheck: "false" });
  const grid = el("div", { class: "pgrid" });
  const foot = el("div", { class: "pfoot" });
  const all = allMaterials();
  const draw = () => {
    grid.innerHTML = "";
    const s = q.value.trim().toUpperCase().replace(/\s+/g, "_");
    const hits = s ? all.filter((m) => m.includes(s)).sort((a, b) => (a.startsWith(s) ? 0 : 1) - (b.startsWith(s) ? 0 : 1) || a.length - b.length) : MATERIALS.filter((m) => all.includes(m) || !haveTextures());
    for (const m of hits.slice(0, 240)) grid.append(el("button", { type: "button", title: prettyMaterial(m), class: m === String(current).toUpperCase() ? "on" : "", onclick: () => { closePop(); onPick(m); } }, sprite(m, 32)));
    foot.textContent = s ? (hits.length ? `${hits.length} match${hits.length === 1 ? "" : "es"}` + (hits.length > 240 ? ", showing 240" : "") : "No item by that name. Press Enter to use it anyway.") : "Common picks. Type to search every item.";
  };
  q.addEventListener("input", draw);
  q.addEventListener("keydown", (e) => { if (e.key === "Enter") { const first = grid.querySelector("button"); if (first && q.value.trim()) first.click(); else if (q.value.trim()) { closePop(); onPick(q.value.trim().toUpperCase().replace(/\s+/g, "_")); } } });
  const p = popover(anchor, el("div", { style: { display: "contents" } }, q, grid, foot), { cls: "picker" });
  draw();
  setTimeout(() => q.focus(), 10);
  return p;
}
export function materialCtl(value, onChange, { allowEmpty } = {}) {
  const b = el("button", { type: "button", class: "matbtn" });
  const draw = (v) => { b.innerHTML = ""; b.append(v ? sprite(v, 30) : el("span", { class: "sprite flat", style: { "--s": "30px", "--c": "transparent" } }), el("span", { class: "nm" }, v ? prettyMaterial(v) : "Pick an item")); };
  draw(value);
  b.addEventListener("click", () => pickMaterial(b, value, (m) => { value = m; draw(m); onChange(m); }));
  if (!allowEmpty || !value) return b;
  return el("div", { class: "row" }, b, iconBtn("close", "Clear", () => { value = undefined; draw(undefined); onChange(undefined); }, "sm"));
}
export function materialsCtl(items, onChange) {
  const list = (items || []).map(String);
  const box = el("div", { class: "chips" });
  const draw = () => {
    box.innerHTML = "";
    list.forEach((m, i) => box.append(el("span", { class: "chip" }, sprite(m, 16), prettyMaterial(m), el("button", { type: "button", "aria-label": "Remove", onclick: () => { list.splice(i, 1); onChange(list.slice()); draw(); } }, icon("close")))));
    const add = el("button", { type: "button", class: "btn ghost small" }, icon("plus"), list.length ? "Add" : "Add an item");
    add.addEventListener("click", () => pickMaterial(add, "", (m) => { if (!list.includes(m)) list.push(m); onChange(list.slice()); draw(); }));
    box.append(add);
  };
  draw();
  return box;
}

/** A sound key with search over every Minecraft sound, and a play button. */
export function soundCtl(value, onChange, { pitch, volume } = {}) {
  const inp = el("input", { type: "text", value: value || "", placeholder: "No sound", spellcheck: "false", style: { fontFamily: "var(--mono)", fontSize: "13.5px" } });
  const play = iconBtn("play", "Play", async () => { if (!(await playSound(inp.value, { pitch: pitch?.(), volume: volume?.() }))) toast("Can't play that sound here.", "err"); }, "sm");
  inp.addEventListener("input", () => onChange(inp.value.trim()));
  inp.addEventListener("focus", async () => {
    const keys = await soundKeys();
    if (!keys.length) return;
    const list = el("div", { class: "plist" });
    const draw = () => {
      list.innerHTML = "";
      const s = inp.value.trim().replace(/^minecraft:/, "").toLowerCase();
      const hits = (s ? keys.filter((k) => k.includes(s)) : keys.filter((k) => /^(ui|block\.note_block|entity\.experience_orb|entity\.player\.levelup|item\.armor)/.test(k))).slice(0, 80);
      for (const k of hits) list.append(el("button", { type: "button", onmousedown: (e) => e.preventDefault(), onclick: () => { inp.value = "minecraft:" + k; onChange(inp.value); playSound(k, { pitch: pitch?.() }); closePop(); } }, k,
        el("small", { onclick: (e) => { e.stopPropagation(); playSound(k); } }, icon("play"))));
    };
    draw();
    inp.oninput = () => { onChange(inp.value.trim()); draw(); };
    popover(inp, list, { cls: "picker" });
  });
  return el("div", { class: "row", style: { flexWrap: "nowrap" } }, inp, play, value ? iconBtn("close", "No sound", () => { inp.value = ""; onChange(""); }, "sm") : null);
}

/** Minecraft's named colours as swatches. */
export function colorCtl(value, onChange, { names } = {}) {
  const box = el("div", { class: "swatches", role: "radiogroup" });
  const BOSS = { pink: "#ec6fd0", blue: "#3fa9f5", red: "#ec3d3d", green: "#5ae35a", yellow: "#f2e84b", purple: "#a35ce6", white: "#f5f5f5" };
  const list = names || Object.keys(MC_COLORS).filter((n) => !/grey/.test(n));
  const draw = (v) => {
    box.innerHTML = "";
    for (const n of list) box.append(el("button", { type: "button", role: "radio", "aria-checked": String(n === v), title: n.replace(/_/g, " "), "aria-label": n.replace(/_/g, " "), class: n === v ? "on" : "", style: { background: BOSS[n] && names ? BOSS[n] : MC_COLORS[n] || n }, onclick: () => { draw(n); onChange(n); } }));
  };
  draw(value);
  return box;
}

/** Time zones: every IANA zone the browser knows, plus the fixed forms Headcount reads. */
export function zoneCtl(value, onChange) {
  let zones = [];
  try { zones = Intl.supportedValuesOf("timeZone"); } catch (e) { zones = ["UTC", "Europe/London", "America/New_York"]; }
  zones = ["UTC", "system", ...zones.filter((z) => z !== "UTC")];
  const inp = el("input", { type: "text", value: value ?? "UTC", spellcheck: "false", style: { maxWidth: "280px" } });
  const open = () => {
    const list = el("div", { class: "plist" });
    const draw = () => {
      list.innerHTML = "";
      const s = inp.value.trim().toLowerCase().replace(/ /g, "_");
      const hits = (zones.some((z) => z.toLowerCase() === s) ? zones : zones.filter((z) => z.toLowerCase().includes(s))).slice(0, 120);
      for (const z of hits) list.append(el("button", { type: "button", onmousedown: (e) => e.preventDefault(), onclick: () => { inp.value = z; onChange(z); closePop(); } }, z.replace(/_/g, " "), el("small", {}, zoneNow(z))));
      if (!hits.length) list.append(el("div", { class: "pfoot" }, "Fixed offsets like UTC+5:30 or MST work too."));
    };
    draw();
    inp.oninput = () => { onChange(inp.value.trim()); draw(); };
    popover(inp, list, { cls: "picker" });
  };
  inp.addEventListener("focus", open);
  return inp;
}
function zoneNow(z) {
  try { return new Intl.DateTimeFormat(undefined, { timeZone: z === "system" ? undefined : z, hour: "2-digit", minute: "2-digit" }).format(new Date()); } catch (e) { return ""; }
}

const UNITS = [["ms", "milliseconds", 1], ["s", "seconds", 1000], ["m", "minutes", 60000], ["h", "hours", 3600000], ["d", "days", 86400000], ["w", "weeks", 604800000]];
/** A duration as a number and a unit. Writes the plugin's short form ("30s", "10m"). */
export function durationCtl(value, onChange, { zero, units } = {}) {
  const ms = parseDuration(String(value ?? 0));
  const list = UNITS.filter(([u]) => (units || ["s", "m", "h", "d"]).includes(u) || (u === "ms" && /ms$/.test(String(value))) || (u === "w" && /w/.test(String(value))));
  let unit = list[list.length - 1];
  if (isFinite(ms)) { for (const u of [...list].reverse()) if (ms % u[2] === 0) { unit = u; break; } if (ms === 0) unit = list.find((u) => u[0] === "s") || list[0]; }
  const num = el("input", { type: "number", min: 0, step: "any", value: isFinite(ms) ? ms / unit[2] : "" });
  const sel = el("select", {}, list.map(([u, name]) => el("option", { value: u, selected: u === unit[0] }, name)));
  const note = el("span", { class: "note-inline" });
  const showNote = () => { note.textContent = zero && +num.value === 0 ? zero : ""; };
  const write = () => {
    if (num.value === "" || isNaN(+num.value)) return;
    const n = +num.value;
    showNote();
    onChange(n === 0 && typeof value === "number" ? 0 : String(Math.round(n * 1000) / 1000) + sel.value);
  };
  num.addEventListener("input", write); sel.addEventListener("change", write);
  showNote();
  const bad = !isFinite(ms) && value !== undefined ? el("div", { class: "err" }, `"${value}" isn't a duration`) : null;
  return el("div", { class: "row" }, el("div", { class: "unit" }, num, sel), note, bad);
}
export function numberCtl(value, onChange, { unit, min, max, step, slider } = {}) {
  const inp = el("input", { type: "number", value: value ?? "", min, max, step: step ?? "any" });
  inp.addEventListener("input", () => { if (inp.value.trim() !== "" && !isNaN(+inp.value)) { onChange(+inp.value); if (r) r.value = inp.value; } });
  const box = unit ? el("div", { class: "unit" }, inp, el("span", {}, unit)) : inp;
  let r = null;
  if (slider) {
    r = el("input", { type: "range", min: slider[0], max: slider[1], step: step ?? 1, value: value ?? 0, "aria-label": "Slider" });
    r.addEventListener("input", () => { inp.value = r.value; onChange(+r.value); });
    return el("div", { class: "slider-row" }, box, r);
  }
  return box;
}

/**
 * MiniMessage text. Shows the text as it looks in game; the source is edited right above it. Lists (lore, multi-line
 * messages) are one line per row.
 */
export function miniCtl(value, onChange, { multiline, list, vars, placeholder, preview = true, emptyText } = {}) {
  const isList = list || Array.isArray(value);
  const text = isList ? [].concat(value ?? []).join("\n") : String(value ?? "");
  const multi = isList || multiline || text.includes("\n");
  const inp = multi ? el("textarea", { rows: Math.max(1, text.split("\n").length), spellcheck: "false", placeholder: placeholder || "" }) : el("input", { type: "text", spellcheck: "false", placeholder: placeholder || "" });
  inp.value = text;
  const out = el("div", { class: "rendered", "data-empty": emptyText || "" });
  const all = { ...SAMPLE, ...(vars || {}) };
  // Variables whose sample is itself formatted text (like <title>) are expanded before rendering.
  const expand = (l) => l.replace(/<([a-z_]+)>/g, (m, k) => (typeof all[k] === "string" && all[k].includes("<") ? all[k] : m));
  const draw = () => {
    if (multi) inp.rows = Math.max(1, inp.value.split("\n").length);
    out.innerHTML = "";
    const lines = inp.value.split("\n");
    if (!inp.value && !isList) { out.classList.add("blank"); return; }
    out.classList.remove("blank");
    for (const l of lines) out.append(el("div", { class: "mc", html: renderMini(expand(l), { vars: all, prefix: prefix() }) || "&nbsp;" }));
  };
  inp.addEventListener("input", () => { draw(); onChange(isList ? (inp.value === "" ? [] : inp.value.split("\n")) : inp.value); });
  out.addEventListener("click", () => inp.focus());
  draw();
  return el("div", { class: "mtext" }, inp, preview ? out : null);
}

// ---------------------------------------------------------------- the settings row
/** Options listed in a help text like "included | pvpmanager | none" that contain the value. */
export function optionsFromHelp(help, value) {
  for (const m of String(help || "").matchAll(/([A-Za-z0-9_.\-]+(?:\s*\|\s*[A-Za-z0-9_.\-]+)+)/g)) {
    const opts = m[1].split("|").map((x) => x.trim()).filter(Boolean);
    if (opts.includes(String(value)) && opts.length <= 12) return opts;
  }
  return null;
}
const looksMini = (s) => /<[a-z#\/!][^>]*>|%[a-z]+_/i.test(String(s ?? ""));

/** YAML text for a value (for the YAML control). */
export function toYamlText(v) {
  if (Array.isArray(v)) return dumpList(v, 0).join("\n");
  if (isObj(v)) return Object.keys(v).flatMap((k) => dumpEntry(k, v[k], 0)).join("\n");
  return v === undefined || v === null ? "" : String(v);
}
export function fromYamlText(t) {
  if (!t.trim()) return [];
  return readYaml(t);
}
const show = (v) => (v === undefined ? "not set" : isObj(v) ? "a section" : Array.isArray(v) ? (v.length ? v.join(", ") : "empty") : v === "" ? "empty" : String(v));

/** The detail behind a setting, on demand: where it lives, its default and the comment in the file. */
function aboutPop(anchor, file, path, help, d) {
  popover(anchor, el("div", {},
    el("div", { class: "k" }, file + "  " + path),
    d !== undefined ? el("div", { class: "defv" }, "Default: ", el("code", {}, show(d).slice(0, 80))) : null,
    help ? el("pre", {}, help) : null));
}

/**
 * A settings row bound to a config option. The control comes from the option's spec (schema.js) or opts: label,
 * hint, type (bool | seg | select | number | duration | time | date | zone | mini | lore | text | mono | list |
 * statuses | status | material | materials | sound | color | bosscolor | yaml), options, unit, min, max, step, slider,
 * zero, placeholder, vars, onChange(value), render(ctl) for extra content.
 */
export function field(file, path, opts = {}) {
  const sp = { ...specFor(file, path), ...opts };
  const value = S.get(file, path);
  const d = S.def(file, path);
  const v0 = value !== undefined ? value : d !== undefined ? d : sp.default;
  const help = S.entry(file, path)?.help || S.doc(file)?.defParsed.index.get(path)?.help || "";
  let type = sp.type;
  if (!type) {
    if (typeof v0 === "boolean") type = "bool";
    else if (typeof v0 === "number") type = "number";
    else if (Array.isArray(v0) && v0.every((x) => !isObj(x) && !Array.isArray(x))) type = v0.some(looksMini) ? "lore" : "list";
    else if (isObj(v0) || Array.isArray(v0)) type = "yaml";
    else if (sp.options || optionsFromHelp(help, v0)) type = (sp.options || optionsFromHelp(help, v0)).length <= 4 ? "seg" : "select";
    else if (typeof v0 === "string" && /^\d+(\.\d+)?(ms|s|m|h|d|w)/.test(v0) && !isNaN(parseDuration(v0))) type = "duration";
    else if (looksMini(v0)) type = "mini";
    else type = "text";
  }
  const key = path.split(".").pop();
  const row = el("div", { class: "field" + (sp.stack || ["lore", "yaml"].includes(type) && sp.stack !== false ? " stack" : "") + (type === "bool" ? " inline" : ""), "data-key": file + "#" + path });
  const write = (v) => { S.set(file, path, v); refresh(); sp.onChange?.(v); };
  const aboutBtn = el("button", { type: "button", class: "about", title: "About this setting", "aria-label": "About " + (sp.label || key) }, icon("info"));
  aboutBtn.addEventListener("click", () => aboutPop(aboutBtn, file, path, help, d));
  const resetBtn = el("button", { type: "button", class: "about", title: "Reset to the default", "aria-label": "Reset to the default" }, icon("reset"));
  resetBtn.addEventListener("click", () => { S.set(file, path, JSON.parse(JSON.stringify(d))); const n = field(file, path, opts); row.replaceWith(n); sp.onChange?.(d); });
  const lab = el("div", { class: "lab" }, el("label", { class: "name" }, sp.label || humanize(key)), aboutBtn, resetBtn, sp.hint ? el("div", { class: "hint" }, sp.hint) : null);
  const ctl = el("div", { class: "ctl" });
  switch (type) {
    case "bool": ctl.append(switchCtl(!!v0, (c) => write(c), sp.label || key)); break;
    case "seg":
    case "select": {
      const options = sp.options || optionsFromHelp(help, v0) || [String(v0)];
      const conv = (v) => (v === "" ? undefined : v === "true" ? true : v === "false" ? false : typeof d === "number" && !isNaN(+v) ? +v : v);
      ctl.append(type === "seg" ? seg(options, String(v0 ?? ""), (v) => write(conv(v))) : select(options, String(v0 ?? ""), (v) => write(conv(v))));
      break;
    }
    case "number": ctl.append(numberCtl(v0, write, sp)); break;
    case "duration": ctl.append(durationCtl(v0, write, sp)); break;
    case "time": {
      const t = el("input", { type: "time", value: String(v0 ?? "00:00") });
      t.addEventListener("input", () => { if (t.value) write(t.value); });
      ctl.append(t);
      break;
    }
    case "date": {
      const t = el("input", { type: "date", value: String(v0 ?? "") });
      t.addEventListener("input", () => { if (t.value) write(t.value); });
      ctl.append(t);
      break;
    }
    case "zone": ctl.append(zoneCtl(v0, write)); break;
    case "mini": ctl.append(miniCtl(v0, write, { vars: sp.vars, placeholder: sp.placeholder, emptyText: file.startsWith("lang/") ? "Empty: never sent" : "" })); break;
    case "lore": ctl.append(miniCtl(Array.isArray(v0) ? v0 : v0 ? [v0] : [], write, { list: true, vars: sp.vars })); break;
    case "list": ctl.append(chips(Array.isArray(v0) ? v0 : [], write, { suggestions: sp.suggestions, placeholder: sp.placeholder })); break;
    case "statuses": ctl.append(statusPicker(Array.isArray(v0) ? v0 : [], write)); break;
    case "status": ctl.append(select(["", ...Object.keys(S.get("statuses.yml", "statuses") || {})].map((n) => [n, n || "None"]), String(v0 ?? ""), (v) => write(v || ""))); break;
    case "material": ctl.append(materialCtl(v0, write)); break;
    case "materials": ctl.append(materialsCtl(Array.isArray(v0) ? v0 : [], write)); break;
    case "sound": ctl.append(soundCtl(v0, write, sp)); break;
    case "color": ctl.append(colorCtl(String(v0 ?? ""), write)); break;
    case "bosscolor": ctl.append(colorCtl(String(v0 ?? ""), write, { names: ["pink", "blue", "red", "green", "yellow", "purple", "white"] })); break;
    case "yaml": {
      const ta = el("textarea", { spellcheck: "false", rows: Math.min(16, Math.max(3, toYamlText(v0).split("\n").length + 1)) });
      ta.value = toYamlText(v0);
      const err = el("div", { class: "err" });
      ta.addEventListener("input", () => { try { const v = fromYamlText(ta.value); err.textContent = ""; write(v); } catch (x) { err.textContent = "Not valid YAML yet: " + x.message; } });
      ctl.append(ta, err);
      break;
    }
    default: {
      const inp = el("input", { type: "text", spellcheck: "false", placeholder: sp.placeholder || "", style: type === "mono" ? { fontFamily: "var(--mono)", fontSize: "13.5px" } : null });
      inp.value = v0 ?? "";
      inp.addEventListener("input", () => write(inp.value));
      ctl.append(inp);
    }
  }
  if (sp.render) ctl.append(sp.render());
  row.append(lab, ctl);
  function refresh() {
    const now = S.get(file, path);
    row.classList.toggle("unsaved", JSON.stringify(now) !== JSON.stringify(S.savedValue(file, path)));
    resetBtn.hidden = d === undefined || JSON.stringify(now ?? d) === JSON.stringify(d);
  }
  refresh();
  return row;
}
export const humanize = (k) => String(k).replace(/[-_]/g, " ").replace(/\bpvp\b/gi, "PvP").replace(/\bafk\b/gi, "AFK").replace(/^\w/, (c) => c.toUpperCase());

/** Rows for every option under a section, except those in `skip` (for parts no page lays out by hand). */
export function allFields(file, base, { skip = [], deep = true } = {}) {
  const box = el("div", { class: "sheet" });
  const seen = new Set();
  const keysOf = (p) => {
    const a = S.get(file, p), b = S.def(file, p);
    return [...new Set([...(isObj(b) ? Object.keys(b) : []), ...(isObj(a) ? Object.keys(a) : [])])];
  };
  const walk = (p, depth) => {
    for (const k of keysOf(p)) {
      const path = p ? p + "." + k : k;
      if (skip.some((s) => path === s || path.startsWith(s + "."))) continue;
      if (seen.has(path)) continue; seen.add(path);
      const v = S.get(file, path) ?? S.def(file, path);
      const sp = specFor(file, path);
      if (isObj(v) && deep && depth < 4 && !sp.type) { box.append(subhead(sp.label || humanize(k))); walk(path, depth + 1); }
      else box.append(field(file, path));
    }
  };
  walk(base, 0);
  if (!box.children.length) return el("div");
  return box;
}

/** Scrolls to and flashes a field, given "file#path". */
export function highlight(key) {
  const row = document.querySelector(`[data-key="${CSS.escape(key)}"]`);
  if (!row) return false;
  row.closest("details")?.setAttribute("open", "");
  row.scrollIntoView({ behavior: "smooth", block: "center" });
  row.classList.remove("flash"); void row.offsetWidth; row.classList.add("flash");
  return true;
}

/** An empty page or section: what it is, why it's empty, and what to do next. */
export function emptyBox(spr, title, text, ...actions) {
  return el("div", { class: "emptybox" }, sprite(spr, 40), el("div", { class: "grow" }, el("h3", {}, title), text ? el("p", {}, text) : null, actions.filter(Boolean).length ? el("div", { class: "row" }, actions) : null));
}
/** Copies text and says so. */
export function copyText(text, what) {
  navigator.clipboard?.writeText(text).then(() => toast("Copied " + (what || text)), () => toast("Couldn't copy. Select it and copy by hand.", "err"));
}

/** Each page's icon: a Minecraft item. */
export const ICONS = {
  home: "filled_map", live: "beacon", players: "player_head", cycle: "clock", trophy: "gold_ingot", history: "book", audit: "spyglass", effects: "potion",
  console: "command_block", vault: "ender_chest", score: "experience_bottle", outcome: "totem_of_undying", shop: "emerald", title: "name_tag", trade: "bundle",
  status: "redstone_torch", plug: "comparator", hook: "tripwire_hook", sidebar: "oak_sign", menu: "chest", lang: "writable_book", reset: "crafting_table",
  learn: "enchanted_book", guide: "map", recipe: "knowledge_book", glossary: "written_book", docs: "bookshelf", why: "iron_door", cmd: "chain_command_block",
  ph: "item_frame", action: "firework_rocket", formula: "brewing_stand", mini: "glow_ink_sac", options: "chiseled_bookshelf", problems: "barrier",
  review: "feather", backups: "barrel", io: "hopper", raw: "paper", reference: "lectern",
};
export { stripMini };
