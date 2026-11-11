// Shared components: elements, buttons, fields bound to config paths, Minecraft-style previews (text, item
// tooltips, chest menus), drawers, dialogs, toasts.
import { renderMini, esc, materialLook, prettyMaterial, parseDuration, formatDuration, dumpEntry, dumpList, readYaml, isObj } from "./core.js";
import * as S from "./store.js";

export function el(tag, attrs = {}, ...kids) {
  // A node, text or list where the attributes go is content (el("div", child) works too).
  if (attrs !== null && attrs !== undefined && (attrs.nodeType || typeof attrs !== "object" || Array.isArray(attrs))) { kids.unshift(attrs); attrs = {}; }
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") n.className = v;
    else if (k === "html") n.innerHTML = v;
    else if (k === "style" && typeof v === "object") Object.assign(n.style, v);
    else if (k.startsWith("on") && typeof v === "function") n.addEventListener(k.slice(2), v);
    else n.setAttribute(k, v === true ? "" : v);
  }
  for (const k of kids.flat(Infinity)) if (k !== null && k !== undefined && k !== false) n.append(k.nodeType ? k : document.createTextNode(String(k)));
  return n;
}
export const frag = (...kids) => { const f = document.createDocumentFragment(); for (const k of kids.flat(Infinity)) if (k != null && k !== false) f.append(k.nodeType ? k : document.createTextNode(String(k))); return f; };
export const btn = (label, onclick, cls = "") => el("button", { class: "btn " + cls, onclick }, label);
export const badge = (text, cls = "") => el("span", { class: "badge " + cls }, text);
export const sectionTitle = (t, ...extra) => el("div", { class: "section-title" }, t, ...extra);

/** Sample values used to preview text: variables (<player>) and placeholders (%headcount_x% as "%x"). */
export const SAMPLE = {
  player: "Steve", other: "Alex", target: "Alex", killer: "Alex", victim: "Steve", owner: "Steve", cycle: 3, current_cycle: 3, points: 12, points_next: 0,
  score: 4, unique: 7, total: 21, own: 9, size: 27, used: 21, rank: 2, percentile: 80, streak: 2, streak_text: "2 positive", time_left: "4d 6h",
  cycle_start: "2026-11-01 00:00", cycle_end: "2026-12-01 00:00", cycle_length: 30, active: 12, players: 18, seconds: 12, countdown: "58s",
  countdown_clock: "0:58", duration: "30m", remaining: "12m", price: 10, name: "Speed for 30 minutes", tier: "Uncommon", reason: "Testing",
  head_id_short: "3f9a1c2e", head_id: "3f9a1c2e-…", created: "2026-11-10 14:02", source: "cycle-start", page: 1, pages: 2, amount: 3,
  title: "<gold>[Champion]", description: "Finished a cycle in the top 3.", status: "in-pvp", command: "/home", state: "on", tagged: "no",
  "%cycle": 3, "%unique": 7, "%total": 21, "%score": 4, "%points": 12, "%streak_text": "2 positive", "%time_left": "4d 6h", "%vault_size": 27,
  "%rank": 2, "%title": "[Champion]", "%points_next": 0, "%active": 12,
};
export const prefix = () => S.lang("prefix") ?? "";

/** A Minecraft text preview of MiniMessage. */
export function mcText(src, opts = {}) {
  return el("div", { class: "mc " + (opts.class || ""), html: renderMini(src ?? "", { vars: { ...SAMPLE, ...(opts.vars || {}) }, prefix: prefix() }) });
}
export const miniInline = (src, vars) => el("span", { class: "mcline", html: renderMini(src ?? "", { vars: { ...SAMPLE, ...(vars || {}) }, prefix: prefix(), base: "#3f3f3f" }) });

/** An item tile (colored by material) with an optional count. */
export function itemTile(spec, opts = {}) {
  const mat = isObj(spec) ? spec.material || (spec.head || spec.custom ? "PLAYER_HEAD" : "STONE") : spec;
  const look = materialLook(mat);
  const t = el("div", { class: "tile" + (isObj(spec) && spec.glint ? " glint" : ""), style: { background: look.color }, title: prettyMaterial(mat) }, look.glyph);
  if (opts.size) Object.assign(t.style, { width: opts.size + "px", height: opts.size + "px", fontSize: Math.round(opts.size / 2) + "px" });
  return t;
}

/** The in-game item tooltip: name and lore lines. */
export function tooltip(name, lore, vars) {
  const box = el("div", { class: "tooltip-mc" });
  box.append(el("div", { class: "ln", html: renderMini(name ?? "", { vars: { ...SAMPLE, ...(vars || {}) }, base: "#FFFFFF" }) }));
  for (const l of [].concat(lore || [])) for (const part of String(l).split("\n")) box.append(el("div", { class: "ln", html: renderMini(part, { vars: { ...SAMPLE, ...(vars || {}) }, base: "#AA00AA" }) }));
  return box;
}

let tipEl = null;
/** Shows `content()` next to the mouse while hovering `target`. */
export function hoverTip(target, content) {
  target.addEventListener("mouseenter", () => {
    tipEl?.remove();
    tipEl = el("div", { class: "floattip" }, content());
    document.body.append(tipEl);
  });
  target.addEventListener("mousemove", (e) => {
    if (!tipEl) return;
    const w = tipEl.offsetWidth, h = tipEl.offsetHeight;
    tipEl.style.left = Math.min(window.innerWidth - w - 8, e.clientX + 16) + "px";
    tipEl.style.top = Math.min(window.innerHeight - h - 8, e.clientY + 12) + "px";
  });
  target.addEventListener("mouseleave", () => { tipEl?.remove(); tipEl = null; });
}

/**
 * A chest menu. slots(i) returns {item, tip, onclick, cls, count} or null for an empty slot. Optional drag between
 * slots: onDrop(from, to).
 */
export function chest(rows, titleSrc, slots, opts = {}) {
  const grid = el("div", { class: "slots" });
  let dragFrom = null;
  for (let i = 0; i < rows * 9; i++) {
    const s = slots(i) || {};
    const cell = el("div", { class: "slot " + (s.cls || ""), title: s.title || null, "data-slot": i });
    if (s.item) cell.append(itemTile(s.item));
    if (s.count > 1) cell.append(el("span", { class: "cnt" }, s.count));
    if (s.tip) hoverTip(cell, s.tip);
    cell.addEventListener("click", () => s.onclick ? s.onclick(i) : opts.onEmpty?.(i));
    if (opts.onDrop) {
      if (s.draggable) { cell.draggable = true; cell.addEventListener("dragstart", (e) => { dragFrom = i; e.dataTransfer.effectAllowed = "move"; }); }
      cell.addEventListener("dragover", (e) => { if (dragFrom !== null) { e.preventDefault(); cell.classList.add("drop"); } });
      cell.addEventListener("dragleave", () => cell.classList.remove("drop"));
      cell.addEventListener("drop", (e) => { e.preventDefault(); cell.classList.remove("drop"); if (dragFrom !== null && dragFrom !== i) opts.onDrop(dragFrom, i); dragFrom = null; });
    }
    grid.append(cell);
  }
  return el("div", { class: "chest" }, el("div", { class: "ctitle" }, miniInline(titleSrc, opts.vars)), grid);
}

// ---------------------------------------------------------------- toasts, dialogs, drawers
export function toast(msg, kind = "") {
  let box = document.querySelector(".toasts");
  if (!box) { box = el("div", { class: "toasts" }); document.body.append(box); }
  const t = el("div", { class: "toast " + kind }, msg);
  box.append(t);
  setTimeout(() => t.remove(), kind === "err" ? 9000 : 4500);
}

export function confirmDialog(title, body, { ok = "OK", danger = false, cancel = "Cancel" } = {}) {
  return new Promise((resolve) => {
    const scrim = el("div", { class: "scrim" });
    const close = (v) => { scrim.remove(); m.remove(); document.removeEventListener("keydown", key); resolve(v); };
    const key = (e) => { if (e.key === "Escape") close(false); if (e.key === "Enter") close(true); };
    const m = el("div", { class: "modal" }, el("h3", {}, title), typeof body === "string" ? el("p", { class: "muted" }, body) : body,
      el("div", { class: "row", style: { justifyContent: "flex-end", marginTop: "14px" } }, btn(cancel, () => close(false)), btn(ok, () => close(true), danger ? "danger" : "primary")));
    scrim.addEventListener("click", () => close(false));
    document.addEventListener("keydown", key);
    document.body.append(scrim, m);
  });
}

export function promptDialog(title, label, value = "", { ok = "OK", check } = {}) {
  return new Promise((resolve) => {
    const inp = el("input", { type: "text", value });
    const err = el("div", { class: "small", style: { color: "var(--bad)", minHeight: "18px", marginTop: "4px" } });
    const scrim = el("div", { class: "scrim" });
    const close = (v) => { scrim.remove(); m.remove(); resolve(v); };
    const done = () => { const v = inp.value.trim(); const problem = check?.(v); if (problem) { err.textContent = problem; return; } close(v); };
    inp.addEventListener("keydown", (e) => { if (e.key === "Enter") done(); if (e.key === "Escape") close(null); });
    const m = el("div", { class: "modal" }, el("h3", {}, title), el("div", { class: "muted small", style: { marginBottom: "6px" } }, label), inp, err,
      el("div", { class: "row", style: { justifyContent: "flex-end", marginTop: "10px" } }, btn("Cancel", () => close(null)), btn(ok, done, "primary")));
    scrim.addEventListener("click", () => close(null));
    document.body.append(scrim, m);
    setTimeout(() => { inp.focus(); inp.select(); }, 20);
  });
}

let openDrawer = null;
export function drawer(title, body, { footer, onClose, subtitle } = {}) {
  closeDrawer();
  const scrim = el("div", { class: "scrim" });
  const d = el("div", { class: "drawer" },
    el("header", {}, el("div", { class: "grow" }, el("h2", {}, title), subtitle ? el("div", { class: "muted small" }, subtitle) : null), el("button", { class: "iconbtn", title: "Close (Esc)", onclick: () => closeDrawer() }, "✕")),
    el("div", { class: "db" }, body), footer ? el("footer", {}, footer) : null);
  scrim.addEventListener("click", () => closeDrawer());
  document.body.append(scrim, d);
  openDrawer = { close: () => { scrim.remove(); d.remove(); onClose?.(); } };
  return openDrawer;
}
export function closeDrawer() { if (openDrawer) { const o = openDrawer; openDrawer = null; o.close(); return true; } return false; }

// ---------------------------------------------------------------- controls
export function switchCtl(checked, onChange) {
  const cb = el("input", { type: "checkbox" });
  cb.checked = !!checked;
  cb.addEventListener("change", () => onChange(cb.checked));
  return el("label", { class: "switch" }, cb, el("span"));
}
export function seg(options, value, onChange) {
  const box = el("div", { class: "seg" });
  const draw = (v) => {
    box.innerHTML = "";
    for (const o of options) {
      const [val, label] = Array.isArray(o) ? o : [o, o];
      box.append(el("button", { class: String(val) === String(v) ? "on" : "", onclick: () => { draw(val); onChange(val); } }, label));
    }
  };
  draw(value);
  return box;
}
export function chips(items, onChange, { placeholder, suggestions } = {}) {
  const list = (items || []).map(String);
  const box = el("div", { class: "chips" });
  const dl = suggestions ? el("datalist", { id: "dl" + Math.random().toString(36).slice(2) }, suggestions.map((s) => el("option", { value: s }))) : null;
  const draw = () => {
    box.innerHTML = "";
    list.forEach((it, idx) => box.append(el("span", { class: "chip" }, it, el("button", { title: "Remove", onclick: () => { list.splice(idx, 1); onChange(list.slice()); draw(); } }, "×"))));
    const inp = el("input", { type: "text", placeholder: placeholder || (list.length ? "add…" : "type and press Enter"), list: dl?.id });
    inp.addEventListener("keydown", (ev) => {
      if ((ev.key === "Enter" || ev.key === ",") && inp.value.trim()) { ev.preventDefault(); list.push(inp.value.trim()); onChange(list.slice()); draw(); box.querySelector("input").focus(); }
      if (ev.key === "Backspace" && !inp.value && list.length) { list.pop(); onChange(list.slice()); draw(); box.querySelector("input").focus(); }
    });
    inp.addEventListener("change", () => { if (suggestions?.includes(inp.value.trim())) { list.push(inp.value.trim()); onChange(list.slice()); draw(); } });
    box.append(inp);
    if (dl) box.append(dl);
  };
  draw();
  return box;
}

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

/**
 * A form row bound to a config option. opts: label, help, type (bool | number | enum | duration | text | mini |
 * multiline | list | yaml | material | time | date), options, min, max, step, suffix, placeholder, suggestions,
 * onChange(value). The row shows its default with a reset, and marks unsaved changes.
 */
export function field(file, path, opts = {}) {
  const e = S.entry(file, path);
  const value = S.get(file, path);
  const d = S.def(file, path);
  const probe = value !== undefined ? value : d;
  const help = opts.help ?? e?.help ?? S.doc(file)?.defParsed.index.get(path)?.help ?? "";
  let type = opts.type;
  if (!type) {
    if (typeof probe === "boolean") type = "bool";
    else if (typeof probe === "number") type = "number";
    else if (Array.isArray(probe) && probe.every((x) => !isObj(x) && !Array.isArray(x))) type = "list";
    else if (isObj(probe) || Array.isArray(probe)) type = "yaml";
    else if (opts.options || optionsFromHelp(help, probe)) type = "enum";
    else if (typeof probe === "string" && /^\d+(\.\d+)?(ms|s|m|h|d|w)/.test(probe) && !isNaN(parseDuration(probe))) type = "duration";
    else if (looksMini(probe)) type = "mini";
    else type = "text";
  }
  const row = el("div", { class: "field" + (opts.stack ? " stack" : ""), "data-key": file + "#" + path });
  const keyName = path.split(".").pop();
  const label = el("div", {}, el("div", { class: "lbl" }, opts.label || humanize(keyName), opts.label ? el("code", {}, keyName) : null));
  if (help && opts.help !== false) label.append(el("div", { class: "help" }, help));
  const ctl = el("div", { class: "ctl" });
  const meta = el("div", { class: "meta" });
  const write = (v) => { S.set(file, path, v); refresh(); opts.onChange?.(v); };
  const cur = () => S.get(file, path);
  let preview = null;
  const v0 = value !== undefined ? value : d;
  switch (type) {
    case "bool": {
      const lab = el("span", { class: "muted small", style: { marginLeft: "8px" } }, v0 ? "on" : "off");
      ctl.append(el("div", { class: "row" }, switchCtl(!!v0, (c) => { lab.textContent = c ? "on" : "off"; write(c); }), lab));
      break;
    }
    case "number": {
      const inp = el("input", { type: "number", value: v0 ?? "", min: opts.min, max: opts.max, step: opts.step ?? "any" });
      inp.addEventListener("input", () => { if (inp.value.trim() !== "" && !isNaN(+inp.value)) write(+inp.value); });
      ctl.append(opts.suffix ? el("span", { class: "suffix" }, inp, el("span", {}, opts.suffix)) : inp);
      if (opts.slider) {
        const r = el("input", { type: "range", min: opts.min ?? 0, max: opts.max ?? 100, step: opts.step ?? 1, value: v0 ?? 0, style: { width: "100%", marginTop: "6px" } });
        r.addEventListener("input", () => { inp.value = r.value; write(+r.value); });
        inp.addEventListener("input", () => { r.value = inp.value; });
        ctl.append(r);
      }
      break;
    }
    case "enum": {
      const options = opts.options || optionsFromHelp(help, v0) || [String(v0)];
      // "" clears the option (the default applies); "true"/"false" are written as YAML booleans.
      const conv = (v) => (v === "" ? undefined : v === "true" ? true : v === "false" ? false : typeof d === "number" && !isNaN(+v) ? +v : v);
      if (options.length <= 5 && !opts.select) ctl.append(seg(options, String(v0 ?? ""), (v) => write(conv(v))));
      else ctl.append(el("select", { onchange: (ev) => write(conv(ev.target.value)) }, options.map((o) => { const [val, lab] = Array.isArray(o) ? o : [o, o]; return el("option", { value: val, selected: String(val) === String(v0) }, lab); })));
      break;
    }
    case "list": {
      ctl.append(chips(Array.isArray(v0) ? v0 : [], (items) => write(items), { suggestions: opts.suggestions, placeholder: opts.placeholder }));
      break;
    }
    case "yaml": {
      const ta = el("textarea", { spellcheck: "false", rows: Math.min(16, Math.max(3, toYamlText(v0).split("\n").length + 1)) });
      ta.value = toYamlText(v0);
      const err = el("div", { class: "small", style: { color: "var(--bad)" } });
      ta.addEventListener("input", () => { try { const v = fromYamlText(ta.value); err.textContent = ""; write(v); } catch (x) { err.textContent = "Not valid yet: " + x.message; } });
      ctl.append(ta, err);
      break;
    }
    case "multiline":
    case "mini":
    case "text":
    case "material":
    case "duration":
    case "time":
    case "date": {
      const multi = type === "multiline" || (typeof v0 === "string" && v0.includes("\n"));
      const inp = multi ? el("textarea", { rows: 3 }) : el("input", { type: type === "time" ? "time" : type === "date" ? "date" : "text", spellcheck: "false", placeholder: opts.placeholder || "", list: type === "material" ? "materials" : opts.list || null });
      inp.value = v0 ?? "";
      const hint = el("span", { class: "small muted" });
      const upd = () => {
        if (type === "duration") { const ms = parseDuration(inp.value); hint.textContent = isNaN(ms) ? "not a duration (30s, 10m, 2h, 3d, 1w, 1h30m)" : "= " + formatDuration(ms); hint.style.color = isNaN(ms) ? "var(--bad)" : ""; }
        if (preview) preview.innerHTML = renderMini(inp.value, { vars: { ...SAMPLE, ...(opts.vars || {}) }, prefix: prefix() });
        if (tileBox) { tileBox.innerHTML = ""; tileBox.append(itemTile(inp.value, { size: 26 })); }
      };
      let tileBox = null;
      if (type === "material") { tileBox = el("span"); ctl.append(el("div", { class: "row", style: { flexWrap: "nowrap" } }, tileBox, inp)); } else ctl.append(inp);
      if (type === "duration") ctl.append(hint);
      if (type === "mini" || opts.preview) { preview = el("div", { class: "mc", style: { marginTop: "6px" } }); ctl.append(preview); }
      inp.addEventListener("input", () => { upd(); write(inp.value); });
      upd();
      break;
    }
    default: ctl.append(el("div", { class: "muted" }, String(v0)));
  }
  ctl.append(meta);
  row.append(label, ctl);
  function refresh() {
    meta.innerHTML = "";
    const now = cur();
    const unsaved = JSON.stringify(now) !== JSON.stringify(S.savedValue(file, path));
    row.classList.toggle("unsaved", unsaved);
    if (unsaved) meta.append(badge("unsaved", "warn"));
    if (d !== undefined && JSON.stringify(now) !== JSON.stringify(d)) {
      const show = isObj(d) || Array.isArray(d) ? (Array.isArray(d) && d.every((x) => !isObj(x)) ? "[" + d.join(", ") + "]" : "(a block)") : JSON.stringify(d);
      meta.append(el("span", {}, "default: ", el("code", {}, show.length > 60 ? show.slice(0, 57) + "…" : show)),
        el("button", { onclick: () => { S.set(file, path, JSON.parse(JSON.stringify(d))); row.replaceWith(field(file, path, opts)); } }, "reset"));
    }
    if (value === undefined && d !== undefined) meta.append(el("span", {}, "not in your file yet (the default applies)"));
  }
  refresh();
  return row;
}
export const humanize = (k) => String(k).replace(/[-_]/g, " ").replace(/^\w/, (c) => c.toUpperCase());

/** A group of fields for every option under a section (skipping some), for the "All options" parts of pages. */
export function allFields(file, base, { skip = [], deep = true } = {}) {
  const box = el("div");
  const d = S.doc(file);
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
      if (isObj(v) && deep && depth < 4) {
        const g = el("div", { style: { margin: "10px 0 4px" } }, el("div", { class: "small", style: { fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: ".05em" } }, path));
        const help = d.parsed.index.get(path)?.help || d.defParsed.index.get(path)?.help;
        if (help) g.append(el("div", { class: "small muted", style: { whiteSpace: "pre-wrap" } }, help));
        box.append(g);
        walk(path, depth + 1);
      } else box.append(field(file, path));
    }
  };
  walk(base, 0);
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

export const ICONS = {
  home: "⌂", live: "◉", players: "☻", cycle: "◷", trophy: "♛", history: "☰", audit: "⌕", effects: "✧", console: "›_",
  vault: "▣", score: "∑", outcome: "⚖", shop: "⛁", title: "✎", trade: "⇄", status: "◐", plug: "⚙", hook: "⚓", sidebar: "▤", menu: "▦",
  lang: "✉", reset: "↺", learn: "✿", guide: "➜", recipe: "✚", glossary: "Aa", docs: "❏", why: "?", cmd: "/", ph: "%", action: "⚡",
  formula: "ƒ", mini: "✐", options: "≡", problems: "⚠", review: "✓", backups: "⧖", io: "⇅", raw: "{ }",
};
