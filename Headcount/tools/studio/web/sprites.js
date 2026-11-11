// Minecraft item sprites and player faces, drawn from the textures Studio downloads into its cache (see mcassets.py).
// Each material is drawn once on a canvas (a flat item, a block cube, a head or a chest) and kept as a data URL.
// Without textures every sprite is a flat square in the material's colour.
import { materialLook, isObj } from "./core.js";

let INDEX = null;
const images = new Map();
const drawn = new Map();

export async function loadSprites(status) {
  INDEX = null;
  drawn.clear();
  if (!status?.ready) return false;
  try {
    const r = await fetch("/mc/index.json");
    if (r.ok) INDEX = await r.json();
  } catch (e) { INDEX = null; }
  if (status.font) document.documentElement.classList.add("mcfont");
  return !!INDEX;
}
export const haveTextures = () => !!INDEX;
export const knownItems = () => (INDEX ? Object.keys(INDEX) : []);

function image(src) {
  if (!images.has(src)) images.set(src, new Promise((res) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = src; }));
  return images.get(src);
}
const tex = (p) => image("/mc/" + p + ".png");

function canvas(w, h) { const c = document.createElement("canvas"); c.width = w; c.height = h; const g = c.getContext("2d"); g.imageSmoothingEnabled = false; return [c, g]; }

/** The first square frame of a texture (animated textures are tall strips), tinted when asked. */
function frame(img, tint) {
  const s = img.width;
  const [c, g] = canvas(s, s);
  g.drawImage(img, 0, 0, s, s, 0, 0, s, s);
  if (tint) {
    g.globalCompositeOperation = "multiply"; g.fillStyle = tint; g.fillRect(0, 0, s, s);
    g.globalCompositeOperation = "destination-in"; g.drawImage(img, 0, 0, s, s, 0, 0, s, s);
  }
  return c;
}
function crop(img, x, y, w, h, scale = 1) {
  const f = img.width / 64 * scale;
  const [c, g] = canvas(w * f, h * f);
  g.drawImage(img, x * f, y * f, w * f, h * f, 0, 0, w * f, h * f);
  return c;
}
function stack(...parts) { // canvases of the same width, top to bottom
  const w = parts[0].width, h = parts.reduce((n, p) => n + p.height, 0);
  const [c, g] = canvas(w, h);
  let y = 0;
  for (const p of parts) { g.drawImage(p, 0, y); y += p.height; }
  return c;
}

// The inventory view of a block: a cube seen from above, top lit, left a little darker, right darker still.
const N = 64;
const P = (x, y) => [x * N / 16, y * N / 16];
function cube(top, left, right, { y0 = 0, y1 = 16 } = {}) {
  const [c, g] = canvas(N, N);
  const drop = (16 - y1) / 16 * 7.5, hgt = (y1 - y0) / 16;
  const T = P(8, 0.5 + drop), L = P(1.5, 4.25 + drop), R = P(14.5, 4.25 + drop), C = P(8, 8 + drop);
  const face = (img, o, a, b, shade, srcRows) => {
    if (!img) return;
    const sw = img.width, sh = img.height;
    const [sy, sH] = srcRows ? [sh * (1 - srcRows[1]), sh * (srcRows[1] - srcRows[0])] : [0, sh];
    g.save();
    g.setTransform((a[0] - o[0]) / sw, (a[1] - o[1]) / sw, (b[0] - o[0]) / sH, (b[1] - o[1]) / sH, o[0], o[1]);
    g.drawImage(img, 0, sy, sw, sH, 0, 0, sw, sH);
    if (shade) { g.globalCompositeOperation = "source-atop"; g.fillStyle = `rgba(0,0,0,${shade})`; g.fillRect(0, 0, sw, sH); }
    g.restore();
  };
  const down = (p) => [p[0], p[1] + 7.5 * hgt * N / 16];
  face(top, L, T, C, 0);
  face(left, L, C, down(L), 0.22, [y0 / 16, y1 / 16]);
  face(right, C, R, down(C), 0.42, [y0 / 16, y1 / 16]);
  return c;
}

async function render(key, spec) {
  const e = INDEX?.[key];
  if (!e) return null;
  if (e.t === "flat") {
    const layers = await Promise.all(e.l.map(tex));
    if (layers.some((x) => !x)) return null;
    const s = Math.max(...layers.map((x) => x.width));
    const [c, g] = canvas(s, s);
    layers.forEach((img, i) => g.drawImage(frame(img, e.c?.[i]), 0, 0, s, s));
    return c.toDataURL();
  }
  if (e.t === "cube") {
    const tint = (n, img) => (e.tint && img && (e.tf || []).length && !/grass_block_side/.test(n) ? frame(img, e.tint) : img && frame(img));
    const [t, l, r] = await Promise.all([tex(e.top), tex(e.left), tex(e.right)]);
    if (!t && !l) return null;
    const y0 = e.box ? e.box[0][1] : 0, y1 = e.box ? e.box[1][1] : 16;
    return cube(tint(e.top, t), tint(e.left, l), tint(e.right, r), { y0, y1 }).toDataURL();
  }
  if (e.t === "head") {
    let skin = null;
    if (spec && spec.owner && spec.owner !== "viewer") skin = await image("/skin/" + encodeURIComponent(spec.owner) + ".png");
    skin ||= await tex(e.skin);
    if (!skin) return null;
    const hat = skin.height >= 64 || e.skin.includes("player");
    const part = (x, y, hx, hy) => { const base = crop(skin, x, y, 8, 8); if (hat) base.getContext("2d").drawImage(crop(skin, hx, hy, 8, 8), 0, 0); return base; };
    return cube(part(8, 0, 40, 0), part(8, 8, 40, 8), part(0, 8, 32, 8)).toDataURL();
  }
  if (e.t === "chest") {
    const img = await tex(e.tex);
    if (!img) return null;
    const front = stack(crop(img, 42, 14, 14, 5), crop(img, 42, 33, 14, 10));
    front.getContext("2d").drawImage(crop(img, 1, 1, 2, 4), 6 * front.width / 14, 2 * front.width / 14);
    return cube(crop(img, 28, 0, 14, 14), front, stack(crop(img, 0, 14, 14, 5), crop(img, 0, 33, 14, 10))).toDataURL();
  }
  return null;
}

function keyOf(spec) {
  const mat = isObj(spec) ? spec.material || (spec.head ? "PLAYER_HEAD" : spec.custom ? "" : "STONE") : spec;
  return String(mat || "").toLowerCase().replace(/^minecraft:/, "");
}
function dataUrl(spec) {
  const key = keyOf(spec);
  const owner = isObj(spec) && spec.head && spec.head !== "viewer" ? String(spec.head) : isObj(spec) && spec.owner ? String(spec.owner) : "";
  const k = key + "|" + owner;
  if (!drawn.has(k)) drawn.set(k, render(key, { owner }).catch(() => null));
  return drawn.get(k);
}

/**
 * An item as Minecraft draws it in an inventory. spec is a material name or an item definition ({material, head,
 * glint}); size is in CSS pixels. Falls back to a flat square in the material's colour.
 */
export function sprite(spec, size = 32, cls = "") {
  const key = keyOf(spec);
  const box = document.createElement("span");
  box.className = "sprite" + (cls ? " " + cls : "") + (isObj(spec) && spec.glint ? " glint" : "");
  box.style.setProperty("--s", size + "px");
  box.title = key ? key.replace(/_/g, " ") : "custom item";
  const flat = () => { box.classList.add("flat"); box.style.setProperty("--c", materialLook(key.toUpperCase()).color); };
  if (!INDEX || !INDEX[key]) { flat(); return box; }
  dataUrl(spec).then((url) => {
    if (!url) { flat(); return; }
    const img = document.createElement("img");
    img.src = url; img.alt = ""; img.draggable = false;
    box.append(img);
    if (box.classList.contains("glint")) box.style.setProperty("--mask", `url(${url})`);
  });
  return box;
}

const faces = new Map();
/** A player's face (with the hat layer) from their skin, or Steve's when it can't be fetched. */
export function face(who, size = 20, cls = "") {
  const box = document.createElement("span");
  box.className = "face" + (cls ? " " + cls : "");
  box.style.setProperty("--s", size + "px");
  const key = String(who || "");
  if (!faces.has(key)) {
    faces.set(key, (async () => {
      const skin = (key && (await image("/skin/" + encodeURIComponent(key) + ".png"))) || (INDEX && (await tex("entity/player/wide/steve")));
      if (!skin) return null;
      const c = crop(skin, 8, 8, 8, 8);
      if (skin.height >= 64) c.getContext("2d").drawImage(crop(skin, 40, 8, 8, 8), 0, 0);
      return c.toDataURL();
    })().catch(() => null));
  }
  faces.get(key).then((url) => {
    if (!url) { box.classList.add("flat"); return; }
    const img = document.createElement("img");
    img.src = url; img.alt = ""; img.draggable = false;
    box.append(img);
  });
  return box;
}

/** Plays a Minecraft sound event (downloaded on demand). Returns false when it can't. */
export async function playSound(event, { volume = 1, pitch = 1 } = {}) {
  if (!event) return false;
  try {
    const a = new Audio("/sound/" + encodeURIComponent(String(event).replace(/^minecraft:/, "")) + ".ogg");
    a.volume = Math.max(0, Math.min(1, +volume || 1)) * 0.6;
    a.preservesPitch = false;
    a.playbackRate = Math.max(0.5, Math.min(2, +pitch || 1));
    await a.play();
    return true;
  } catch (e) { return false; }
}

let soundList = null;
export async function soundKeys() {
  if (soundList) return soundList;
  try { const r = await fetch("/mc/sounds.json"); soundList = r.ok ? Object.keys(await r.json()).sort() : []; } catch (e) { soundList = []; }
  return soundList;
}
