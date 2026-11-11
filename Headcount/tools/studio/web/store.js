// The app's state: every config file's text (the source of truth), its parsed lines and data, the bundled defaults,
// undo/redo across files, and saving. Pages read with get()/def() and write with set()/remove()/rename().
import { parseDoc, readYaml, setValue, removePath, renamePath, moveEntry, getIn } from "./core.js";

export const store = {
  dir: "", names: [], docs: {}, catalog: null, info: {}, live: null, history: [], future: [], listeners: new Set(),
};

export async function api(path, opts) {
  const r = await fetch(path, opts);
  const ct = r.headers.get("Content-Type") || "";
  const j = ct.includes("json") ? await r.json().catch(() => ({})) : await r.text();
  if (!r.ok) throw Object.assign(new Error((j && j.error) || r.statusText), { status: r.status, body: j });
  return j;
}
export const post = (path, body) => api(path, { method: "POST", headers: { "Content-Type": "application/json", "X-Studio": "1" }, body: JSON.stringify(body) });

function build(d, text) {
  d.text = text;
  d.parsed = parseDoc(text);
  try { d.data = readYaml(text); d.error = null; } catch (e) { d.error = e.message; }
}

export async function loadAll() {
  const [all, catalog] = await Promise.all([api("/api/all"), api("/api/catalog").catch(() => null)]);
  store.dir = all.dir;
  store.info = all;
  store.catalog = catalog;
  store.names = all.files.map((f) => f.name);
  for (const f of all.files) {
    const d = { name: f.name, exists: f.exists, mtime: f.mtime, saved: f.text ?? "", defaultText: f.defaultText ?? "" };
    build(d, f.text ?? "");
    try { d.defaults = readYaml(d.defaultText); } catch (e) { d.defaults = {}; }
    d.defParsed = parseDoc(d.defaultText);
    store.docs[f.name] = d;
  }
  emit({ type: "load" });
}

export const doc = (name) => store.docs[name];
export const get = (name, path) => getIn(store.docs[name]?.data, path);
export const def = (name, path) => getIn(store.docs[name]?.defaults, path);
export const savedValue = (name, path) => { const d = store.docs[name]; if (!d) return undefined; try { return getIn(d.savedData ??= readYaml(d.saved), path); } catch (e) { return undefined; } };
export const entry = (name, path) => store.docs[name]?.parsed.index.get(path);
export const lang = (key) => { const v = get("lang/en_US.yml", key); return Array.isArray(v) ? v.join("\n") : v; };

export function on(fn) { store.listeners.add(fn); return () => store.listeners.delete(fn); }
function emit(ev) { for (const fn of [...store.listeners]) { try { fn(ev); } catch (e) { console.error(e); } } }

let lastGroup = null, lastTime = 0;
/** Replaces a file's text as one undoable edit. Edits with the same group within a second merge (typing). */
export function setText(name, text, group) {
  const d = store.docs[name];
  if (!d || text === d.text) return;
  const now = Date.now();
  if (group && group === lastGroup && now - lastTime < 1200 && store.history.length && store.history[store.history.length - 1].name === name && !store.history[store.history.length - 1].batch) {
    store.history[store.history.length - 1].after = text;
  } else {
    store.history.push({ name, before: d.text, after: text });
    if (store.history.length > 400) store.history.shift();
  }
  lastGroup = group; lastTime = now;
  store.future = [];
  build(d, text);
  emit({ type: "change", name });
}

/** Replaces several files' text as one undoable edit (a recipe that touches more than one file). */
export function setTexts(map) {
  const items = Object.entries(map).filter(([n, t]) => store.docs[n] && store.docs[n].text !== t);
  if (!items.length) return;
  store.history.push({ name: items.map(([n]) => n).join(", "), batch: items.map(([n, t]) => ({ name: n, before: store.docs[n].text, after: t })) });
  store.future = [];
  lastGroup = null;
  for (const [n, t] of items) build(store.docs[n], t);
  for (const [n] of items) emit({ type: "change", name: n });
}

function edit(name, fn, group) {
  const d = store.docs[name];
  if (!d) throw new Error("no file " + name);
  setText(name, fn(d.text), group);
}
export const set = (name, path, value, group) => edit(name, (t) => setValue(t, path, value), group ?? name + "#" + path);
export const remove = (name, path) => edit(name, (t) => removePath(t, path), null);
export const rename = (name, path, key) => edit(name, (t) => renamePath(t, path, key), null);
export const move = (name, path, sibling, before) => edit(name, (t) => moveEntry(t, path, sibling, before), null);

export function undo() {
  const h = store.history.pop();
  if (!h) return false;
  store.future.push(h);
  for (const x of h.batch || [h]) build(store.docs[x.name], x.before);
  lastGroup = null;
  for (const x of h.batch || [h]) emit({ type: "change", name: x.name, undo: true });
  return h.name;
}
export function redo() {
  const h = store.future.pop();
  if (!h) return false;
  store.history.push(h);
  for (const x of h.batch || [h]) build(store.docs[x.name], x.after);
  lastGroup = null;
  for (const x of h.batch || [h]) emit({ type: "change", name: x.name, undo: true });
  return h.name;
}

export const dirtyNames = () => store.names.filter((n) => store.docs[n].text !== store.docs[n].saved);
export function revert(name) { const d = store.docs[name]; setText(name, d.saved, null); }

export async function save(name, force) {
  const d = store.docs[name];
  const r = await post("/api/save?name=" + encodeURIComponent(name), { text: d.text, mtime: d.mtime, force: !!force });
  d.saved = d.text; d.mtime = r.mtime; d.exists = true; d.savedData = undefined;
  emit({ type: "saved", name });
  return r;
}

export function emitLive(data) { store.live = data; emit({ type: "live" }); }
export const notify = emit;
