// Headcount Studio core: the YAML engine (reads data, edits text line by line), MiniMessage and legacy color
// rendering, the score formula evaluator, number ranges, a line diff, and small helpers. No DOM in this file.

// ============================================================================================
//  Small helpers
// ============================================================================================
export const indentOf = (s) => s.length - s.trimStart().length;
export const isBlank = (s) => s.trim() === "";
export const isComment = (s) => s.trim().startsWith("#");
export const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
export const clone = (v) => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
export const isObj = (v) => v !== null && typeof v === "object" && !Array.isArray(v);

/** "1h30m" -> milliseconds; a plain number is seconds. NaN when unreadable. */
export function parseDuration(s) {
  if (s === null || s === undefined) return NaN;
  const t = String(s).trim().toLowerCase();
  if (/^\d+(\.\d+)?$/.test(t)) return parseFloat(t) * 1000;
  const units = { ms: 1, s: 1000, m: 60000, h: 3600000, d: 86400000, w: 604800000 };
  let total = 0, any = false;
  const re = /(\d+(?:\.\d+)?)\s*(ms|s|m|h|d|w)/g;
  let m, consumed = "";
  while ((m = re.exec(t))) { total += parseFloat(m[1]) * units[m[2]]; any = true; consumed += m[0]; }
  return any && consumed.replace(/\s/g, "") === t.replace(/\s/g, "") ? total : NaN;
}
export function formatDuration(ms) {
  if (!isFinite(ms)) return "?";
  if (ms < 1000) return Math.round(ms) + "ms";
  const parts = [];
  let s = Math.round(ms / 1000);
  for (const [u, n] of [["d", 86400], ["h", 3600], ["m", 60], ["s", 1]]) {
    if (s >= n) { parts.push(Math.floor(s / n) + u); s %= n; }
  }
  return parts.slice(0, 2).join(" ") || "0s";
}

// ============================================================================================
//  YAML data reader: the subset Headcount's files use (block maps and lists, "- key: value" items, flow lists and
//  maps, quoted and plain scalars, comments). Gives plain JS values.
// ============================================================================================
export function readScalarValue(raw) {
  const v = raw.trim();
  if (v === "" || v === "~" || v === "null") return v === "" ? "" : null;
  if (v.startsWith('"') && v.endsWith('"') && v.length >= 2) {
    return v.slice(1, -1).replace(/\\(["\\nt\/])/g, (m, c) => (c === "n" ? "\n" : c === "t" ? "\t" : c));
  }
  if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) return v.slice(1, -1).replace(/''/g, "'");
  if (v.startsWith("[") || v.startsWith("{")) return parseFlow(v);
  if (/^(true|false)$/i.test(v)) return v.toLowerCase() === "true";
  if (/^[-+]?\d+$/.test(v)) return parseInt(v, 10);
  if (/^[-+]?(\d+\.\d*|\.\d+|\d+)([eE][-+]?\d+)?$/.test(v)) return parseFloat(v);
  return v;
}

/** Parses a flow collection like [a, {b: c}, "d, e"]. */
export function parseFlow(src) {
  let i = 0;
  const ws = () => { while (i < src.length && /\s/.test(src[i])) i++; };
  const value = () => {
    ws();
    if (src[i] === "[") {
      i++; const out = []; ws();
      if (src[i] === "]") { i++; return out; }
      while (i < src.length) { out.push(value()); ws(); if (src[i] === ",") { i++; continue; } if (src[i] === "]") { i++; break; } break; }
      return out;
    }
    if (src[i] === "{") {
      i++; const out = {}; ws();
      if (src[i] === "}") { i++; return out; }
      while (i < src.length) {
        ws();
        const k = scalarUntil(/[:,}]/);
        ws();
        let v = null;
        if (src[i] === ":") { i++; v = value(); }
        out[String(readScalarValue(k))] = v;
        ws();
        if (src[i] === ",") { i++; continue; }
        if (src[i] === "}") { i++; break; }
        break;
      }
      return out;
    }
    return readScalarValue(scalarUntil(/[,\]}]/));
  };
  const scalarUntil = (stop) => {
    ws();
    if (src[i] === '"' || src[i] === "'") {
      const q = src[i]; const start = i; i++;
      while (i < src.length) { if (q === '"' && src[i] === "\\") { i += 2; continue; } if (src[i] === q) { if (q === "'" && src[i + 1] === "'") { i += 2; continue; } i++; break; } i++; }
      return src.slice(start, i);
    }
    const start = i;
    while (i < src.length && !stop.test(src[i])) {
      if (src[i] === ":" && stop.source.includes(":") && !/\s|[,}]/.test(src[i + 1] || " ")) { i++; continue; }
      i++;
    }
    return src.slice(start, i).trim();
  };
  return value();
}

/** "value   # comment" -> [value, "   # comment"], respecting quotes and brackets. */
export function splitInlineComment(rest) {
  let q = null, depth = 0;
  for (let i = 0; i < rest.length; i++) {
    const c = rest[i];
    if (q) {
      if (q === '"' && c === "\\") { i++; continue; }
      if (c === q) { if (q === "'" && rest[i + 1] === "'") { i++; continue; } q = null; }
      continue;
    }
    if (c === '"' || c === "'") q = c;
    else if (c === "[" || c === "{") depth++;
    else if (c === "]" || c === "}") depth--;
    else if (c === "#" && depth <= 0 && (i === 0 || /\s/.test(rest[i - 1]))) {
      let j = i; while (j > 0 && /\s/.test(rest[j - 1])) j--;
      return [rest.slice(0, j), rest.slice(j)];
    }
  }
  return [rest.replace(/\s+$/, ""), ""];
}

const KEY_RE = /^(\s*)("(?:[^"\\]|\\.)*"|'[^']*'|[^\s#:\-"'][^:]*?|-[^\s:][^:]*?):(?:\s+(.*))?$/;
export function keyLine(s) {
  if (s.trimStart().startsWith("- ") || s.trim() === "-") return null;
  const m = KEY_RE.exec(s);
  if (!m) return null;
  return { indent: m[1].length, key: m[2], rest: m[3] || "" };
}

/** Reads the whole file into JS values. Throws with a line number on a structure it can't follow. */
export function readYaml(text) {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  let i = 0;
  const skip = () => { while (i < lines.length && (isBlank(lines[i]) || isComment(lines[i]))) i++; };
  const block = (indent) => {
    skip();
    if (i >= lines.length) return null;
    const t = lines[i];
    const ind = indentOf(t);
    if (ind < indent) return null;
    if (t.trimStart().startsWith("- ") || t.trim() === "-") return list(ind);
    return map(ind);
  };
  const map = (ind) => {
    const out = {};
    while (true) {
      skip();
      if (i >= lines.length) break;
      const t = lines[i];
      const d = indentOf(t);
      if (d < ind) break;
      if (d > ind) throw new Error(`line ${i + 1}: unexpected indentation`);
      const k = keyLine(t);
      if (!k) { if (t.trimStart().startsWith("- ")) break; throw new Error(`line ${i + 1}: expected "key: value"`); }
      const key = String(readScalarValue(k.key));
      const [v] = splitInlineComment(k.rest);
      i++;
      if (v.trim() !== "") out[key] = readScalarValue(v);
      else {
        skip();
        if (i < lines.length && (indentOf(lines[i]) > ind || (indentOf(lines[i]) === ind && lines[i].trimStart().startsWith("- ")))) {
          out[key] = block(indentOf(lines[i]));
        } else out[key] = null;
      }
    }
    return out;
  };
  const list = (ind) => {
    const out = [];
    while (true) {
      skip();
      if (i >= lines.length) break;
      const t = lines[i];
      const d = indentOf(t);
      if (d !== ind || !(t.trimStart().startsWith("- ") || t.trim() === "-")) break;
      const rest = t.trimStart().slice(2);
      const itemIndent = d + 2;
      const k = keyLine(" ".repeat(itemIndent) + rest);
      if (rest.trim() === "") { i++; out.push(block(ind + 1)); continue; }
      if (k && !/^["'\[{]/.test(rest.trim())) {
        // "- key: value" starts a map whose other keys follow at itemIndent.
        lines[i] = " ".repeat(itemIndent) + rest;  // read the first key as part of the map (local copy only)
        out.push(map(itemIndent));
        continue;
      }
      i++;
      out.push(readScalarValue(splitInlineComment(rest)[0]));
    }
    return out;
  };
  const root = block(0);
  return root === null ? {} : root;
}

// ============================================================================================
//  YAML line engine: parses lines into entries (path, kind, where they are), and edits the text by replacing only
//  the lines involved. Comments, blank lines, order and quoting stay as they were.
// ============================================================================================
function cleanComments(list) {
  const out = list.filter((c) => !/^[=\-]{6,}\s*$/.test(c.trim()));
  while (out.length && out[0].trim() === "") out.shift();
  while (out.length && out[out.length - 1].trim() === "") out.pop();
  return out.join("\n");
}

function scalarList(lines, start, end) {
  let ind = -1;
  for (let i = start; i < end; i++) {
    const s = lines[i];
    if (isBlank(s) || isComment(s)) continue;
    const t = s.trimStart();
    if (!t.startsWith("- ") && t !== "-") return false;
    if (ind < 0) ind = indentOf(s);
    if (indentOf(s) !== ind) return false;
    const item = splitInlineComment(t.slice(2))[0].trim();
    if (item.startsWith("{") || item.startsWith("[")) return false;
    if (!/^["']/.test(item) && /:\s|:$/.test(item)) return false;
  }
  return ind >= 0;
}

function scalarInfo(raw) {
  const v = raw.trim();
  const quote = v.startsWith('"') ? '"' : v.startsWith("'") ? "'" : "";
  const value = readScalarValue(v);
  const type = v.startsWith("[") ? "flowlist" : v.startsWith("{") ? "flowmap" : typeof value === "boolean" ? "bool"
    : typeof value === "number" && !quote ? "number" : "string";
  return { quote, value, type };
}

function parseMap(lines, start, end, path, parentIndent) {
  const entries = [];
  let comments = [], commentStart = -1, sawBlank = false;
  let childIndent = -1;
  let i = start;
  while (i < end) {
    const s = lines[i];
    if (isBlank(s)) { comments.push(""); sawBlank = true; commentStart = -1; i++; continue; }
    if (isComment(s)) { comments.push(s.trim().replace(/^#\s?/, "")); if (commentStart < 0) commentStart = i; i++; continue; }
    const ind = indentOf(s);
    if (childIndent < 0) childIndent = ind;
    if (ind !== childIndent) return { ok: false };
    const k = keyLine(s);
    if (!k) return { ok: false };
    let j = i + 1, last = i;
    while (j < end) {
      const t = lines[j];
      if (isBlank(t) || isComment(t)) { j++; continue; }
      if (indentOf(t) <= childIndent && !(indentOf(t) === childIndent && t.trimStart().startsWith("- "))) break;
      last = j; j++;
    }
    const blockEnd = last + 1;
    const keyName = String(readScalarValue(k.key));
    const p = path ? path + "." + keyName : keyName;
    const [valueRaw, inlineComment] = splitInlineComment(k.rest);
    const help = cleanComments(comments) + (inlineComment ? (comments.some((c) => c.trim()) ? "\n" : "") + inlineComment.trim().replace(/^#\s?/, "") : "");
    const base = { key: keyName, keyText: k.key, path: p, line: i, end: blockEnd, indent: childIndent, help, inlineComment,
      commentStart: commentStart >= 0 ? commentStart : i };
    if (valueRaw.trim() !== "") {
      if (blockEnd > i + 1) return { ok: false };
      const sc = scalarInfo(valueRaw);
      const kind = sc.type === "flowlist" ? "flowlist" : sc.type === "flowmap" ? "flowmap" : "scalar";
      entries.push({ ...base, kind, type: sc.type, quote: sc.quote, value: sc.value, raw: valueRaw.trim() });
    } else if (blockEnd === i + 1) {
      entries.push({ ...base, kind: "scalar", type: "null", quote: "", value: null, raw: "" });
    } else {
      const sub = parseMap(lines, i + 1, blockEnd, p, childIndent);
      if (sub.ok) entries.push({ ...base, kind: "map", children: sub.entries, childIndent: sub.childIndent });
      else if (scalarList(lines, i + 1, blockEnd)) {
        const items = [];
        let itemIndent = 0;
        for (let x = i + 1; x < blockEnd; x++) {
          const t = lines[x];
          if (isBlank(t) || isComment(t)) continue;
          itemIndent = indentOf(t);
          items.push(readScalarValue(splitInlineComment(t.trimStart().slice(2))[0]));
        }
        entries.push({ ...base, kind: "list", items, itemIndent });
      } else {
        const body = lines.slice(i + 1, blockEnd);
        const minInd = Math.min(...body.filter((t) => !isBlank(t)).map(indentOf));
        entries.push({ ...base, kind: "raw", rawIndent: minInd, text: body.map((t) => (isBlank(t) ? "" : t.slice(minInd))).join("\n") });
      }
    }
    comments = []; commentStart = -1; sawBlank = false;
    i = blockEnd;
  }
  return { ok: true, entries, childIndent: childIndent < 0 ? (parentIndent ?? -2) + 2 : childIndent };
}

/** Parses a file into {lines, header, entries, index}. entries is null when the layout can't be followed. */
export function parseDoc(text) {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  let h = 0; const header = [];
  while (h < lines.length && (isBlank(lines[h]) || isComment(lines[h]))) { if (isComment(lines[h])) header.push(lines[h].replace(/^#\s?/, "")); h++; }
  const res = parseMap(lines, h, lines.length, "", -2);
  const index = new Map();
  const walk = (es) => { for (const e of es || []) { index.set(e.path, e); if (e.kind === "map") walk(e.children); } };
  if (res.ok) walk(res.entries);
  return { lines, header: cleanComments(header), entries: res.ok ? res.entries : null, index, rootIndent: res.childIndent ?? 0 };
}

/** Formats one scalar for YAML, keeping the original quote style where it can. */
export function formatScalar(value, quote = "") {
  if (value === null || value === undefined) return "";
  if (typeof value === "boolean") return value ? "true" : "false";
  if (typeof value === "number" && !quote) return String(value);
  const s = String(value);
  const needsQuote = s === "" || /^[\s\-?:,\[\]{}#&*!|>'"%@`]/.test(s) || /:\s|\s#|:$/.test(s) || /\s$/.test(s)
    || /^(true|false|yes|no|on|off|null|~|[-+]?\d+(\.\d+)?)$/i.test(s) || s.includes("\n") || s.includes("<") || s.includes("%");
  if (quote === "'" && !s.includes("\n")) return "'" + s.replace(/'/g, "''") + "'";
  if (quote === '"' || needsQuote) return '"' + s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n") + '"';
  return s;
}
const flowItem = (v) => (typeof v === "string" && /^[A-Za-z0-9_.\/#:+-]+$/.test(v) && !/^(true|false|yes|no|on|off|null|[-+]?\d+(\.\d+)?)$/i.test(v)
  ? v : isObj(v) ? flowMap(v) : Array.isArray(v) ? "[" + v.map(flowItem).join(", ") + "]" : formatScalar(v));
const flowMap = (o) => "{ " + Object.entries(o).map(([k, v]) => `${flowKey(k)}: ${flowItem(v)}`).join(", ") + " }";
const flowKey = (k) => (/^[A-Za-z0-9_.\-]+$/.test(k) ? k : formatScalar(k, '"'));

/** YAML lines for a value under `key` at `indent` (the key line included). */
export function dumpEntry(key, value, indent) {
  const pad = " ".repeat(indent);
  const k = flowKey(String(key));
  if (Array.isArray(value)) {
    if (value.length === 0) return [`${pad}${k}: []`];
    const simple = value.every((v) => !isObj(v) && !Array.isArray(v));
    const flat = "[" + value.map(flowItem).join(", ") + "]";
    if (simple && flat.length + indent + k.length < 100 && !value.some((v) => typeof v === "string" && v.includes("<"))) return [`${pad}${k}: ${flat}`];
    return [`${pad}${k}:`, ...dumpList(value, indent + 2)];
  }
  if (isObj(value)) {
    const keys = Object.keys(value);
    if (keys.length === 0) return [`${pad}${k}: {}`];
    return [`${pad}${k}:`, ...keys.flatMap((kk) => dumpEntry(kk, value[kk], indent + 2))];
  }
  return [`${pad}${k}: ${formatScalar(value)}`.replace(/:\s$/, ":")];
}
export function dumpList(items, indent) {
  const pad = " ".repeat(indent);
  const out = [];
  for (const v of items) {
    if (isObj(v)) {
      const keys = Object.keys(v);
      if (keys.length === 0) { out.push(`${pad}- {}`); continue; }
      const first = dumpEntry(keys[0], v[keys[0]], indent + 2);
      first[0] = pad + "- " + first[0].slice(indent + 2);
      out.push(...first);
      for (const kk of keys.slice(1)) out.push(...dumpEntry(kk, v[kk], indent + 2));
    } else if (Array.isArray(v)) out.push(`${pad}- ${flowItem(v)}`);
    else out.push(`${pad}- ${formatScalar(v)}`);
  }
  return out;
}

/** Value of an entry as JS (for the line engine's entries). */
export function entryValue(doc, e) {
  if (e.kind === "scalar") return e.value;
  if (e.kind === "list") return e.items.slice();
  if (e.kind === "flowlist" || e.kind === "flowmap") return parseFlow(e.raw);
  const body = doc.lines.slice(e.line + 1, e.end);
  try { return readYaml(body.map((t) => (isBlank(t) ? "" : t.slice(Math.min(...body.filter((x) => !isBlank(x)).map(indentOf))))).join("\n")); } catch (err) { return undefined; }
}

const sameScalarType = (e, v) => e.kind === "scalar" && !isObj(v) && !Array.isArray(v);

/**
 * Sets the value at a dotted path and returns the new text. Existing scalars keep their line, quoting and inline
 * comment; lists of plain values keep their style; anything else is rewritten as a block. Missing parents are created.
 */
export function setValue(text, path, value) {
  const doc = parseDoc(text);
  if (!doc.entries) throw new Error("This file's layout can't be edited field by field; use the raw editor.");
  const e = doc.index.get(path);
  const lines = doc.lines.slice();
  if (e) {
    const pad = " ".repeat(e.indent);
    if (value === undefined) return removePath(text, path);
    if (sameScalarType(e, value)) {
      const quote = typeof value === "string" ? (e.quote || (e.type === "string" ? "" : "")) : "";
      let v = value;
      if (e.type === "number" && typeof value === "string" && /^[-+]?\d+(\.\d+)?$/.test(value.trim())) v = Number(value);
      lines[e.line] = pad + e.keyText + ": " + formatScalar(v, typeof v === "string" ? quote : "") + e.inlineComment;
      if (value === null) lines[e.line] = pad + e.keyText + ":" + e.inlineComment;
      return lines.join("\n");
    }
    if (Array.isArray(value) && value.every((v) => !isObj(v) && !Array.isArray(v))) {
      if (e.kind === "flowlist" || (e.kind === "scalar" && e.type === "null")) {
        lines[e.line] = pad + e.keyText + ": [" + value.map(flowItem).join(", ") + "]" + e.inlineComment;
        lines.splice(e.line + 1, e.end - e.line - 1);
        return lines.join("\n");
      }
      if (e.kind === "list") {
        const keep = [];
        for (let x = e.line + 1; x < e.end; x++) if (isComment(lines[x])) keep.push(lines[x]);
        const ip = " ".repeat(e.itemIndent);
        if (value.length === 0) { lines[e.line] = pad + e.keyText + ": []" + e.inlineComment; lines.splice(e.line + 1, e.end - e.line - 1, ...keep); }
        else { lines[e.line] = pad + e.keyText + ":" + e.inlineComment; lines.splice(e.line + 1, e.end - e.line - 1, ...keep, ...value.map((v) => ip + "- " + flowItem(v))); }
        return lines.join("\n");
      }
    }
    if (isObj(value) && e.kind === "flowmap" && Object.values(value).every((v) => !isObj(v) && !Array.isArray(v))) {
      lines[e.line] = pad + e.keyText + ": " + (Object.keys(value).length ? flowMap(value) : "{}") + e.inlineComment;
      return lines.join("\n");
    }
    // Rewrite the block under this key (the key line keeps its spelling).
    const dumped = dumpEntry(e.key, value, e.indent);
    dumped[0] = pad + e.keyText + dumped[0].slice(pad.length + flowKey(e.key).length);
    lines.splice(e.line, e.end - e.line, ...dumped);
    return lines.join("\n");
  }
  if (value === undefined) return text;
  // Missing: create it (and missing parents) at the end of its parent.
  const parts = path.split(".");
  for (let n = parts.length - 1; n >= 0; n--) {
    const parentPath = parts.slice(0, n).join(".");
    const parent = parentPath ? doc.index.get(parentPath) : null;
    if (parentPath && !parent) continue;
    let nested = value;
    for (let x = parts.length - 1; x > n; x--) nested = { [parts[x]]: nested };
    const key = parts[n];
    if (!parent) {
      const insertAt = trimEnd(lines, lines.length);
      lines.splice(insertAt, 0, ...dumpEntry(key, nested, doc.rootIndent || 0));
      return lines.join("\n");
    }
    if (parent.kind === "map") {
      const indent = parent.childIndent ?? parent.indent + 2;
      const insertAt = trimEnd(lines, parent.end);
      lines.splice(insertAt, 0, ...dumpEntry(key, nested, indent));
      return lines.join("\n");
    }
    // The parent is a scalar, flow map or something else: rewrite it as a map holding the old content plus the new key.
    const old = entryValue(doc, parent);
    const obj = isObj(old) ? old : {};
    obj[key] = nested;
    return setValue(text, parentPath, obj);
  }
  return text;
}

function trimEnd(lines, end) {
  let e = end;
  while (e > 0 && (isBlank(lines[e - 1]) || isComment(lines[e - 1]))) e--;
  // Comments directly above the next entry belong to it, but a trailing comment block right after content belongs here.
  return e;
}

/** Removes the entry at a path, with the comment block directly above it. */
export function removePath(text, path) {
  const doc = parseDoc(text);
  const e = doc.index.get(path);
  if (!e) return text;
  const lines = doc.lines.slice();
  let start = e.commentStart;
  // Keep comments that were separated by a blank line (they describe the section, not this entry).
  for (let x = e.line - 1; x >= start; x--) if (isBlank(lines[x])) { start = x + 1; break; }
  lines.splice(start, e.end - start);
  return lines.join("\n");
}

/** Renames the last key of a path. */
export function renamePath(text, path, newKey) {
  const doc = parseDoc(text);
  const e = doc.index.get(path);
  if (!e) return text;
  const lines = doc.lines.slice();
  lines[e.line] = " ".repeat(e.indent) + flowKey(newKey) + lines[e.line].slice(e.indent + e.keyText.length);
  return lines.join("\n");
}

/** Moves a map entry to sit right after (or before) a sibling. */
export function moveEntry(text, path, siblingPath, before) {
  const doc = parseDoc(text);
  const a = doc.index.get(path), b = doc.index.get(siblingPath);
  if (!a || !b || a === b) return text;
  const lines = doc.lines.slice();
  const chunk = lines.slice(a.commentStart, a.end);
  lines.splice(a.commentStart, a.end - a.commentStart);
  const doc2 = parseDoc(lines.join("\n"));
  const b2 = doc2.index.get(siblingPath);
  const at = before ? b2.commentStart : b2.end;
  const l2 = doc2.lines.slice();
  l2.splice(at, 0, ...chunk);
  return l2.join("\n");
}

/** A value at a dotted path in plain data. */
export function getIn(data, path) {
  if (!path) return data;
  let cur = data;
  for (const p of path.split(".")) { if (!isObj(cur)) return undefined; cur = cur[p]; }
  return cur;
}

// ============================================================================================
//  MiniMessage and legacy colors
// ============================================================================================
export const MC_COLORS = { black: "#000000", dark_blue: "#0000AA", dark_green: "#00AA00", dark_aqua: "#00AAAA", dark_red: "#AA0000",
  dark_purple: "#AA00AA", gold: "#FFAA00", gray: "#AAAAAA", grey: "#AAAAAA", dark_gray: "#555555", dark_grey: "#555555", blue: "#5555FF",
  green: "#55FF55", aqua: "#55FFFF", red: "#FF5555", light_purple: "#FF55FF", yellow: "#FFFF55", white: "#FFFFFF" };
const DECO = { bold: "b", b: "b", italic: "i", i: "i", em: "i", underlined: "u", u: "u", strikethrough: "s", st: "s", obfuscated: "o", obf: "o" };
const colorOf = (n) => (MC_COLORS[n] ? MC_COLORS[n] : /^#[0-9a-f]{6}$/i.test(n) ? n : null);
function hexMix(a, b, t) {
  const p = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
  const x = p(a), y = p(b);
  return "#" + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, "0")).join("");
}

/**
 * MiniMessage to HTML, the way Minecraft draws it. opts.vars fills <name> variables and %headcount_x% placeholders
 * with sample values; anything unknown shows as a label. opts.prefix is the lang prefix for <prefix>.
 */
export function renderMini(src, opts = {}) {
  const vars = opts.vars || {};
  const out = [];
  const stack = [{ color: opts.base || "#FFFFFF", deco: {}, grad: null, tag: "" }];
  const top = () => stack[stack.length - 1];
  const span = (text, st, cls) => {
    const style = [`color:${st.color}`];
    if (st.deco.b) style.push("font-weight:bold");
    if (st.deco.i) style.push("font-style:italic");
    const dec = [st.deco.u && "underline", st.deco.s && "line-through"].filter(Boolean).join(" ");
    if (dec) style.push("text-decoration:" + dec);
    return `<span${cls ? ` class="${cls}"` : ""} style="${style.join(";")}">${esc(text)}</span>`;
  };
  const gradColor = (cols, t) => {
    if (cols.length === 1) return cols[0];
    const seg = Math.min(cols.length - 2, Math.floor(t * (cols.length - 1)));
    return hexMix(cols[seg], cols[seg + 1], t * (cols.length - 1) - seg);
  };
  const text = (t) => {
    const st = top();
    for (const part of t.split(/(%[A-Za-z0-9]+_[^%\s]*%)/)) {
      if (!part) continue;
      const ph = /^%headcount_([^%]+)%$/.exec(part);
      if (ph && vars["%" + ph[1]] !== undefined) { out.push(span(String(vars["%" + ph[1]]), st, "sample")); continue; }
      if (/^%[A-Za-z0-9]+_[^%\s]*%$/.test(part)) { out.push(`<span class="ph">${esc(part)}</span>`); continue; }
      if (st.grad) {
        for (const c of [...part]) { const tt = st.grad.n <= 1 ? 0 : st.grad.i / (st.grad.n - 1); st.grad.i++; out.push(span(c, { ...st, color: gradColor(st.grad.colors, tt) })); }
      } else out.push(span(part, st));
    }
  };
  let i = 0;
  const s = String(src ?? "");
  while (i < s.length) {
    if (s[i] === "\\" && s[i + 1] === "<") { text("<"); i += 2; continue; }
    if (s[i] === "\n") { out.push("\n"); i++; continue; }
    if (s[i] === "<") {
      const close = s.indexOf(">", i);
      if (close < 0) { text(s.slice(i)); break; }
      const raw = s.slice(i + 1, close);
      const tag = raw.toLowerCase();
      i = close + 1;
      if (tag.startsWith("/")) {
        const name = tag.slice(1).split(":")[0];
        for (let k = stack.length - 1; k > 0; k--) if (stack[k].tag === name || name === "") { stack.length = k; break; }
        continue;
      }
      const name = tag.split(":")[0];
      const st = top();
      if (name.startsWith("!") && DECO[name.slice(1)]) { stack.push({ ...st, deco: { ...st.deco, [DECO[name.slice(1)]]: false }, tag: name.slice(1) }); continue; }
      if (name === "reset") { stack.length = 1; continue; }
      if (name === "newline" || name === "br") { out.push("\n"); continue; }
      if (name === "prefix" && opts.prefix !== undefined) { out.push(renderMini(opts.prefix, { ...opts, prefix: "" })); continue; }
      if (colorOf(name)) { stack.push({ ...st, deco: { ...st.deco }, grad: null, color: colorOf(name), tag: name }); continue; }
      if (["color", "c", "colour"].includes(name) && colorOf(tag.split(":")[1] || "")) { stack.push({ ...st, deco: { ...st.deco }, grad: null, color: colorOf(tag.split(":")[1]), tag: name }); continue; }
      if (DECO[name]) { stack.push({ ...st, deco: { ...st.deco, [DECO[name]]: true }, tag: name }); continue; }
      if (name === "gradient" || name === "rainbow") {
        const cols = name === "rainbow" ? ["#FF5555", "#FFAA00", "#FFFF55", "#55FF55", "#55FFFF", "#5555FF", "#FF55FF"] : tag.split(":").slice(1).map(colorOf).filter(Boolean);
        const end = s.toLowerCase().indexOf("</" + name, i);
        const n = [...s.slice(i, end < 0 ? s.length : end).replace(/<[^>]*>/g, "")].length;
        stack.push({ ...st, deco: { ...st.deco }, grad: { colors: cols.length ? cols : ["#FFFFFF"], n, i: 0 }, tag: name });
        continue;
      }
      if (["click", "hover", "insert", "font", "key", "lang", "translate", "selector", "nbt", "transition", "pride", "shadow", "sprite", "head"].includes(name)) { stack.push({ ...st, tag: name }); continue; }
      if (name === "score" && vars.score !== undefined) {
        const args = tag.split(":").slice(1);
        const sc = Number(vars.score);
        const col = colorOf(sc > 0 ? args[0] || "green" : sc < 0 ? args[1] || "red" : args[2] || "gray") || "#FFFFFF";
        out.push(span(String(sc), { ...st, color: col }, "sample"));
        continue;
      }
      if (vars[name] !== undefined) { out.push(span(String(vars[name]), st, "sample")); continue; }
      out.push(`<span class="var">‹${esc(raw)}›</span>`);
      continue;
    }
    const next = s.slice(i).search(/[<\\\n]/);
    const chunk = next < 0 ? s.slice(i) : s.slice(i, i + Math.max(1, next));
    text(chunk);
    i += chunk.length;
  }
  return out.join("");
}

const LEGACY = { 0: "#000000", 1: "#0000AA", 2: "#00AA00", 3: "#00AAAA", 4: "#AA0000", 5: "#AA00AA", 6: "#FFAA00", 7: "#AAAAAA",
  8: "#555555", 9: "#5555FF", a: "#55FF55", b: "#55FFFF", c: "#FF5555", d: "#FF55FF", e: "#FFFF55", f: "#FFFFFF" };
/** Text with § color codes (RCON replies) to HTML. */
export function legacyToHtml(s) {
  let color = "#E8E8E8", bold = false, italic = false;
  const out = [];
  const parts = String(s).split(/(§x(?:§[0-9a-f]){6}|§[0-9a-fk-or])/i);
  for (const p of parts) {
    if (!p) continue;
    if (/^§x/i.test(p)) { color = "#" + p.replace(/§x|§/gi, ""); continue; }
    if (/^§[0-9a-fk-or]$/i.test(p)) {
      const c = p[1].toLowerCase();
      if (LEGACY[c]) { color = LEGACY[c]; bold = italic = false; } else if (c === "l") bold = true; else if (c === "o") italic = true; else if (c === "r") { color = "#E8E8E8"; bold = italic = false; }
      continue;
    }
    out.push(`<span style="color:${color};${bold ? "font-weight:bold;" : ""}${italic ? "font-style:italic;" : ""}">${esc(p)}</span>`);
  }
  return out.join("");
}
export const stripMini = (s) => String(s ?? "").replace(/\\</g, "\u0000").replace(/<[^>]*>/g, "").replace(/\u0000/g, "<");

// ============================================================================================
//  Score formula evaluator (the same language as scoring.yml)
// ============================================================================================
const FUNCS = {
  min: Math.min, max: Math.max, avg: (...a) => (a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0), floor: Math.floor, ceil: Math.ceil,
  round: (x, d = 0) => { const f = Math.pow(10, d); return Math.round(x * f) / f; }, trunc: Math.trunc, abs: Math.abs, sign: Math.sign, pow: Math.pow,
  sqrt: Math.sqrt, cbrt: Math.cbrt, exp: Math.exp, log: (x, b) => (b === undefined ? Math.log10(x) : Math.log(x) / Math.log(b)), ln: Math.log,
  log10: Math.log10, log2: Math.log2, clamp: (x, lo, hi) => Math.max(lo, Math.min(hi, x)), if: (c, a, b) => (c ? a : b), sin: Math.sin, cos: Math.cos, tan: Math.tan,
};
const CONSTS = { pi: Math.PI, e: Math.E, true: 1, false: 0 };

/** Compiles a formula. Returns {ok, error, at, vars:Set, eval(vars) -> number}. */
export function compileFormula(src) {
  const toks = [];
  const re = /\s*(?:(\d+(?:\.\d+)?|\.\d+)|(%[^%\s]+%)|([A-Za-z_][A-Za-z0-9_]*)|(\|\||&&|==|!=|<=|>=|[-+*/%^()<>!?:,]))/y;
  let pos = 0;
  while (pos < src.length) {
    if (/^\s*$/.test(src.slice(pos))) break;
    re.lastIndex = pos;
    const m = re.exec(src);
    if (!m) return { ok: false, error: `can't read "${src.slice(pos).trim().slice(0, 12)}"`, at: pos, vars: new Set(), eval: () => NaN };
    toks.push({ t: m[1] ? "num" : m[2] ? "ph" : m[3] ? "id" : "op", v: m[1] || m[2] || m[3] || m[4], at: m.index });
    pos = re.lastIndex;
  }
  let i = 0;
  const vars = new Set();
  const peek = () => toks[i], next = () => toks[i++];
  const expect = (v) => { const t = next(); if (!t || t.v !== v) throw new Error(`expected "${v}"`); };
  const ternary = () => {
    const c = or();
    if (peek()?.v === "?") { next(); const a = ternary(); expect(":"); const b = ternary(); return (env) => (c(env) ? a(env) : b(env)); }
    return c;
  };
  const bin = (sub, ops) => () => {
    let l = sub();
    while (peek() && ops[peek().v]) { const op = ops[next().v]; const r = sub(); const ll = l; l = (env) => op(ll(env), r(env)); }
    return l;
  };
  const unary = () => {
    const t = peek();
    if (t?.v === "-") { next(); const u = unary(); return (env) => -u(env); }
    if (t?.v === "+") { next(); return unary(); }
    if (t?.v === "!") { next(); const u = unary(); return (env) => (u(env) ? 0 : 1); }
    return power();
  };
  const power = () => {
    const b = primary();
    if (peek()?.v === "^") { next(); const e = unary(); return (env) => Math.pow(b(env), e(env)); }
    return b;
  };
  const primary = () => {
    const t = next();
    if (!t) throw new Error("the formula ends too early");
    if (t.t === "num") { const v = parseFloat(t.v); return () => v; }
    if (t.t === "ph") { vars.add(t.v); return (env) => Number(env[t.v] ?? 0); }
    if (t.v === "(") { const e = ternary(); expect(")"); return e; }
    if (t.t === "id") {
      const name = t.v.toLowerCase();
      if (peek()?.v === "(") {
        next();
        const args = [];
        if (peek()?.v !== ")") { args.push(ternary()); while (peek()?.v === ",") { next(); args.push(ternary()); } }
        expect(")");
        const f = FUNCS[name];
        if (!f) throw new Error(`unknown function ${t.v}()`);
        return (env) => f(...args.map((a) => a(env)));
      }
      if (name in CONSTS) return () => CONSTS[name];
      vars.add(t.v);
      return (env) => (env[t.v] === undefined ? NaN : Number(env[t.v]));
    }
    throw new Error(`unexpected "${t.v}"`);
  };
  const B = (f) => (a, b) => (f(a, b) ? 1 : 0);
  const mul = bin(unary, { "*": (a, b) => a * b, "/": (a, b) => a / b, "%": (a, b) => a % b });
  const add = bin(mul, { "+": (a, b) => a + b, "-": (a, b) => a - b });
  const cmp = bin(add, { "<": B((a, b) => a < b), "<=": B((a, b) => a <= b), ">": B((a, b) => a > b), ">=": B((a, b) => a >= b) });
  const eq = bin(cmp, { "==": B((a, b) => a === b), "!=": B((a, b) => a !== b) });
  const and = bin(eq, { "&&": B((a, b) => a && b) });
  const or = bin(and, { "||": B((a, b) => a || b) });
  try {
    const f = ternary();
    if (i < toks.length) throw new Error(`unexpected "${toks[i].v}"`);
    return { ok: true, vars, eval: (env) => f(env) };
  } catch (err) {
    return { ok: false, error: err.message, at: toks[i]?.at ?? src.length, vars, eval: () => NaN };
  }
}

export function roundScore(x, mode) {
  if (!isFinite(x)) return 0;
  return mode === "round" ? Math.round(x) : mode === "ceil" ? Math.ceil(x) : Math.floor(x);
}

// ============================================================================================
//  Number ranges (outcome scores, conditions): 5, 1..3, ..-1, 10.., >=2, >2, <=3, <3, !=0, any
// ============================================================================================
export function parseRange(raw) {
  if (raw === null || raw === undefined) return { min: -Infinity, max: Infinity, test: () => true, ok: true };
  if (typeof raw === "number") return { min: raw, max: raw, test: (v) => v === raw, ok: true };
  const s = String(raw).trim().toLowerCase().replace(/ /g, "");
  const num = (x) => { if (!/^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(x)) throw new Error(x); return parseFloat(x); };
  try {
    if (s === "" || s === "any" || s === "*") return { min: -Infinity, max: Infinity, test: () => true, ok: true };
    if (s.startsWith(">=")) { const n = num(s.slice(2)); return { min: n, max: Infinity, test: (v) => v >= n, ok: true }; }
    if (s.startsWith("<=")) { const n = num(s.slice(2)); return { min: -Infinity, max: n, test: (v) => v <= n, ok: true }; }
    if (s.startsWith("!=")) { const n = num(s.slice(2)); return { min: -Infinity, max: Infinity, test: (v) => v !== n, ok: true }; }
    if (s.startsWith(">")) { const n = num(s.slice(1)); return { min: n + 1e-9, max: Infinity, test: (v) => v > n, ok: true }; }
    if (s.startsWith("<")) { const n = num(s.slice(1)); return { min: -Infinity, max: n - 1e-9, test: (v) => v < n, ok: true }; }
    const t = s.startsWith("=") ? s.slice(1) : s;
    const d = t.indexOf("..");
    if (d >= 0) {
      const a = t.slice(0, d), b = t.slice(d + 2);
      let lo = a ? num(a) : -Infinity, hi = b ? num(b) : Infinity;
      if (lo > hi) [lo, hi] = [hi, lo];
      return { min: lo, max: hi, test: (v) => v >= lo && v <= hi, ok: true };
    }
    const n = num(t);
    return { min: n, max: n, test: (v) => v === n, ok: true };
  } catch (e) {
    return { min: NaN, max: NaN, test: () => false, ok: false };
  }
}
export function describeRange(raw) {
  const r = parseRange(raw);
  if (!r.ok) return "invalid";
  const f = (x) => (Number.isInteger(x) ? String(x) : x.toFixed(1));
  if (r.min === -Infinity && r.max === Infinity) return "any score";
  if (r.min === r.max) return "exactly " + f(r.min);
  if (r.min === -Infinity) return f(Math.round(r.max)) + " or lower";
  if (r.max === Infinity) return f(Math.round(r.min)) + " or higher";
  return f(r.min) + " to " + f(r.max);
}

// ============================================================================================
//  Line diff (for Review & save)
// ============================================================================================
export function diffLines(a, b) {
  const A = a.split("\n"), B = b.split("\n");
  // Trim common start and end, then LCS on the middle.
  let s = 0; while (s < A.length && s < B.length && A[s] === B[s]) s++;
  let e = 0; while (e < A.length - s && e < B.length - s && A[A.length - 1 - e] === B[B.length - 1 - e]) e++;
  const a2 = A.slice(s, A.length - e), b2 = B.slice(s, B.length - e);
  const n = a2.length, m = b2.length;
  const L = Array.from({ length: n + 1 }, () => new Int32Array(m + 1));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) L[i][j] = a2[i] === b2[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
  const out = [];
  for (let k = 0; k < s; k++) out.push({ op: "=", a: k + 1, b: k + 1, text: A[k] });
  let i = 0, j = 0;
  while (i < n || j < m) {
    if (i < n && j < m && a2[i] === b2[j]) { out.push({ op: "=", a: s + i + 1, b: s + j + 1, text: a2[i] }); i++; j++; }
    else if (j < m && (i === n || L[i][j + 1] >= L[i + 1][j])) { out.push({ op: "+", b: s + j + 1, text: b2[j] }); j++; }
    else { out.push({ op: "-", a: s + i + 1, text: a2[i] }); i++; }
  }
  for (let k = 0; k < e; k++) out.push({ op: "=", a: A.length - e + k + 1, b: B.length - e + k + 1, text: A[A.length - e + k] });
  return out;
}
/** The diff as hunks with `context` unchanged lines around each change. */
export function diffHunks(a, b, context = 3) {
  const d = diffLines(a, b);
  const keep = new Array(d.length).fill(false);
  d.forEach((x, i) => { if (x.op !== "=") for (let k = Math.max(0, i - context); k <= Math.min(d.length - 1, i + context); k++) keep[k] = true; });
  const hunks = [];
  let cur = null;
  d.forEach((x, i) => {
    if (!keep[i]) { cur = null; return; }
    if (!cur) { cur = []; hunks.push(cur); }
    cur.push(x);
  });
  return { hunks, added: d.filter((x) => x.op === "+").length, removed: d.filter((x) => x.op === "-").length };
}

// ============================================================================================
//  Materials: a recognizable color and glyph per item, without textures
// ============================================================================================
const DYE = { white: "#E9ECEC", orange: "#F07613", magenta: "#BD44B3", light_blue: "#3AAFD9", yellow: "#F8C627", lime: "#70B919", pink: "#ED8DAC",
  gray: "#3E4447", light_gray: "#8E8E86", cyan: "#158991", purple: "#792AAC", blue: "#35399D", brown: "#724728", green: "#546D1B", red: "#A12722", black: "#141519" };
export function materialLook(mat) {
  const m = String(mat || "").toUpperCase();
  const low = m.toLowerCase();
  for (const [d, c] of Object.entries(DYE)) if (low.startsWith(d + "_")) return { color: c, glyph: low.includes("pane") ? "▦" : low.includes("dye") ? "●" : low.includes("concrete") ? "■" : "▣" };
  const rules = [
    [/DIAMOND/, "#4AEDD9", "◆"], [/EMERALD/, "#17DD62", "◆"], [/NETHERITE/, "#4D494D", "◆"], [/GOLD|SUNFLOWER/, "#FCEE4B", "◆"], [/IRON/, "#D8D8D8", "◆"],
    [/REDSTONE/, "#FF2A1B", "●"], [/LAPIS/, "#2D58C4", "●"], [/COPPER/, "#C46B4B", "◆"], [/PLAYER_HEAD|SKULL|_HEAD$/, "#C69A6C", "☻"],
    [/BARRIER/, "#E33A3A", "⨯"], [/ARROW/, "#B8B8B8", "➤"], [/CLOCK/, "#E5C76B", "◷"], [/BOOK|PAPER|MAP/, "#E8E1C8", "▤"], [/NAME_TAG/, "#D9CBA0", "✎"],
    [/CHEST|BARREL|SHULKER/, "#A87A3A", "▣"], [/ENDER/, "#2B8A75", "◉"], [/NETHER_STAR|STAR/, "#F4F4D8", "✦"], [/TOTEM/, "#E8C547", "♀"],
    [/SHIELD/, "#8D6B3E", "⛨"], [/SWORD/, "#C8C8C8", "⚔"], [/PICKAXE|AXE|SHOVEL|HOE/, "#B98A55", "⛏"], [/FEATHER/, "#F2F2F2", "❧"],
    [/SUGAR/, "#F5F5F5", "∴"], [/BEEF|PORK|CHICKEN|MUTTON|BREAD|APPLE|STEAK|COOKED/, "#B85A3A", "♨"], [/POTION|BOTTLE/, "#C55BE0", "⚗"],
    [/PUMPKIN/, "#E38A1D", "☗"], [/HELMET/, "#D4AF37", "⛑"], [/BLOCK/, "#9A9A9A", "■"], [/GLASS/, "#C7E3EA", "▦"], [/BANNER/, "#D0D0D0", "⚑"],
    [/BARS/, "#7A7A7A", "▥"], [/BELL/, "#E2B53B", "♪"], [/FIREWORK/, "#E35A5A", "✷"], [/TNT/, "#D33B2C", "✹"],
  ];
  for (const [re, c, g] of rules) if (re.test(m)) return { color: c, glyph: g };
  let h = 0; for (const ch of m) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return { color: `hsl(${h % 360} 35% 55%)`, glyph: m.slice(0, 1) || "?" };
}
export const prettyMaterial = (m) => String(m || "").toLowerCase().replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

/** Slot list from "0-8, 45" style ints/ranges (YAML gives numbers or "a-b" strings). */
export function expandSlots(spec, size) {
  if (spec === "empty" || spec === undefined || spec === null) return "empty";
  if (spec === "border" || spec === "none") return spec;
  const out = [];
  for (const x of Array.isArray(spec) ? spec : [spec]) {
    const m = /^(\d+)\s*-\s*(\d+)$/.exec(String(x));
    if (m) for (let k = +m[1]; k <= +m[2]; k++) out.push(k); else if (/^\d+$/.test(String(x))) out.push(+x);
  }
  return out.filter((k) => size === undefined || k < size);
}
export function compressSlots(list) {
  const s = [...new Set(list)].sort((a, b) => a - b);
  const out = [];
  for (let i = 0; i < s.length; i++) {
    let j = i;
    while (j + 1 < s.length && s[j + 1] === s[j] + 1) j++;
    out.push(j > i ? `${s[i]}-${s[j]}` : s[i]);
    i = j;
  }
  return out;
}
