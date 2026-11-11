// The action builder: edits any action list (hooks, outcomes, shop items, named lists) as cards, one per action,
// with controls that fit each setting. It can sit inside another editor (actionList) or open in a drawer
// (editActions). Also the conditions editor and the score-range control that rules and actions share.
import { plain, el, btn, icon, iconBtn, drawer, closeDrawer, seg, select, switchCtl, chips, statusPicker, materialCtl, soundCtl, colorCtl, miniCtl, durationCtl,
  numberCtl, toast, promptDialog, toYamlText, fromYamlText, sprite, humanize } from "./ui.js";
import { isObj, clone, parseDuration, formatDuration, stripMini, prettyMaterial } from "./core.js";
import * as S from "./store.js";

const COMMON = ["duration", "time", "stacking", "key", "chance", "delay", "repeat", "conditions", "start-message", "end-message"];
const NUM = new Set(["amplifier", "seconds", "ticks", "count", "volume", "pitch", "power", "radius", "charges", "points", "percent", "priority",
  "food", "exhaustion", "luck", "claims", "progress", "speed", "height", "x", "z", "min-radius", "chance", "weight", "offset", "fade-in-ticks"]);
const BOOL = new Set(["lock", "restore", "revert", "take-back", "ambient", "particles", "icon", "announce", "equip", "require-online", "levels", "console", "glint", "return", "damage", "value"]);
const MINI = new Set(["text", "title", "subtitle", "message", "start-message", "end-message", "reason", "name", "prefix", "suffix"]);
const LISTS = new Set(["commands", "worlds", "items", "allowed-commands", "revert-commands"]);
const YAMLS = new Set(["choices", "item", "random", "display", "pdc", "enchants"]);
const OPEN = new Set(["audience", "to", "countdown-to", "amount", "owner", "starting-heads", "vault-size"]);
const EFFECTS = ["speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage", "jump_boost", "nausea", "regeneration", "resistance",
  "fire_resistance", "water_breathing", "invisibility", "blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost", "absorption", "saturation",
  "glowing", "levitation", "luck", "unluck", "slow_falling", "conduit_power", "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness", "trial_omen", "raid_omen",
  "wind_charged", "weaving", "oozing", "infested"];
/** Settings the docs table doesn't spell out, by type. */
const EXTRA = {
  "give-item": ["material", "item", "amount", "to", "take-back"], "take-item": ["material", "name-contains", "lore-contains", "custom", "amount", "from", "return"],
  equip: ["slot", "material", "item", "lock"], teleport: ["to", "world", "random", "return"], deny: ["deny", "commands", "allowed-commands", "items", "worlds", "message"],
  "give-heads": ["owner", "amount", "cycle", "to"], console: ["command", "commands", "revert-commands", "require-online"], "player-command": ["command", "commands", "revert-commands"],
  run: ["list"], random: ["choices"], potion: ["effect", "amplifier", "seconds", "ambient", "particles", "icon"], points: ["amount", "when", "revert"],
  "next-cycle": ["starting-heads", "vault-size"], rollover: ["percent", "add"], bounty: ["points", "actions", "claims", "announce"], pvp: ["state", "lock", "restore"],
};
const DESC = {
  message: "Chat message", broadcast: "Message to everyone", title: "Big text on screen", actionbar: "Text above the hotbar", sound: "Play a sound",
  particle: "Particles around them", bossbar: "Boss bar at the top", discord: "Post to Discord", "broadcast-location": "Announce where they are",
  console: "Run a console command", "player-command": "They run a command", "give-item": "Give an item", "take-item": "Take items away", equip: "Put an item on them",
  "give-heads": "Give economy heads", loot: "Roll a loot table", experience: "Give or take XP", points: "Give or take points", money: "Give or take money",
  permission: "Add or remove a permission", group: "Add or remove a group", "primary-group": "Set the main group", track: "Promote or demote", prefix: "Set a chat prefix",
  suffix: "Set a chat suffix", "grant-title": "Give a title", "revoke-title": "Take a title", "strip-title": "Unequip titles", "force-title": "Lock a title on them",
  ban: "Ban them", mute: "Mute them", warn: "Warn them", kick: "Kick them", jail: "Send them to jail", pvp: "Switch their PvP", potion: "Potion effect",
  attribute: "Change an attribute", glowing: "Make them glow", fire: "Set them on fire", lightning: "Strike lightning", freeze: "Freeze them", hunger: "Change hunger",
  gamemode: "Change game mode", teleport: "Teleport them", heal: "Heal them", feed: "Feed them", damage: "Hurt them", launch: "Launch them upward",
  "clear-effects": "Remove potion effects", deny: "Block things they do", "next-cycle": "Change their next cycle", rollover: "Keep more points next cycle",
  "head-protection": "Keep their head on death", "extra-heads": "Drop more heads on death", bounty: "Put a bounty on them", shield: "Block the next punishment",
  flag: "Set or clear a flag", "score-bonus": "Add to this cycle's score", run: "Run an action list", random: "Pick one at random",
};
const SPRITE = {
  message: "paper", broadcast: "bell", title: "item_frame", actionbar: "oak_sign", sound: "note_block", particle: "blaze_powder", bossbar: "ender_eye", discord: "amethyst_shard",
  "broadcast-location": "compass", console: "command_block", "player-command": "feather", "give-item": "chest", "take-item": "hopper", equip: "iron_chestplate",
  "give-heads": "player_head", loot: "chest", experience: "experience_bottle", points: "sunflower", money: "gold_nugget", permission: "trial_key", group: "lead",
  "primary-group": "lead", track: "rail", prefix: "name_tag", suffix: "name_tag", "grant-title": "name_tag", "revoke-title": "shears", "strip-title": "shears",
  "force-title": "carved_pumpkin", ban: "barrier", mute: "white_wool", warn: "goat_horn", kick: "iron_boots", jail: "iron_bars", pvp: "iron_sword", potion: "potion",
  attribute: "golden_apple", glowing: "glowstone_dust", fire: "flint_and_steel", lightning: "trident", freeze: "powder_snow_bucket", hunger: "rotten_flesh",
  gamemode: "grass_block", teleport: "ender_pearl", heal: "glistering_melon_slice", feed: "cooked_beef", damage: "cactus", launch: "wind_charge", "clear-effects": "milk_bucket",
  deny: "structure_void", "next-cycle": "clock", rollover: "chest_minecart", "head-protection": "turtle_helmet", "extra-heads": "skeleton_skull", bounty: "crossbow",
  shield: "iron_chestplate", flag: "redstone", "score-bonus": "lapis_lazuli", run: "repeater", random: "rabbit_foot",
};

export function types() {
  const map = {};
  for (const a of S.store.catalog?.actions || []) map[a.type] = a;
  map.run ??= { type: "run", main: "list", group: "Flow", settings: ["list", "actions"], settingsText: "list: <name>, or actions: [...]" };
  map.random ??= { type: "random", main: "choices", group: "Flow", settings: ["choices"], settingsText: "choices: [{weight, actions | list}]" };
  return map;
}
export const listNames = () => Object.keys(S.get("actions.yml", "lists") || {});

/** Each setting's choices and default, read from the catalog's settings text. */
const parsed = new Map();
function settingsOf(type) {
  if (parsed.has(type)) return parsed.get(type);
  const text = types()[type]?.settingsText || "";
  const out = {};
  for (const m of text.matchAll(/`([a-z0-9-]+)`:\s*((?:`[^`]+`(?:\s*\([^)]*\))?(?:,\s*|\s+or\s+|,\s*or\s+)?)+)/g)) out[m[1]] = { options: [...m[2].matchAll(/`([^`]+)`/g)].map((x) => x[1]) };
  for (const m of text.matchAll(/`([a-z0-9-]+):\s*([^`]+)`/g)) if (!out[m[1]]) out[m[1]] = { def: m[2].replace(/^"|"$/g, "") };
  parsed.set(type, out);
  return out;
}

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

/** A sound event as words: "minecraft:entity.player.levelup" reads "Player levelup". */
export const prettySound = (k) => { const parts = String(k || "").replace(/^minecraft:/, "").split("."); if (parts.length > 1 && /^(entity|block|item|ui|ambient|music|event|weather|enchant|particle)$/.test(parts[0])) parts.shift(); return humanize(parts.join(" ").replace(/_/g, " ")); };
const ROMAN = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"];
const titleName = (id) => stripMini(S.get("titles.yml", "titles." + id + ".display") || id);
const count = (n, one, many) => (n === undefined || n === null || n === "" ? many : String(n).startsWith("-") ? String(n).slice(1) + " " + (+String(n).slice(1) === 1 ? one : many) : n + " " + (+n === 1 ? one : many));
const sign = (n) => (String(n ?? "").trim().startsWith("-") ? "Take" : "Give");
/** A one-line summary of an action, for cards and lists, in words rather than config values. */
export function summarize(item) {
  const c = normalize(item);
  if (c.form === "ref") return `Run the list ${c.data.list}`;
  const d = c.data, T = types()[c.type];
  let main = T?.main ? d[T.main] : undefined;
  if (isObj(main)) main = main.amount ?? main.value; // shorthand like points: {amount: 5, when: next-cycle}
  const text = (v) => (Array.isArray(v) ? v.map(plain).join(" / ") : plain(typeof v === "object" ? "" : v));
  const item2 = isObj(d.item) ? d.item : {};
  let s;
  switch (c.type) {
    case "message": case "broadcast": case "actionbar": case "bossbar": case "discord": { const t = main ?? d.text ?? d.message; s = (DESC[c.type] || c.type) + (t ? ": " + text(t) : ""); break; }
    case "particle": s = "Particles: " + humanize(String(main || d.particle || "").replace(/^minecraft:/, "").replace(/_/g, " ")).toLowerCase(); break;
    case "next-cycle": { const bits = []; if (d["starting-heads"] !== undefined) bits.push(d["starting-heads"] + " starting heads"); if (d["vault-size"] !== undefined) bits.push(d["vault-size"] + " vault slots"); s = "Next cycle: " + (bits.join(", ") || "changes"); break; }
    case "title": s = "Title: " + text(d.title ?? "") + (d.subtitle ? " / " + text(d.subtitle) : ""); break;
    case "sound": s = d.sound ? "Sound: " + prettySound(d.sound) : "Play a sound"; break;
    case "give-item": case "take-item": { const m = d.material || item2.material; const n = d.amount ?? item2.amount; s = (c.type === "give-item" ? "Give " : "Take ") + (n > 1 ? n + " " : "") + (item2.name ? plain(item2.name) : m ? prettyMaterial(m) : "an item"); break; }
    case "equip": s = "Put on " + (item2.name ? plain(item2.name) : prettyMaterial(d.material || item2.material || "an item")); break;
    case "potion": s = (d.effect ? humanize(String(d.effect).replace(/^minecraft:/, "").replace(/_/g, " ")) : "Potion effect") + (d.amplifier ? " " + (ROMAN[+d.amplifier] || +d.amplifier + 1) : ""); break;
    case "points": case "experience": case "money": case "score-bonus": s = (c.type === "score-bonus" ? (String(main ?? "").startsWith("-") ? "Lower the score by " : "Raise the score by ") + String(main ?? "?").replace(/^-/, "") : sign(main) + " " + count(main, c.type === "money" ? "coin" : c.type === "experience" ? "XP" : "point", c.type === "money" ? "coins" : c.type === "experience" ? "XP" : "points")); break;
    case "give-heads": s = "Give " + count(main ?? 1, "head", "heads") + (d.cycle === "next" ? " for next cycle" : ""); break;
    case "grant-title": s = "Give the title " + titleName(main); break;
    case "revoke-title": s = "Take the title " + titleName(main); break;
    case "force-title": s = "Lock the title " + titleName(main) + " on"; break;
    case "console": s = "Console: /" + String(main ?? (d.commands || [])[0] ?? "").replace(/^\//, ""); break;
    case "player-command": s = "They run /" + String(main ?? (d.commands || [])[0] ?? "").replace(/^\//, ""); break;
    case "pvp": s = "PvP " + (main || "on") + (d.lock ? ", locked" : ""); break;
    case "bounty": s = "Bounty of " + count(main, "point", "points"); break;
    case "shield": case "head-protection": s = DESC[c.type] + (main > 1 ? " (" + main + ")" : ""); break;
    case "run": s = "Run the list " + (main || "?"); break;
    case "random": s = "Pick one of " + [].concat(main || []).length; break;
    case "permission": case "group": case "primary-group": case "track": s = DESC[c.type] + (main ? ": " + main : ""); break;
    default: s = DESC[c.type] ? DESC[c.type] + (main !== undefined && main !== null && main !== "" && typeof main !== "object" ? ": " + text(main) : "") : c.type;
  }
  return s.length > 90 ? s.slice(0, 87) + "..." : s;
}
/** The sprite that stands for an action: the item it gives or puts on, else one per type. */
export function actionSprite(item) {
  const c = normalize(item);
  if (c.form === "ref") return "repeater";
  const m = c.data.material || (isObj(c.data.item) && c.data.item.material);
  if (["give-item", "equip", "take-item"].includes(c.type) && m) return m;
  if (c.type === "potion") return { material: "POTION" };
  return SPRITE[c.type] || "paper";
}
const DUR_WORD = { cycle: "until the cycle ends", death: "until they die", permanent: "for good", manual: "until removed" };
function meta(c) {
  const d = c.data;
  const out = [];
  if (d.duration !== undefined && d.duration !== "instant") out.push(DUR_WORD[d.duration] || (String(d.duration).startsWith("cycles:") ? "for " + String(d.duration).slice(7) + " cycles" : "for " + d.duration));
  if (d.chance !== undefined && +d.chance < 100) out.push(d.chance + "% chance");
  if (d.delay) out.push("after " + d.delay);
  if (d.repeat) out.push("every " + d.repeat);
  if (d.conditions) out.push("only when " + Object.keys(d.conditions).join(", "));
  return out.join(", ");
}

// ---------------------------------------------------------------- shared controls
/** A score range ("..-1", "1..3", ">=2", "5") as a comparison and numbers. */
export function rangeCtl(value, onChange) {
  const s = String(value ?? "").trim();
  let mode = "custom", a = "", b = "";
  const opStyle = /^[<>!=]/.test(s);
  let m;
  if (/^-?\d+(\.\d+)?$/.test(s)) { mode = "eq"; a = s; }
  else if ((m = /^(-?\d+(?:\.\d+)?)\.\.$/.exec(s)) || (m = /^>=\s*(-?\d+(?:\.\d+)?)$/.exec(s))) { mode = "ge"; a = m[1]; }
  else if ((m = /^\.\.(-?\d+(?:\.\d+)?)$/.exec(s)) || (m = /^<=\s*(-?\d+(?:\.\d+)?)$/.exec(s))) { mode = "le"; a = m[1]; }
  else if ((m = /^(-?\d+(?:\.\d+)?)\.\.(-?\d+(?:\.\d+)?)$/.exec(s))) { mode = "between"; a = m[1]; b = m[2]; }
  else if ((m = /^>\s*(-?\d+(?:\.\d+)?)$/.exec(s))) { mode = "gt"; a = m[1]; }
  else if ((m = /^<\s*(-?\d+(?:\.\d+)?)$/.exec(s))) { mode = "lt"; a = m[1]; }
  else if ((m = /^!=\s*(-?\d+(?:\.\d+)?)$/.exec(s))) { mode = "ne"; a = m[1]; }
  else if (!s) { mode = "ge"; a = "1"; }
  const box = el("div", { class: "row", style: { flexWrap: "nowrap" } });
  const write = () => {
    const A = String(+a || 0), B = String(+b || 0);
    const v = { eq: A, ge: opStyle ? ">=" + A : A + "..", le: opStyle ? "<=" + A : ".." + A, between: A + ".." + B, gt: ">" + A, lt: "<" + A, ne: "!=" + A }[mode];
    if (v !== undefined) onChange(v);
  };
  const draw = () => {
    box.innerHTML = "";
    box.append(select([["eq", "Exactly"], ["ge", "At least"], ["le", "At most"], ["between", "From"], ["gt", "Above"], ["lt", "Below"], ["ne", "Not"], ["custom", "Written as"]], mode, (v) => {
      if (mode === "custom" && v !== "custom") a = a || "0";
      mode = v; draw(); if (v !== "custom") write();
    }));
    if (mode === "custom") { const t = el("input", { type: "text", value: s, style: { width: "140px" }, spellcheck: "false" }); t.addEventListener("input", () => onChange(t.value)); box.append(t); return; }
    const n1 = el("input", { type: "number", value: a, style: { width: "90px" }, "aria-label": "Value" });
    n1.addEventListener("input", () => { a = n1.value; write(); });
    box.append(n1);
    if (mode === "between") {
      const n2 = el("input", { type: "number", value: b, style: { width: "90px" }, "aria-label": "To" });
      n2.addEventListener("input", () => { b = n2.value; write(); });
      box.append(el("span", { class: "muted" }, "to"), n2);
    }
  };
  draw();
  return box;
}

const COND = [["statuses", "Has every status"], ["any-statuses", "Has one of"], ["score", "Score"], ["streak", "Streak"], ["rank", "Rank"], ["points", "Points"],
  ["unique", "Unique heads"], ["active", "Was active"], ["permission", "Has permission"], ["expression", "Formula is true"]];
/** The conditions of a rule or action as rows, one per condition, with an add menu. */
export function conditionsEditor(value, onChange) {
  let cond = isObj(value) ? { ...value } : {};
  const box = el("div", { class: "cond" });
  const write = () => onChange(Object.keys(cond).length ? { ...cond } : undefined);
  const draw = () => {
    box.innerHTML = "";
    for (const k of Object.keys(cond)) {
      const label = COND.find((c) => c[0] === k)?.[1] || humanize(k);
      const set = (v) => { if (v === undefined) delete cond[k]; else cond[k] = v; write(); };
      let ctl;
      if (k === "statuses" || k === "any-statuses") ctl = statusPicker([].concat(cond[k] || []), (v) => set(v));
      else if (["score", "streak", "rank", "points", "unique"].includes(k)) ctl = rangeCtl(cond[k], set);
      else if (k === "active") ctl = seg([[true, "Yes"], [false, "No"]], String(cond[k] !== false), (v) => set(v === true || v === "true"));
      else if (k === "expression" || k === "permission") { ctl = el("input", { type: "text", value: cond[k] ?? "", spellcheck: "false", style: { fontFamily: "var(--mono)", fontSize: "13.5px" } }); ctl.addEventListener("input", () => set(ctl.value)); }
      else { ctl = el("textarea", { rows: 2, spellcheck: "false" }); ctl.value = toYamlText(cond[k]); ctl.addEventListener("input", () => { try { set(fromYamlText(ctl.value)); } catch (e) { /* not valid yet */ } }); }
      box.append(el("div", { class: "crow" }, el("b", {}, label), el("div", { class: "grow" }, ctl), iconBtn("close", "Remove this condition", () => { delete cond[k]; write(); draw(); }, "sm")));
    }
    const free = COND.filter(([k]) => !(k in cond));
    box.append(el("div", {}, select(free.map(([k, l]) => [k, l]), "", (k) => { if (!k) return; cond[k] = k === "active" ? true : k.includes("statuses") ? [] : ["score", "streak", "rank", "points", "unique"].includes(k) ? "1.." : ""; write(); draw(); },
      { placeholder: Object.keys(cond).length ? "Add a condition" : "Always. Add a condition" })));
  };
  draw();
  return box;
}
export function conditionsCtl(file, path) { return conditionsEditor(S.get(file, path), (v) => S.set(file, path, v)); }

/** An action duration: once, a time, until the cycle ends, N cycles, until death, for good, or until removed. */
function actionDuration(value, onChange) {
  const v = value === undefined || value === null ? "instant" : String(value);
  const mode = ["instant", "cycle", "death", "permanent", "manual"].includes(v) ? v : v.startsWith("cycles:") ? "cycles" : "time";
  const box = el("div", { style: { display: "grid", gap: "8px", width: "100%" } });
  const extra = el("div");
  const drawExtra = (m) => {
    extra.innerHTML = "";
    if (m === "time") extra.append(durationCtl(mode === "time" ? v : "30m", (x) => onChange(x), { units: ["s", "m", "h", "d"] }));
    if (m === "cycles") extra.append(numberCtl(mode === "cycles" ? +v.slice(7) : 2, (n) => { if (n >= 1) onChange("cycles:" + Math.floor(n)); }, { unit: "cycle ends", min: 1, step: 1 }));
  };
  box.append(select([["instant", "Once, nothing to undo"], ["time", "For a while"], ["cycle", "Until the cycle ends"], ["cycles", "For a number of cycles"], ["death", "Until they die"],
    ["permanent", "For good"], ["manual", "Until an admin removes it"]], mode, (m) => {
    drawExtra(m);
    if (m === "time") onChange("30m"); else if (m === "cycles") onChange("cycles:2"); else onChange(m === "instant" ? undefined : m);
  }), extra);
  drawExtra(mode);
  return box;
}

function settingRow(type, key, value, onChange, { main, vars } = {}) {
  const info = settingsOf(type)[key] || {};
  const ctl = el("div", { class: "ctl" });
  const titles = Object.keys(S.get("titles.yml", "titles") || {});
  const set = (v) => onChange(v === "" ? undefined : v);
  if (key === "duration") ctl.append(actionDuration(value, onChange));
  else if (key === "chance") ctl.append(numberCtl(value ?? 100, (v) => onChange(v >= 100 ? undefined : v), { unit: "%", min: 0, max: 100, step: 1, slider: [0, 100] }));
  else if (key === "delay" || key === "repeat" || (["fade-in", "stay", "fade-out", "seconds"].includes(key) && typeof value === "string")) {
    ctl.append(value === undefined ? btn([icon("plus"), key === "delay" ? "Add a delay" : key === "repeat" ? "Repeat it" : "Set"], () => onChange(key === "delay" ? "5s" : key === "repeat" ? "5m" : "1s"), "ghost small")
      : el("div", { class: "row" }, durationCtl(value, onChange, { units: ["s", "m", "h", "d"] }), iconBtn("close", "Remove", () => onChange(undefined), "sm")));
  }
  else if (key === "time") ctl.append(seg([["real", "Real time"], ["online", "Only while online"]], value ?? "real", (v) => onChange(v === "real" ? undefined : v)));
  else if (key === "stacking") ctl.append(select([["refresh", "Restart its timer"], ["extend", "Add the time"], ["stack", "Run another copy"], ["ignore", "Do nothing"]], value ?? "refresh", (v) => onChange(v === "refresh" ? undefined : v)));
  else if (key === "conditions") ctl.append(conditionsEditor(value, onChange));
  else if (key === "list") ctl.append(select(listNames(), value ?? "", set, { placeholder: "Pick a list" }));
  else if (key === "title" && ["grant-title", "revoke-title", "force-title"].includes(type)) ctl.append(select(titles, value ?? "", set, { placeholder: "Pick a title" }));
  else if (key === "effect" && type === "potion") ctl.append(select(EFFECTS.map((e) => [e, humanize(e)]), value ?? "", set, { placeholder: "Pick an effect" }));
  else if (key === "sound") ctl.append(soundCtl(value, set));
  else if (key === "material") ctl.append(materialCtl(value, set, { allowEmpty: true }));
  else if (key === "items" && type === "deny") ctl.append(chips([].concat(value ?? []), (v) => onChange(v.length ? v : undefined), { placeholder: "MATERIAL", suggestions: [] }));
  else if (key === "color") ctl.append(colorCtl(value ?? "", set, { names: ["pink", "blue", "red", "green", "yellow", "purple", "white"] }));
  else if (key === "deny" && info.options) {
    const cur = [].concat(value ?? []);
    const box = el("div", { class: "row gap-s" });
    for (const o of info.options) {
      const on = cur.includes(o);
      box.append(el("button", { type: "button", class: "btn small" + (on ? " primary" : ""), onclick: () => { const n = on ? cur.filter((x) => x !== o) : [...cur, o]; onChange(n.length ? n : undefined); rowEl.replaceWith(settingRow(type, key, n.length ? n : undefined, onChange)); } }, humanize(o)));
    }
    ctl.append(box);
  }
  else if (key === "actions") ctl.append(actionEditor(value, (v) => onChange(v && v.length ? v : undefined), { nested: true }));
  else if (info.options && (OPEN.has(key) || info.options.some((o) => /[<\[,]/.test(o)))) {
    const id = "o" + Math.random().toString(36).slice(2);
    const inp = el("input", { type: "text", value: value ?? "", list: id, spellcheck: "false", placeholder: info.options[0] });
    inp.addEventListener("input", () => set(inp.value));
    ctl.append(inp, el("datalist", { id }, info.options.map((o) => el("option", { value: o }))));
  }
  else if (info.options && info.options.length <= 4) ctl.append(seg(info.options.map((o) => [o, humanize(o)]), value ?? "", set));
  else if (info.options) ctl.append(select(info.options.map((o) => [o, humanize(o)]), value ?? "", set, { placeholder: "Default" }));
  else if (typeof value === "boolean" || BOOL.has(key) || info.def === "true" || info.def === "false") ctl.append(switchCtl(value ?? info.def === "true", onChange, key));
  else if (typeof value === "number" || NUM.has(key) || (info.def !== undefined && /^-?\d+(\.\d+)?$/.test(info.def))) {
    const i = el("input", { type: "number", step: "any", value: value ?? "", placeholder: info.def ?? "" });
    i.addEventListener("input", () => onChange(i.value === "" ? undefined : +i.value));
    ctl.append(i);
  }
  else if (LISTS.has(key)) ctl.append(chips([].concat(value ?? []), (v) => onChange(v.length ? v : undefined)));
  else if (YAMLS.has(key) || isObj(value) || (Array.isArray(value) && value.some(isObj))) {
    const ta = el("textarea", { rows: Math.min(12, Math.max(3, toYamlText(value).split("\n").length + 1)), spellcheck: "false" });
    ta.value = toYamlText(value);
    const err = el("div", { class: "err" });
    ta.addEventListener("input", () => { try { const v = ta.value.trim() ? fromYamlText(ta.value) : undefined; err.textContent = ""; onChange(v); } catch (x) { err.textContent = x.message; } });
    ctl.append(ta, err);
  }
  else if (MINI.has(key) || /<[a-z#\/]/i.test(String(value ?? ""))) ctl.append(miniCtl(value ?? "", (v) => onChange(Array.isArray(v) ? (v.length ? v : undefined) : v === "" ? undefined : v), { vars, list: Array.isArray(value), multiline: key === "text" }));
  else {
    const inp = el("input", { type: "text", value: value ?? "", spellcheck: "false", placeholder: info.def ?? "", style: /command|permission|key|flag|table|group|track|node/.test(key) ? { fontFamily: "var(--mono)", fontSize: "13.5px" } : null });
    inp.addEventListener("input", () => set(inp.value));
    ctl.append(inp);
  }
  const rowEl = el("div", { class: "field" + (key === "conditions" || key === "actions" ? " stack" : "") }, el("div", { class: "lab" }, el("span", { class: "name" }, LABEL[key] || humanize(key))), ctl);
  return rowEl;
}
const LABEL = { "start-message": "When it starts", "end-message": "When it ends", time: "Time counts", stacking: "If already active", key: "Same-action key", chance: "Chance",
  delay: "Delay", repeat: "Repeat every", conditions: "Only when", text: "Text", "take-back": "Take it back when it ends", "require-online": "Wait until they're online",
  "revert-commands": "Commands to undo it", "countdown-to": "Counts down to", "fade-in": "Fade in", "fade-out": "Fade out", "min-amplifier": "Lowest level", amplifier: "Level (0 is I)" };

function typePicker(onPick, onCancel) {
  const T = types();
  const q = el("input", { type: "search", placeholder: "Search actions", spellcheck: "false" });
  const list = el("div");
  const draw = () => {
    list.innerHTML = "";
    const s = q.value.trim().toLowerCase();
    const groups = {};
    for (const a of Object.values(T)) {
      if (s && !(a.type + " " + (DESC[a.type] || "") + " " + a.group).toLowerCase().includes(s)) continue;
      (groups[a.group] ||= []).push(a);
    }
    for (const [g, as] of Object.entries(groups)) list.append(el("div", { class: "typegroup" }, g), el("div", { class: "typegrid" }, as.map((a) => el("button", { type: "button", class: "typebtn", onclick: () => onPick(a.type) },
      sprite(SPRITE[a.type] || "paper", 24), el("span", {}, el("b", {}, humanize(a.type)), el("small", {}, DESC[a.type] || a.settingsText.slice(0, 50)))))));
    const names = listNames().filter((n) => !s || n.includes(s));
    if (names.length) list.append(el("div", { class: "typegroup" }, "Your action lists"), el("div", { class: "typegrid" }, names.map((n) => el("button", { type: "button", class: "typebtn", onclick: () => onPick("@" + n) },
      sprite("repeater", 24), el("span", {}, el("b", {}, n), el("small", {}, "From actions.yml"))))));
    if (!list.children.length) list.append(el("div", { class: "empty" }, "No action matches."));
  };
  q.addEventListener("input", draw);
  draw();
  setTimeout(() => q.focus(), 30);
  return el("div", { class: "sheet", style: { padding: "12px 14px" } }, el("div", { class: "row", style: { flexWrap: "nowrap" } }, q, btn("Cancel", onCancel, "ghost")), list);
}

/**
 * An action list editor bound to a value. raw is the list (or a list name); onChange gets the new list. Used for
 * hooks, rules, shop items, named lists and nested lists (bounty actions).
 */
export function actionEditor(raw, onChange, { vars, nested } = {}) {
  const box = el("div", { class: "acts-list" });
  if (typeof raw === "string" && raw && !raw.includes(":")) {
    const draw = () => {
      box.innerHTML = "";
      box.append(el("div", { class: "sheet", style: { padding: "14px 16px" } }, el("div", { class: "row" }, sprite("repeater", 28), el("div", { class: "grow" }, "Runs the action list ", el("b", {}, raw)),
        select(listNames(), raw, (v) => { raw = v; onChange(v); draw(); })),
        el("div", { class: "row", style: { marginTop: "12px" } }, btn("Edit " + raw, () => editActions("actions.yml", "lists." + raw, { title: "Action list " + raw })),
          btn("Use its actions here instead", () => { const copy = clone(S.get("actions.yml", "lists." + raw)) || []; onChange(Array.isArray(copy) ? copy : [copy]); box.replaceWith(actionEditor(Array.isArray(copy) ? copy : [copy], onChange, { vars, nested })); }, "ghost"))));
    };
    draw();
    return box;
  }
  let cards = (Array.isArray(raw) ? raw : raw === undefined || raw === null ? [] : [raw]).map(normalize);
  const openSet = new Set();
  let yamlMode = false, picking = false;
  const commit = () => onChange(cards.map(denormalize));
  const draw = () => {
    box.innerHTML = "";
    if (yamlMode) {
      const ta = el("textarea", { rows: Math.max(8, toYamlText(cards.map(denormalize)).split("\n").length + 1), spellcheck: "false" });
      ta.value = toYamlText(cards.map(denormalize));
      const err = el("div", { class: "err" });
      ta.addEventListener("input", () => { try { const v = ta.value.trim() ? fromYamlText(ta.value) : []; cards = (Array.isArray(v) ? v : [v]).map(normalize); err.textContent = ""; commit(); } catch (x) { err.textContent = x.message; } });
      box.append(ta, err, el("div", {}, btn("Back to cards", () => { yamlMode = false; draw(); }, "ghost small")));
      return;
    }
    let dragIdx = null;
    cards.forEach((c, idx) => {
      const T = types()[c.type];
      const isOpen = openSet.has(idx);
      const card = el("div", { class: "actioncard" + (isOpen ? " open" : "") });
      const grip = el("span", { class: "grip", title: "Drag to reorder", draggable: "true" }, icon("grip"));
      grip.addEventListener("dragstart", (e) => { dragIdx = idx; card.classList.add("drag"); e.dataTransfer.setData("text/plain", String(idx)); e.dataTransfer.setDragImage(card, 20, 20); });
      grip.addEventListener("dragend", () => card.classList.remove("drag"));
      card.addEventListener("dragover", (e) => { if (dragIdx !== null) { e.preventDefault(); card.classList.add("over"); } });
      card.addEventListener("dragleave", () => card.classList.remove("over"));
      card.addEventListener("drop", (e) => { e.preventDefault(); card.classList.remove("over"); if (dragIdx !== null && dragIdx !== idx) { const [m] = cards.splice(dragIdx, 1); cards.splice(idx, 0, m); openSet.clear(); commit(); draw(); } dragIdx = null; });
      const spr = actionSprite(denormalize(c));
      const t1 = el("div", { class: "t1" }, summarize(denormalize(c)));
      const t2 = el("div", { class: "t2" }, meta(c));
      const head = el("div", { class: "ah", onclick: (e) => { if (e.target.closest("button,.grip")) return; isOpen ? openSet.delete(idx) : openSet.add(idx); draw(); } },
        grip, sprite(spr, 28), el("div", { class: "sum" }, t1, t2),
        iconBtn("up", "Move up", () => { if (idx > 0) { [cards[idx - 1], cards[idx]] = [cards[idx], cards[idx - 1]]; openSet.clear(); commit(); draw(); } }, "sm"),
        iconBtn("copy", "Duplicate", () => { cards.splice(idx + 1, 0, clone(c)); openSet.clear(); commit(); draw(); }, "sm"),
        iconBtn("trash", "Delete", () => { cards.splice(idx, 1); openSet.clear(); commit(); draw(); }, "sm"),
        el("span", { style: { color: "var(--ink-3)", display: "grid" } }, icon(isOpen ? "down" : "right")));
      card.append(head);
      const refresh = () => { t1.textContent = summarize(denormalize(c)); t2.textContent = meta(c); };
      if (isOpen) {
        const ab = el("div", { class: "ab" });
        const upd = (k) => (v) => { if (v === undefined) delete c.data[k]; else c.data[k] = v; commit(); refresh(); };
        if (c.form === "ref") ab.append(settingRow("run", "list", c.data.list, (v) => { c.data.list = v; commit(); refresh(); }));
        else {
          ab.append(el("div", { class: "field" }, el("div", { class: "lab" }, el("span", { class: "name" }, "Action")), el("div", { class: "ctl" },
            select(Object.values(types()).reduce((gs, a) => { let g = gs.find((x) => x.group === a.group); if (!g) gs.push(g = { group: a.group, items: [] }); g.items.push([a.type, humanize(a.type)]); return gs; }, []), c.type, (v) => { c.type = v; c.form = "typed"; commit(); draw(); }),
            T ? null : el("div", { class: "err" }, "Headcount has no action called " + c.type + "."))));
          const keys = [...new Set([T?.main, ...(T?.settings || []), ...(EXTRA[c.type] || []), ...Object.keys(c.data)].filter(Boolean))].filter((k) => !COMMON.includes(k));
          for (const k of keys) ab.append(settingRow(c.type, k, c.data[k], upd(k), { main: k === T?.main, vars }));
          const opt = el("details", { class: "more" }, el("summary", {}, "Timing, chance and conditions"));
          for (const k of COMMON) {
            if (["time", "stacking", "key", "repeat", "start-message", "end-message"].includes(k) && (c.data.duration === undefined || c.data.duration === "instant") && c.data[k] === undefined) continue;
            opt.append(settingRow(c.type, k, c.data[k], (v) => { upd(k)(v); if (k === "duration") draw(); }, { vars }));
          }
          if (COMMON.some((k) => c.data[k] !== undefined)) opt.open = true;
          ab.append(opt);
        }
        card.append(ab);
      }
      box.append(card);
    });
    if (picking) box.append(typePicker((t) => {
      picking = false;
      if (t.startsWith("@")) cards.push({ form: "ref", type: "run", data: { list: t.slice(1) } });
      else {
        const T = types()[t];
        const data = {};
        if (T?.main) data[T.main] = /text|title|message/.test(T.main) ? "<yellow>Hello <player>!" : "";
        cards.push({ form: T?.main ? "keyed" : "typed", type: t, data });
      }
      openSet.add(cards.length - 1);
      commit(); draw();
    }, () => { picking = false; draw(); }));
    else box.append(el("div", { class: "row" }, btn([icon("plus"), cards.length ? "Add an action" : "Add the first action"], () => { picking = true; draw(); }, nested ? "small" : ""), el("span", { class: "grow" }),
      btn("Edit as YAML", () => { yamlMode = true; draw(); }, "ghost small")));
  };
  draw();
  return box;
}

/** An action list editor for a config path. */
export function actionList(file, path, opts = {}) {
  return el("div", { "data-key": file + "#" + path }, actionEditor(S.get(file, path), (v) => S.set(file, path, v, file + "#" + path + "#actions"), opts));
}

/** Opens the action builder for the list at file/path in a drawer. */
export function editActions(file, path, { title, subtitle, onClose } = {}) {
  drawer(title || "Actions", actionList(file, path), { wide: true, subtitle, onClose,
    footer: el("div", { class: "row", style: { width: "100%" } }, el("span", { class: "grow" }), btn("Done", () => closeDrawer(), "primary")) });
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
/** Where a list is used, in words: "Used by the cycle end and the shop item Steak". */
export function usageText(uses) {
  const name = ({ file, path }) => {
    const p = path.split(".");
    if (file === "outcomes.yml") return "the outcome " + stripMini(S.get(file, "rules." + p[1] + ".display") || p[1]);
    if (file === "shop.yml" && p[0] === "tiers") return "the shop item " + stripMini(S.get(file, `tiers.${p[1]}.entries.${p[3]}.display.name`) || p[3]);
    if (file === "actions.yml") return "the list " + p[1];
    if (file === "integrations.yml") return "combat logging";
    if (p[1] === "player") return "the " + humanize(p[2]).toLowerCase() + " hook";
    if (p[1] === "warnings") return "the " + formatDuration(parseDuration(S.get(file, "hooks.warnings." + p[2] + ".before") ?? p[2])) + " warning";
    if (p[1] === "cycle-start") return "the cycle start";
    if (p[1] === "cycle-end") return "the cycle end";
    return file;
  };
  const names = [...new Set(uses.map(name))];
  if (!names.length) return "Not used yet";
  return "Used by " + (names.length <= 2 ? names.join(" and ") : names.slice(0, 2).join(", ") + " and " + (names.length - 2) + " more");
}
export { SPRITE as ACTION_SPRITES, DESC as ACTION_DESC };
