// The action builder: edits any action list (hooks, outcomes, shop entries, named lists) as cards.
import { el, btn, badge, drawer, closeDrawer, seg, switchCtl, chips, toast, promptDialog, confirmDialog, toYamlText, fromYamlText, SAMPLE, prefix, ICONS } from "./ui.js";
import { renderMini, isObj, clone, parseDuration, formatDuration, stripMini } from "./core.js";
import * as S from "./store.js";

const COMMON = ["duration", "time", "stacking", "key", "chance", "delay", "repeat", "conditions", "start-message", "end-message"];
const NUM = new Set(["amount", "amplifier", "seconds", "ticks", "count", "volume", "pitch", "power", "radius", "charges", "points", "percent", "priority",
  "food", "exhaustion", "luck", "claims", "progress", "speed", "height", "x", "z", "min-radius", "chance", "weight"]);
const BOOL = new Set(["lock", "restore", "revert", "take-back", "ambient", "particles", "icon", "announce", "equip", "require-online", "levels", "console", "glint", "return"]);
const ENUMS = {
  time: ["real", "online"], stacking: ["refresh", "extend", "stack", "ignore"], state: ["on", "off"], when: ["now", "next-cycle"], cycle: ["current", "next"],
  color: ["pink", "blue", "red", "green", "yellow", "purple", "white"], overlay: ["progress", "notched_6", "notched_10", "notched_12", "notched_20"],
  direction: ["promote", "demote"], which: ["equipped", "all"], from: ["inventory", "enderchest", "both"], slot: ["head", "chest", "legs", "feet", "offhand"],
  operation: ["add_number", "add_scalar", "multiply_scalar_1"],
};
const MINI = new Set(["text", "title", "subtitle", "message", "start-message", "end-message", "reason", "name"]);
const LISTS = new Set(["commands", "deny", "worlds", "items", "allowed-commands", "revert-commands"]);
const YAMLS = new Set(["conditions", "actions", "choices", "item", "random", "display"]);
/** Settings the docs table doesn't spell out, by type. */
const EXTRA = {
  "give-item": ["material", "item", "amount", "to", "take-back"], "take-item": ["material", "name-contains", "lore-contains", "custom", "amount", "from", "return"],
  equip: ["slot", "material", "item", "lock"], teleport: ["to", "world", "random", "return"], deny: ["deny", "commands", "allowed-commands", "items", "worlds", "message"],
  "give-heads": ["owner", "amount", "cycle", "to"], console: ["command", "commands", "revert-commands", "require-online"], "player-command": ["command", "commands", "revert-commands"],
  run: ["list", "actions"], random: ["choices"], potion: ["effect", "amplifier", "seconds", "ambient", "particles", "icon"], points: ["amount", "when", "revert"],
  "next-cycle": ["starting-heads", "vault-size"], rollover: ["percent", "add"], bounty: ["points", "actions", "claims", "announce"], pvp: ["state", "lock", "restore"],
};
const GROUP_ICON = { "Messages and effects": "✉", Commands: "›", Items: "▣", Currency: "◎", "Permissions and ranks": "⚿", Titles: "✎", Moderation: "⚖", PvP: "⚔",
  "Buffs and debuffs": "✧", Headcount: "☻", Flow: "↻" };

export function types() {
  const map = {};
  for (const a of S.store.catalog?.actions || []) map[a.type] = a;
  map.run ??= { type: "run", main: "list", group: "Flow", settings: ["list", "actions"], settingsText: "list: <name>, or actions: [...]" };
  map.random ??= { type: "random", main: "choices", group: "Flow", settings: ["choices"], settingsText: "choices: [{weight, actions | list}]" };
  return map;
}
export const listNames = () => Object.keys(S.get("actions.yml", "lists") || {});

/** An action list entry as a card model: {form, type, data}. */
export function normalize(item) {
  const T = types();
  if (typeof item === "string") {
    const m = /^([a-z][a-z0-9-]*):\s*(.*)$/s.exec(item);
    if (m && T[m[1]]) return { form: "keyed", type: m[1], data: { [T[m[1]].main || m[1]]: m[2] } };
    if (listNames().includes(item)) return { form: "ref", type: "run", data: { list: item } };
    if (T[item]) return { form: "typed", type: item, data: {} };
    return { form: "ref", type: "run", data: { list: item } };
  }
  if (isObj(item)) {
    if (item.type) { const { type, ...rest } = item; return { form: "typed", type: String(type), data: rest }; }
    const t = Object.keys(item).find((k) => T[k]);
    if (t) { const { [t]: mv, ...rest } = item; return { form: "keyed", type: t, data: { [T[t].main || t]: mv, ...rest } }; }
    return { form: "typed", type: "?", data: item };
  }
  return { form: "typed", type: "?", data: {} };
}
export function denormalize(c) {
  const T = types();
  if (c.form === "ref" && c.type === "run" && Object.keys(c.data).length === 1 && c.data.list) return c.data.list;
  if (c.form === "keyed") {
    const main = T[c.type]?.main || c.type;
    const { [main]: mv, ...rest } = c.data;
    if (Object.keys(rest).length === 0 && typeof mv === "string" && !mv.includes("\n")) return { [c.type]: mv };
    return { [c.type]: mv, ...rest };
  }
  return { type: c.type, ...c.data };
}

/** A one-line summary of an action, for cards and lists. */
export function summarize(item) {
  const c = normalize(item);
  if (c.form === "ref") return `run list "${c.data.list}"`;
  const T = types()[c.type];
  const main = T?.main ? c.data[T.main] : undefined;
  let s = c.type;
  if (main !== undefined && main !== null) s += ": " + (Array.isArray(main) ? main.map(stripMini).join(" / ") : stripMini(typeof main === "object" ? JSON.stringify(main) : main));
  if (c.data.duration) s += ` (${c.data.duration})`;
  return s.length > 90 ? s.slice(0, 87) + "…" : s;
}

function kindOf(key, value) {
  if (key === "duration") return "duration";
  if (typeof value === "boolean" || BOOL.has(key)) return "bool";
  if (typeof value === "number" || NUM.has(key)) return "number";
  if (ENUMS[key]) return "enum";
  if (Array.isArray(value) ? value.every((x) => !isObj(x)) && LISTS.has(key) : LISTS.has(key)) return "list";
  if (isObj(value) || Array.isArray(value) || YAMLS.has(key)) return "yaml";
  if (MINI.has(key) || /<[a-z#\/]/i.test(String(value ?? ""))) return "mini";
  if (key === "material") return "material";
  return "text";
}

/** The duration picker: instant | a time | cycle | cycles:N | death | permanent | manual. */
function durationCtl(value, onChange) {
  const v = value === undefined || value === null ? "instant" : String(value);
  const mode = ["instant", "cycle", "death", "permanent", "manual"].includes(v) ? v : v.startsWith("cycles:") ? "cycles" : "time";
  const box = el("div");
  const extra = el("div", { style: { marginTop: "6px" } });
  const drawExtra = (m, cur) => {
    extra.innerHTML = "";
    if (m === "time") {
      const inp = el("input", { type: "text", value: mode === "time" ? v : "30m", style: { maxWidth: "160px" } });
      const hint = el("span", { class: "small muted", style: { marginLeft: "8px" } });
      const upd = () => { const ms = parseDuration(inp.value); hint.textContent = isNaN(ms) ? "e.g. 30s, 10m, 2h, 3d, 1h30m" : "= " + formatDuration(ms); };
      inp.addEventListener("input", () => { upd(); if (!isNaN(parseDuration(inp.value))) onChange(inp.value); });
      upd();
      extra.append(el("div", { class: "row" }, inp, hint));
    } else if (m === "cycles") {
      const inp = el("input", { type: "number", min: 1, value: mode === "cycles" ? v.slice(7) : 2, style: { maxWidth: "100px" } });
      inp.addEventListener("input", () => { if (+inp.value >= 1) onChange("cycles:" + Math.floor(+inp.value)); });
      extra.append(el("div", { class: "row" }, inp, el("span", { class: "small muted" }, "cycle ends")));
    }
  };
  const help = { instant: "happens once", time: "for a while, then undone", cycle: "until this cycle ends", cycles: "for N cycle ends", death: "until the player dies",
    permanent: "never undone", manual: "until an admin removes it" };
  const note = el("div", { class: "small muted", style: { marginTop: "4px" } }, help[mode]);
  box.append(seg(["instant", "time", "cycle", "cycles", "death", "permanent", "manual"], mode, (m) => {
    note.textContent = help[m];
    drawExtra(m);
    if (m === "time") onChange("30m"); else if (m === "cycles") onChange("cycles:2"); else onChange(m === "instant" ? undefined : m);
  }), extra, note);
  drawExtra(mode);
  return box;
}

function settingRow(key, value, onChange, { main } = {}) {
  const kind = kindOf(key, value);
  const ctl = el("div", { class: "ctl" });
  let preview = null;
  switch (kind) {
    case "duration": ctl.append(durationCtl(value, onChange)); break;
    case "bool": ctl.append(switchCtl(!!value, onChange)); break;
    case "number": { const i = el("input", { type: "number", step: "any", value: value ?? "" }); i.addEventListener("input", () => onChange(i.value === "" ? undefined : +i.value)); ctl.append(i); break; }
    case "enum": ctl.append(el("select", { onchange: (e) => onChange(e.target.value || undefined) }, el("option", { value: "" }, "(default)"), ENUMS[key].map((o) => el("option", { value: o, selected: String(value) === o }, o)))); break;
    case "list": ctl.append(chips(Array.isArray(value) ? value : value ? [value] : [], (v) => onChange(v.length ? v : undefined))); break;
    case "yaml": {
      const ta = el("textarea", { rows: Math.min(12, Math.max(3, toYamlText(value).split("\n").length + 1)), spellcheck: "false" });
      ta.value = toYamlText(value);
      const err = el("div", { class: "small", style: { color: "var(--bad)" } });
      ta.addEventListener("input", () => { try { const v = ta.value.trim() ? fromYamlText(ta.value) : undefined; err.textContent = ""; onChange(v); } catch (x) { err.textContent = x.message; } });
      ctl.append(ta, err);
      if (key === "actions") ctl.append(el("div", { class: "small muted" }, "A nested action list (YAML)."));
      break;
    }
    default: {
      const multi = Array.isArray(value) || (typeof value === "string" && value.includes("\n"));
      const inp = multi ? el("textarea", { rows: 3 }) : el("input", { type: "text", list: kind === "material" ? "materials" : null });
      inp.value = Array.isArray(value) ? value.join("\n") : value ?? "";
      if (kind === "mini") preview = el("div", { class: "mc", style: { marginTop: "5px" } });
      const upd = () => { if (preview) preview.innerHTML = renderMini(inp.value, { vars: SAMPLE, prefix: prefix() }); };
      inp.addEventListener("input", () => {
        upd();
        const v = inp.value;
        onChange(Array.isArray(value) || (multi && v.includes("\n") && key === "text") ? v.split("\n") : v === "" ? undefined : v);
      });
      upd();
      ctl.append(inp);
      if (preview) ctl.append(preview);
    }
  }
  return el("div", { class: "field" }, el("div", {}, el("div", { class: "lbl" }, key, main ? el("span", { class: "badge plain", style: { marginLeft: "6px" } }, "main") : null)), ctl);
}

/** A type picker with search, grouped. */
function typePicker(onPick) {
  const T = types();
  const q = el("input", { type: "search", placeholder: "Search 60 action types…" });
  const list = el("div", { style: { maxHeight: "58vh", overflow: "auto", marginTop: "8px" } });
  const draw = () => {
    list.innerHTML = "";
    const groups = {};
    for (const a of Object.values(T)) {
      if (q.value && !(a.type + " " + a.settingsText + " " + a.group).toLowerCase().includes(q.value.toLowerCase())) continue;
      (groups[a.group] ||= []).push(a);
    }
    for (const [g, as] of Object.entries(groups)) {
      list.append(el("div", { class: "small", style: { fontWeight: 700, color: "var(--muted)", margin: "10px 0 4px", textTransform: "uppercase", letterSpacing: ".05em" } }, g));
      for (const a of as) {
        list.append(el("div", { class: "card click", style: { padding: "8px 12px", margin: "4px 0" }, onclick: () => onPick(a.type) },
          el("div", { class: "row" }, el("span", { class: "typeicon" }, GROUP_ICON[a.group] || "⚡"), el("b", {}, a.type), el("span", { class: "small muted grow" }, a.settingsText.slice(0, 110)))));
      }
    }
    // Named lists can be run too.
    const names = listNames().filter((n) => !q.value || n.includes(q.value));
    if (names.length) {
      list.append(el("div", { class: "small", style: { fontWeight: 700, color: "var(--muted)", margin: "10px 0 4px", textTransform: "uppercase" } }, "Run a named list"));
      for (const n of names) list.append(el("div", { class: "card click", style: { padding: "8px 12px", margin: "4px 0" }, onclick: () => onPick("@" + n) }, el("b", {}, n), " ", el("span", { class: "small muted" }, "from actions.yml")));
    }
  };
  q.addEventListener("input", draw);
  draw();
  setTimeout(() => q.focus(), 30);
  return el("div", {}, q, list);
}

/**
 * Opens the action builder for the list at file/path. A string value (a named list's name) offers to edit that
 * list instead. Changes apply as you make them (undo with Ctrl+Z).
 */
export function editActions(file, path, { title, hint, vars } = {}) {
  const raw = S.get(file, path);
  if (typeof raw === "string" && listNames().includes(raw)) {
    const body = el("div", {}, el("p", {}, "This runs the named list ", el("b", {}, raw), " from actions.yml, which other places may use too."),
      el("div", { class: "row" }, btn("Edit the list " + raw, () => editActions("actions.yml", "lists." + raw, { title: "List: " + raw }), "primary"),
        btn("Copy it here instead", () => { S.set(file, path, clone(S.get("actions.yml", "lists." + raw)) || []); editActions(file, path, { title, hint }); })));
    drawer(title || "Actions", body, { subtitle: file + " › " + path });
    return;
  }
  let cards = (Array.isArray(raw) ? raw : raw === undefined || raw === null ? [] : [raw]).map(normalize);
  const openSet = new Set();
  const commit = () => { S.set(file, path, cards.map(denormalize), file + "#" + path + "#actions"); };
  const body = el("div");
  let yamlMode = false;
  const draw = () => {
    body.innerHTML = "";
    if (hint) body.append(el("div", { class: "note", style: { marginBottom: "10px" } }, hint));
    if (yamlMode) {
      const ta = el("textarea", { rows: 24, spellcheck: "false" });
      ta.value = toYamlText(cards.map(denormalize));
      const err = el("div", { class: "small", style: { color: "var(--bad)" } });
      ta.addEventListener("input", () => { try { const v = ta.value.trim() ? fromYamlText(ta.value) : []; cards = (Array.isArray(v) ? v : [v]).map(normalize); err.textContent = ""; commit(); } catch (x) { err.textContent = x.message; } });
      body.append(ta, err);
      return;
    }
    if (!cards.length) body.append(el("div", { class: "empty" }, "No actions yet. Add one below."));
    let dragIdx = null;
    cards.forEach((c, idx) => {
      const T = types()[c.type];
      const isOpen = openSet.has(idx);
      const card = el("div", { class: "actioncard", draggable: "true" });
      card.addEventListener("dragstart", () => { dragIdx = idx; card.classList.add("drag"); });
      card.addEventListener("dragend", () => card.classList.remove("drag"));
      card.addEventListener("dragover", (e) => { e.preventDefault(); card.classList.add("over"); });
      card.addEventListener("dragleave", () => card.classList.remove("over"));
      card.addEventListener("drop", (e) => { e.preventDefault(); card.classList.remove("over"); if (dragIdx !== null && dragIdx !== idx) { const [m] = cards.splice(dragIdx, 1); cards.splice(idx, 0, m); openSet.clear(); commit(); draw(); } });
      const head = el("div", { class: "ah", onclick: () => { isOpen ? openSet.delete(idx) : openSet.add(idx); draw(); } },
        el("span", { class: "grip", title: "Drag to reorder" }, "⠿"), el("span", { class: "typeicon" }, c.form === "ref" ? "↻" : GROUP_ICON[T?.group] || "⚡"),
        el("div", { class: "grow" }, el("div", { style: { fontWeight: 600 } }, summarize(denormalize(c))),
          T && T.online ? el("div", { class: "small faint" }, (T.online.startsWith("yes") ? "player must be online" : "works for offline players") + (T.undo && T.undo !== "-" ? " · undo: " + T.undo : "")) : null),
        c.data.chance !== undefined && c.data.chance < 100 ? badge(c.data.chance + "%", "plain") : null,
        c.data.duration ? badge(String(c.data.duration), "info") : null,
        el("button", { class: "iconbtn", title: "Duplicate", onclick: (e) => { e.stopPropagation(); cards.splice(idx + 1, 0, clone(c)); commit(); draw(); } }, "⧉"),
        el("button", { class: "iconbtn", title: "Delete", onclick: (e) => { e.stopPropagation(); cards.splice(idx, 1); openSet.clear(); commit(); draw(); } }, "🗑"));
      card.append(head);
      if (isOpen) {
        const ab = el("div", { class: "ab" });
        if (c.form === "ref") {
          ab.append(el("div", { class: "field" }, el("div", { class: "lbl" }, "List"),
            el("div", { class: "ctl" }, el("select", { onchange: (e) => { c.data.list = e.target.value; commit(); draw(); } }, listNames().map((n) => el("option", { value: n, selected: n === c.data.list }, n))),
              el("div", { class: "meta" }, el("button", { onclick: () => editActions("actions.yml", "lists." + c.data.list, { title: "List: " + c.data.list }) }, "edit this list")))));
        } else {
          ab.append(el("div", { class: "field" }, el("div", { class: "lbl" }, "Type"), el("div", { class: "ctl" },
            el("select", { onchange: (e) => { c.type = e.target.value; c.form = "typed"; commit(); draw(); } }, Object.keys(types()).sort().map((t) => el("option", { value: t, selected: t === c.type }, t))),
            T ? el("div", { class: "small muted", style: { marginTop: "4px" } }, T.settingsText) : el("div", { class: "small", style: { color: "var(--bad)" } }, "Unknown action type."))));
          const keys = [...new Set([T?.main, ...(T?.settings || []), ...(EXTRA[c.type] || []), ...Object.keys(c.data)].filter(Boolean))].filter((k) => !COMMON.includes(k));
          for (const k of keys) ab.append(settingRow(k, c.data[k], (v) => { if (v === undefined) delete c.data[k]; else c.data[k] = v; commit(); refreshHead(); }, { main: k === T?.main }));
          const opt = el("details", { class: "more" }, el("summary", {}, "Duration, chance, delay, repeat, conditions, messages"));
          for (const k of COMMON) opt.append(settingRow(k, c.data[k], (v) => { if (v === undefined) delete c.data[k]; else c.data[k] = v; commit(); refreshHead(); }));
          if (COMMON.some((k) => c.data[k] !== undefined)) opt.open = true;
          ab.append(opt);
        }
        card.append(ab);
      }
      const refreshHead = () => { head.querySelector(".grow > div").textContent = summarize(denormalize(c)); };
      body.append(card);
    });
    body.append(el("div", { class: "row", style: { marginTop: "10px" } }, btn("＋ Add action", () => {
      const d = drawer("Add an action", typePicker((t) => {
        closeDrawer();
        if (t.startsWith("@")) cards.push({ form: "ref", type: "run", data: { list: t.slice(1) } });
        else {
          const T = types()[t];
          const data = {};
          if (T?.main) data[T.main] = /text|title|message/.test(T.main) ? "<yellow>Hello <player>!" : "";
          cards.push({ form: T?.main ? "keyed" : "typed", type: t, data });
        }
        openSet.add(cards.length - 1);
        commit();
        reopen();
      }), { onClose: () => {} });
    }, "primary")));
  };
  let reopen;
  const footer = el("div", { class: "row", style: { width: "100%" } },
    el("span", { class: "small muted grow" }, "Changes apply as you edit. Ctrl+Z undoes."),
    seg([["cards", "Cards"], ["yaml", "YAML"]], "cards", (m) => { yamlMode = m === "yaml"; draw(); }),
    btn("Done", () => closeDrawer(), "primary"));
  reopen = () => { drawer(title || "Actions", body, { subtitle: file + " › " + path, footer }); draw(); };
  reopen();
}

/** Every place an action list name is used (string items, run actions, outcome/shop "actions: name"). */
export function listUsages(name) {
  const out = [];
  const scan = (file, v, path) => {
    if (typeof v === "string") { if (v === name && /actions$|\.\d+$|lists\./.test(path)) out.push({ file, path }); return; }
    if (Array.isArray(v)) v.forEach((x, i) => scan(file, x, path + "." + i));
    else if (isObj(v)) for (const [k, x] of Object.entries(v)) { if (k === "list" && x === name) out.push({ file, path }); scan(file, x, path ? path + "." + k : k); }
  };
  for (const f of ["config.yml", "outcomes.yml", "shop.yml", "actions.yml", "integrations.yml"]) scan(f, S.doc(f)?.data, "");
  return out.filter((u) => !(u.file === "actions.yml" && u.path === "lists." + name));
}
