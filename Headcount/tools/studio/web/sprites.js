// Minecraft item sprites and player faces, drawn from the textures Studio downloads into its cache (StudioAssets.java).
// Each material is drawn once on a canvas and kept as a data URL: a flat item is its layers stacked; anything 3D (blocks,
// chests, banners, heads...) is a shape from shapes.json, quads already projected the way the inventory shows them,
// painted back to front. Without textures every sprite is a flat square in the material's colour.
import { materialLook, isObj } from "./core.js";

let INDEX = null;
let SHAPES = {};
const images = new Map();
const drawn = new Map();
const shaded = new Map();

export async function loadSprites(status) {
  INDEX = null;
  SHAPES = {};
  drawn.clear();
  shaded.clear();
  if (!status?.ready) return false;
  try {
    const [r, s] = await Promise.all([fetch("/mc/index.json"), fetch("/mc/shapes.json")]);
    if (r.ok) INDEX = await r.json();
    if (s.ok) SHAPES = await s.json();
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

/** A texture's first frame (animated textures are tall strips; entity textures keep their size), tinted when asked. */
function frame(img, tint) {
  const w = img.width, h = img.height > img.width ? img.width : img.height;
  const [c, g] = canvas(w, h);
  g.drawImage(img, 0, 0, w, h, 0, 0, w, h);
  if (tint) {
    g.globalCompositeOperation = "multiply"; g.fillStyle = tint; g.fillRect(0, 0, w, h);
    g.globalCompositeOperation = "destination-in"; g.drawImage(img, 0, 0, w, h, 0, 0, w, h);
  }
  return c;
}
function crop(img, x, y, w, h, scale = 1) {
  const f = img.width / 64 * scale;
  const [c, g] = canvas(w * f, h * f);
  g.drawImage(img, x * f, y * f, w * f, h * f, 0, 0, w * f, h * f);
  return c;
}

/** A frame tinted and darkened by Minecraft's inventory lighting, kept per texture, tint and brightness. */
function lit(key, img, tint, light) {
  const k = key + "|" + (tint || "") + "|" + light;
  if (!shaded.has(k)) {
    const c = frame(img, tint);
    if (light < 1) {
      const g = c.getContext("2d");
      g.globalCompositeOperation = "source-atop"; g.fillStyle = `rgba(0,0,0,${1 - light})`; g.fillRect(0, 0, c.width, c.height);
    }
    shaded.set(k, c);
  }
  return shaded.get(k);
}

// A shape is painted into an N x N canvas standing for the 16 x 16 inventory slot.
const N = 64;
const S = N / 16;
function paint(quads, imgs, keys, colours) {
  const [c, g] = canvas(N, N);
  for (const [v, tint, light, u0, v0, u1, v1, a, b, cc, d, e, f] of quads) {
    const img = imgs[v];
    if (!img) continue;
    const src = lit(keys[v], img, tint >= 0 ? colours?.[tint] : null, light);
    const fw = src.width, fh = src.height;
    const sx = u0 * fw, sy = v0 * fh, sw = (u1 - u0) * fw, sh = (v1 - v0) * fh;
    if (sw <= 0 || sh <= 0) continue;
    // Grow each quad by about half a pixel around its middle so neighbouring faces meet without hairline gaps.
    const ex = Math.hypot(a, b) * (u1 - u0) * S, ey = Math.hypot(cc, d) * (v1 - v0) * S;
    if (ex < 0.01 || ey < 0.01) continue;
    const kx = 1 + 0.6 / ex, ky = 1 + 0.6 / ey;
    const um = (u0 + u1) / 2, vm = (v0 + v1) / 2;
    const A = a * kx, B = b * kx, C = cc * ky, D = d * ky;
    const E = (a * um + cc * vm + e) - (A * um + C * vm), F = (b * um + d * vm + f) - (B * um + D * vm);
    g.setTransform(S * A / fw, S * B / fw, S * C / fh, S * D / fh, S * E, S * F);
    g.drawImage(src, sx, sy, sw, sh, sx, sy, sw, sh);
  }
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
  if (e.t === "model") {
    const quads = SHAPES[e.g];
    if (!quads) return null;
    const vars = Object.keys(e.x || {});
    const keys = Object.fromEntries(vars.map((v) => [v, e.x[v]]));
    const imgs = Object.fromEntries(await Promise.all(vars.map(async (v) => [v, await tex(e.x[v])])));
    if (e.o && spec && spec.owner && spec.owner !== "viewer") {
      const skin = await image("/skin/" + encodeURIComponent(spec.owner) + ".png");
      if (skin && skin.height >= 64) { imgs[e.o] = skin; keys[e.o] = "skin:" + spec.owner; }
    }
    if (!Object.values(imgs).some(Boolean)) return null;
    return paint(quads, imgs, keys, e.c).toDataURL();
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
