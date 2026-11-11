"""Minecraft textures for Headcount Studio's previews.

Mojang's textures can't ship with Studio, so on first launch Studio downloads the official client jar for the
server's Minecraft version (from Mojang), keeps the item, block and a few GUI and entity textures in a local cache
folder, and deletes the jar. From the jar's item models it writes index.json, which says how to draw each material
(a flat item, a block cube, a head or a chest), and it turns the bitmap font into a TrueType font so previews use
Minecraft's own lettering. Nothing here is committed: the cache folder is git-ignored.

Standard library only.
"""
import glob
import hashlib
import io
import json
import os
import re
import shutil
import struct
import threading
import urllib.request
import zipfile
import zlib

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net"
UA = {"User-Agent": "HeadcountStudio"}

# Single files kept besides textures/item and textures/block.
EXTRA = [
    "textures/gui/container/generic_54.png",
    "textures/entity/player/wide/steve.png",
    "textures/entity/player/wide/alex.png",
    "textures/entity/chest/normal.png",
    "textures/entity/chest/trapped.png",
    "textures/entity/chest/ender.png",
    "textures/entity/skeleton/skeleton.png",
    "textures/entity/skeleton/wither_skeleton.png",
    "textures/entity/zombie/zombie.png",
    "textures/entity/creeper/creeper.png",
    "textures/entity/piglin/piglin.png",
    "textures/misc/enchanted_glint_item.png",
]
SKULLS = {"skeleton_skull": "entity/skeleton/skeleton", "wither_skeleton_skull": "entity/skeleton/wither_skeleton",
          "zombie_head": "entity/zombie/zombie", "creeper_head": "entity/creeper/creeper", "piglin_head": "entity/piglin/piglin"}
# Tints that item definitions give without a fixed colour (biome colours): the plains look.
BIOME_TINT = {"minecraft:grass": "#91bd59", "minecraft:foliage": "#77ab2f", "minecraft:dry_foliage": "#a17e46"}


def server_version(server_root):
    """The Minecraft version of the server in server_root, from Paper's jar or cache names, or None."""
    for pat, rx in ((os.path.join(server_root, "cache", "mojang_*.jar"), r"mojang_(.+)\.jar$"),
                    (os.path.join(server_root, "paper-*.jar"), r"paper-(.+?)-\d+\.jar$"),
                    (os.path.join(server_root, "versions", "*"), r"versions[\\/](.+)$")):
        for p in sorted(glob.glob(pat)):
            m = re.search(rx, p)
            if m:
                return m.group(1)
    return None


class Assets:
    """The texture cache in `folder`, and its download."""

    def __init__(self, folder, server_root):
        self.folder = folder
        self.server_root = server_root
        self.lock = threading.Lock()
        self.job = {"state": "idle", "progress": 0, "error": None}

    # ---- status
    def meta(self):
        try:
            with open(os.path.join(self.folder, "meta.json"), encoding="utf-8") as f:
                return json.load(f)
        except (OSError, ValueError):
            return None

    def status(self):
        m = self.meta()
        out = dict(self.job)
        out["wanted"] = server_version(self.server_root)
        if m:
            out.update({"ready": True, "version": m.get("version"), "items": m.get("items"), "font": m.get("font"),
                        "sounds": m.get("sounds", 0)})
        else:
            out["ready"] = False
        return out

    def path(self, rel):
        """The cached file for a URL path under /mc/, or None."""
        full = os.path.abspath(os.path.join(self.folder, rel))
        if not full.startswith(os.path.abspath(self.folder) + os.sep) or not os.path.isfile(full):
            return None
        return full

    # ---- download
    def start(self, force=False):
        with self.lock:
            if self.job["state"] == "running":
                return False
            if self.meta() and not force:
                return False
            self.job = {"state": "running", "progress": 0, "error": None, "step": "Finding the version"}
        threading.Thread(target=self._run, daemon=True).start()
        return True

    def _get(self, url, timeout=30):
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
            return r.read()

    def _run(self):
        tmp = self.folder + ".part"
        try:
            manifest = json.loads(self._get(MANIFEST))
            versions = manifest["versions"]
            wanted = server_version(self.server_root) or manifest["latest"]["release"]
            entry = next((v for v in versions if v["id"] == wanted), None)
            if entry is None:  # a version Mojang doesn't list (a fork's own name): the closest release before it
                releases = [v for v in versions if v["type"] == "release"]
                key = lambda s: [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", s)]
                older = [v for v in releases if key(v["id"]) <= key(wanted)]
                entry = (older or releases)[0]
            info = json.loads(self._get(entry["url"]))
            client = info["downloads"]["client"]
            self.job["step"] = "Downloading Minecraft " + entry["id"]
            os.makedirs(os.path.dirname(self.folder), exist_ok=True)
            shutil.rmtree(tmp, ignore_errors=True)
            os.makedirs(tmp)
            jar = os.path.join(tmp, "client.jar")
            sha = hashlib.sha1()
            with urllib.request.urlopen(urllib.request.Request(client["url"], headers=UA), timeout=60) as r, open(jar, "wb") as f:
                got = 0
                while True:
                    chunk = r.read(1 << 16)
                    if not chunk:
                        break
                    f.write(chunk)
                    sha.update(chunk)
                    got += len(chunk)
                    self.job["progress"] = round(80 * got / max(1, client["size"]))
            if sha.hexdigest() != client["sha1"]:
                raise ValueError("the download didn't match Mojang's checksum")
            self.job["step"] = "Unpacking textures"
            with zipfile.ZipFile(jar) as z:
                count = extract(z, tmp)
                self.job["progress"] = 92
                font = None
                try:
                    font = build_font(z, os.path.join(tmp, "font", "minecraft.ttf"))
                except Exception as e:  # pragma: no cover - previews fall back to the bundled font
                    print("Couldn't build the Minecraft font:", e)
            os.remove(jar)
            self.job["step"] = "Listing sounds"
            sounds = 0
            try:
                sounds = self._sounds(info, tmp)
            except Exception as e:  # pragma: no cover - sound fields fall back to plain text
                print("Couldn't list Minecraft's sounds:", e)
            with open(os.path.join(tmp, "meta.json"), "w", encoding="utf-8") as f:
                json.dump({"version": entry["id"], "items": count, "font": bool(font), "sounds": sounds}, f)
            shutil.rmtree(self.folder, ignore_errors=True)
            os.replace(tmp, self.folder)
            self.job = {"state": "done", "progress": 100, "error": None}
        except Exception as e:
            shutil.rmtree(tmp, ignore_errors=True)
            self.job = {"state": "error", "progress": 0, "error": f"{type(e).__name__}: {e}"}


    def _sounds(self, info, out):
        """Writes sounds.json: every sound event and the asset hash of its first sound file."""
        objects = json.loads(self._get(info["assetIndex"]["url"]))["objects"]
        h = objects["minecraft/sounds.json"]["hash"]
        events = json.loads(self._get(f"{RESOURCES}/{h[:2]}/{h}"))
        table = {}
        for ev, spec in events.items():
            for snd in spec.get("sounds", []):
                name = snd if isinstance(snd, str) else snd.get("name")
                if isinstance(snd, dict) and snd.get("type") == "event":
                    continue
                obj = objects.get(f"minecraft/sounds/{name}.ogg")
                if obj:
                    table[ev] = obj["hash"]
                    break
        with open(os.path.join(out, "sounds.json"), "w", encoding="utf-8") as f:
            json.dump(table, f, separators=(",", ":"))
        return len(table)

    def sound(self, event):
        """The .ogg file for a sound event (downloaded once from Mojang's asset server), or None."""
        event = event.removeprefix("minecraft:")
        if not re.match(r"^[a-z0-9_.\-/]+$", event):
            return None
        try:
            with open(os.path.join(self.folder, "sounds.json"), encoding="utf-8") as f:
                h = json.load(f).get(event)
        except (OSError, ValueError):
            return None
        if not h or not re.match(r"^[0-9a-f]{40}$", h):
            return None
        p = os.path.join(self.folder, "sound", h + ".ogg")
        if not os.path.exists(p):
            try:
                data = self._get(f"{RESOURCES}/{h[:2]}/{h}", timeout=10)
                if hashlib.sha1(data).hexdigest() != h:
                    return None
                os.makedirs(os.path.dirname(p), exist_ok=True)
                with open(p, "wb") as f:
                    f.write(data)
            except Exception:
                return None
        return p


# ---------------------------------------------------------------- extracting and indexing

def extract(z, out):
    """Copies the textures Studio uses and writes index.json. Returns how many items it can draw."""
    names = set(z.namelist())
    A = "assets/minecraft/"
    for n in names:
        if n.endswith(".png") and (n.startswith(A + "textures/item/") or n.startswith(A + "textures/block/")):
            _copy(z, n, os.path.join(out, n[len(A + "textures/"):]))
    for rel in EXTRA:
        if A + rel in names:
            _copy(z, A + rel, os.path.join(out, rel[len("textures/"):]))

    def model(ref, depth=0):
        """A model's merged textures, whether it is a 2D item, and its elements' bounds."""
        ref = ref.replace("minecraft:", "")
        if depth > 12:
            return {}, False, None, set()
        if ref in ("item/generated", "builtin/generated"):
            return {}, True, None, set()
        try:
            m = json.loads(z.read(A + "models/" + ref + ".json"))
        except KeyError:
            return {}, False, None, set()
        tex, flat, box, tinted = ({}, False, None, set())
        if "parent" in m:
            tex, flat, box, tinted = model(m["parent"], depth + 1)
            tex = dict(tex)
        tex.update(m.get("textures", {}))
        if m.get("elements"):
            lo, hi = [16, 16, 16], [0, 0, 0]
            tinted = set()
            for e in m["elements"]:
                for i in range(3):
                    lo[i] = min(lo[i], e["from"][i])
                    hi[i] = max(hi[i], e["to"][i])
                for face, spec in (e.get("faces") or {}).items():
                    if "tintindex" in spec:
                        tinted.add(face)
            box, flat = (lo, hi), False
        return tex, flat, box, tinted

    def resolve(tex, key):
        seen = 0
        v = tex.get(key)
        while isinstance(v, str) and v.startswith("#") and seen < 8:
            v = tex.get(v[1:])
            seen += 1
        return v.replace("minecraft:", "") if isinstance(v, str) else None

    def pick(node):
        """The plain model (or special renderer) an item definition shows by default."""
        if isinstance(node, dict):
            t = node.get("type", "").replace("minecraft:", "")
            if t == "model":
                return node
            if t == "special":
                return node
            for k in ("fallback", "on_false", "model"):
                if isinstance(node.get(k), dict):
                    r = pick(node[k])
                    if r:
                        return r
            for v in node.values():
                r = pick(v) if isinstance(v, (dict, list)) else None
                if r:
                    return r
        elif isinstance(node, list):
            for v in node:
                r = pick(v)
                if r:
                    return r
        return None

    def colour(t):
        if not isinstance(t, dict):
            return None
        v = t.get("value", t.get("default"))
        if isinstance(v, int):
            return "#%06x" % (v & 0xFFFFFF)
        if isinstance(v, list) and len(v) >= 3:
            return "#" + "".join("%02x" % int(max(0, min(1, x)) * 255) for x in v[:3])
        return BIOME_TINT.get(t.get("type"))

    index = {}
    for n in sorted(names):
        if not (n.startswith(A + "items/") and n.endswith(".json")):
            continue
        item = n[len(A + "items/"):-5]
        try:
            d = pick(json.loads(z.read(n)).get("model"))
        except (ValueError, KeyError):
            continue
        if not d:
            continue
        tints = [colour(t) for t in d.get("tints", [])] if isinstance(d.get("tints"), list) else []
        if d.get("type", "").endswith("special"):
            kind = (d.get("model") or {}).get("type", "").replace("minecraft:", "")
            if kind == "player_head":
                index[item] = {"t": "head", "skin": "entity/player/wide/steve"}
            elif item in SKULLS:
                index[item] = {"t": "head", "skin": SKULLS[item]}
            elif kind == "chest":
                tx = (d.get("model") or {}).get("texture", "minecraft:normal").replace("minecraft:", "")
                if item == "ender_chest":
                    tx = "ender"
                index[item] = {"t": "chest", "tex": "entity/chest/" + (tx if tx in ("normal", "trapped", "ender") else "normal")}
            continue
        tex, flat, box, tinted = model(d.get("model", ""))
        if flat:
            layers, k = [], 0
            while resolve(tex, f"layer{k}"):
                layers.append(resolve(tex, f"layer{k}"))
                k += 1
            if layers:
                e = {"t": "flat", "l": layers}
                if any(tints[:len(layers)]):
                    e["c"] = [tints[i] if i < len(tints) else None for i in range(len(layers))]
                index[item] = e
            continue
        top = next((resolve(tex, k) for k in ("top", "up", "end", "all", "texture", "side", "particle") if resolve(tex, k)), None)
        left = next((resolve(tex, k) for k in ("front", "north", "south", "side", "all", "texture", "particle") if resolve(tex, k)), None)
        right = next((resolve(tex, k) for k in ("side", "east", "west", "all", "texture", "front", "particle") if resolve(tex, k)), None)
        if top or left:
            e = {"t": "cube", "top": top or left, "left": left or top, "right": right or left or top}
            if box and (box[0] != [0, 0, 0] or box[1] != [16, 16, 16]):
                e["box"] = box
            tint = tints[0] if tints else None
            if tint and tinted:
                e["tint"] = tint
                e["tf"] = sorted(tinted)
            index[item] = e
    # Only keep entries whose textures were copied.
    have = lambda p: os.path.exists(os.path.join(out, p + ".png"))
    clean = {}
    for k, e in index.items():
        files = e.get("l") or [e.get(x) for x in ("top", "left", "right", "skin", "tex") if e.get(x)]
        if files and all(have(f) for f in files):
            clean[k] = e
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as f:
        json.dump(clean, f, separators=(",", ":"))
    return len(clean)


def _copy(z, name, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with z.open(name) as src, open(dest, "wb") as f:
        shutil.copyfileobj(src, f)


# ---------------------------------------------------------------- PNG (enough for the font atlases)

def read_png(data):
    """(width, height, rows of RGBA tuples) for 8-bit or palette PNGs."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, plte, trns = 8, b"", None, None
    w = h = depth = ctype = interlace = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
    if interlace:
        raise ValueError("interlaced PNGs aren't supported")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bits = channels * depth
    stride = (w * bits + 7) // 8
    bpp = max(1, bits // 8)
    raw = zlib.decompress(idat)
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        px = []
        if depth < 8:
            vals = []
            for byte in line:
                for k in range(8 // depth):
                    vals.append((byte >> (8 - depth * (k + 1))) & ((1 << depth) - 1))
            vals = vals[:w]
        else:
            vals = list(line)
        for x in range(w):
            if ctype == 3:
                i = vals[x]
                r, g, b = plte[i]
                a = trns[i] if trns and i < len(trns) else 255
            elif ctype == 6:
                r, g, b, a = vals[x * 4:x * 4 + 4]
            elif ctype == 2:
                r, g, b = vals[x * 3:x * 3 + 3]
                a = 255
            elif ctype == 4:
                r = g = b = vals[x * 2]
                a = vals[x * 2 + 1]
            else:
                r = g = b = vals[x] * (255 // ((1 << depth) - 1))
                a = 255
            px.append((r, g, b, a))
        rows.append(px)
    return w, h, rows


# ---------------------------------------------------------------- the bitmap font as TrueType

UNIT = 128           # font units per Minecraft pixel
EM = 8 * UNIT        # the em is one 8-pixel line


def build_font(z, dest):
    """Writes Minecraft's default bitmap font (ascii, accented, nonlatin_european) as a TrueType font."""
    A = "assets/minecraft/"
    inc = json.loads(z.read(A + "font/include/default.json"))
    glyphs = {}  # char -> (advance px, [rectangles in px: x0, y0, x1, y1 with y up from the baseline])
    for prov in inc.get("providers", []):
        if prov.get("type") != "bitmap":
            continue
        fname = prov["file"].replace("minecraft:", "")
        w, h, px = read_png(z.read(A + "textures/" + fname))
        chars = prov.get("chars", [])
        if not chars:
            continue
        cols, nrows = max(len(r) for r in chars), len(chars)
        cw, ch = w // cols, h // nrows
        height = prov.get("height", 8)
        ascent = prov.get("ascent", 7)
        scale = height / ch
        for ry, row in enumerate(chars):
            for rx, c in enumerate(row):
                if c in ("\u0000", " ") or c in glyphs:
                    continue
                cells = [[px[ry * ch + y][rx * cw + x][3] > 0 for x in range(cw)] for y in range(ch)]
                used = [x for x in range(cw) if any(cells[y][x] for y in range(ch))]
                width = (max(used) + 1) if used else 0
                rects = []
                for y in range(ch):
                    x = 0
                    while x < cw:
                        if cells[y][x]:
                            x0 = x
                            while x < cw and cells[y][x]:
                                x += 1
                            top = ascent - y * scale
                            rects.append((x0 * scale, top - scale, x * scale, top))
                        else:
                            x += 1
                # merge rectangles stacked with the same columns
                merged = []
                for r in rects:
                    for i, m in enumerate(merged):
                        if m[0] == r[0] and m[2] == r[2] and abs(m[1] - r[3]) < 1e-6:
                            merged[i] = (m[0], r[1], m[2], m[3])
                            break
                    else:
                        merged.append(r)
                glyphs[c] = (int(width * scale + 0.5) + 1, merged)
    glyphs[" "] = (4, [])
    glyphs[" "] = (4, [])
    data = _ttf(glyphs)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(data)
    return dest


def _ttf(glyphs):
    chars = sorted((c for c in glyphs if ord(c) < 0xFFFF), key=ord)
    order = [None] + chars  # glyph 0 is .notdef
    glyf, loca, hmtx = b"", [], b""
    max_pts = max_cont = 0
    xmin = ymin = 1 << 30
    xmax = ymax = -(1 << 30)
    adv_max = 0
    for c in order:
        loca.append(len(glyf))
        if c is None:
            adv, rects = 6, [(0, 0, 5, 7)]
            rects = [(0, 0, 5, 1), (0, 6, 5, 7), (0, 1, 1, 6), (4, 1, 5, 6)]
        else:
            adv, rects = glyphs[c]
        advu = int(adv * UNIT)
        adv_max = max(adv_max, advu)
        if not rects:
            hmtx += struct.pack(">Hh", advu, 0)
            continue
        pts = []
        ends = []
        for (x0, y0, x1, y1) in rects:
            a, b, cc, d = (int(round(v * UNIT)) for v in (x0, y0, x1, y1))
            pts += [(a, b), (a, d), (cc, d), (cc, b)]
            ends.append(len(pts) - 1)
        gx0 = min(p[0] for p in pts); gy0 = min(p[1] for p in pts)
        gx1 = max(p[0] for p in pts); gy1 = max(p[1] for p in pts)
        xmin, ymin, xmax, ymax = min(xmin, gx0), min(ymin, gy0), max(xmax, gx1), max(ymax, gy1)
        max_pts, max_cont = max(max_pts, len(pts)), max(max_cont, len(ends))
        g = struct.pack(">hhhhh", len(ends), gx0, gy0, gx1, gy1)
        g += b"".join(struct.pack(">H", e) for e in ends) + struct.pack(">H", 0)
        g += bytes([1]) * len(pts)
        px = py = 0
        xs = ys = b""
        for (x, y) in pts:
            xs += struct.pack(">h", x - px)
            ys += struct.pack(">h", y - py)
            px, py = x, y
        g += xs + ys
        g += b"\0" * ((4 - len(g) % 4) % 4)
        glyf += g
        hmtx += struct.pack(">Hh", advu, gx0)
    loca.append(len(glyf))
    n = len(order)
    asc, desc = 7 * UNIT, -1 * UNIT
    head = struct.pack(">IIIIHHqqhhhhHHhhh", 0x00010000, 0x00010000, 0, 0x5F0F3CF5, 0x000B, EM, 0, 0,
                       xmin, ymin, xmax, ymax, 0, 8, 2, 1, 0)
    hhea = struct.pack(">IhhhHhhhhhhhhhhhH", 0x00010000, asc, desc, UNIT, adv_max, 0, 0, xmax, 1, 0, 0, 0, 0, 0, 0, 0, n)
    maxp = struct.pack(">IHHHHHHHHHHHHHH", 0x00010000, n, max_pts, max_cont, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0)
    first, last = ord(chars[0]), ord(chars[-1])
    os2 = struct.pack(">HhHHH", 4, 6 * UNIT, 400, 5, 0)
    os2 += struct.pack(">hhhhhhhhhh", 4 * UNIT, 4 * UNIT, 0, UNIT, 4 * UNIT, 4 * UNIT, 0, 3 * UNIT, UNIT, 3 * UNIT)
    os2 += struct.pack(">h", 0) + bytes(10) + struct.pack(">IIII", 3, 0, 0, 0) + b"HCST"
    os2 += struct.pack(">HHHhhhHH", 0x40, first, last, asc, desc, UNIT, 11 * UNIT, 3 * UNIT)
    os2 += struct.pack(">II", 1, 0) + struct.pack(">hhHHH", 5 * UNIT, 7 * UNIT, 0, 32, 1)
    # cmap format 4: one segment per character
    segs = [(ord(c), ord(c), (i + 1 - ord(c)) & 0xFFFF) for i, c in enumerate(chars)] + [(0xFFFF, 0xFFFF, 1)]
    sc = len(segs)
    sr = 2 * (1 << (sc.bit_length() - 1))
    sub = struct.pack(">HHHHHHH", 4, 0, 0, sc * 2, sr, (sc.bit_length() - 1), sc * 2 - sr)
    sub += b"".join(struct.pack(">H", e) for _, e, _ in segs) + b"\0\0"
    sub += b"".join(struct.pack(">H", s) for s, _, _ in segs)
    sub += b"".join(struct.pack(">H", d) for _, _, d in segs)
    sub += b"\0\0" * sc
    sub = sub[:2] + struct.pack(">H", len(sub)) + sub[4:]
    cmap = struct.pack(">HHHHI", 0, 1, 3, 1, 12) + sub
    names = {1: "Minecraft Studio", 2: "Regular", 3: "Minecraft Studio Regular", 4: "Minecraft Studio", 6: "MinecraftStudio-Regular"}
    strs = b""
    recs = b""
    for nid, s in names.items():
        enc = s.encode("utf-16-be")
        recs += struct.pack(">HHHHHH", 3, 1, 0x409, nid, len(enc), len(strs))
        strs += enc
    name = struct.pack(">HHH", 0, len(names), 6 + 12 * len(names)) + recs + strs
    post = struct.pack(">IIhhIIIII", 0x00030000, 0, -UNIT, UNIT, 0, 0, 0, 0, 0)
    loca_t = b"".join(struct.pack(">I", o) for o in loca)
    tables = {b"OS/2": os2, b"cmap": cmap, b"glyf": glyf, b"head": head, b"hhea": hhea, b"hmtx": hmtx,
              b"loca": loca_t, b"maxp": maxp, b"name": name, b"post": post}
    tags = sorted(tables)
    nt = len(tags)
    es = nt.bit_length() - 1
    out = struct.pack(">IHHHH", 0x00010000, nt, 16 * (1 << es), es, nt * 16 - 16 * (1 << es))
    offset = 12 + 16 * nt
    body = b""
    dirs = b""
    for t in tags:
        d = tables[t]
        dirs += struct.pack(">4sIII", t, _sum(d), offset + len(body), len(d))
        body += d + b"\0" * ((4 - len(d) % 4) % 4)
    font = bytearray(out + dirs + body)
    adj = (0xB1B0AFBA - _sum(bytes(font))) & 0xFFFFFFFF
    hpos = 12 + 16 * nt + sum(len(tables[t]) + (4 - len(tables[t]) % 4) % 4 for t in tags[:tags.index(b"head")])
    font[hpos + 8:hpos + 12] = struct.pack(">I", adj)
    return bytes(font)


def _sum(data):
    data = data + b"\0" * ((4 - len(data) % 4) % 4)
    return sum(struct.unpack(">%dI" % (len(data) // 4), data)) & 0xFFFFFFFF


# ---------------------------------------------------------------- player skins

NAME = re.compile(r"^[A-Za-z0-9_]{1,16}$|^[0-9a-fA-F-]{32,36}$")


def skin(folder, who):
    """A player's skin PNG (cached for a day), downloaded from mc-heads.net, or None."""
    if not NAME.match(who or ""):
        return None
    d = os.path.join(folder, "skins")
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, who.lower().replace("-", "") + ".png")
    import time
    if os.path.exists(p) and time.time() - os.path.getmtime(p) < 86400:
        return p
    try:
        with urllib.request.urlopen(urllib.request.Request("https://mc-heads.net/skin/" + who, headers=UA), timeout=6) as r:
            data = r.read()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError("not a PNG")
        with open(p, "wb") as f:
            f.write(data)
        return p
    except Exception:
        return p if os.path.exists(p) else None
