#!/usr/bin/env python3
"""Headcount Studio: a local web app to set up, run and learn Headcount.

    python3 server.py [--dir <a server's plugins/Headcount>] [--port 8765] [--no-browser] [--exit-when-idle]

Listens on 127.0.0.1 only. The page (web/) edits the config files line by line (comments and layout stay), reads the
server's database read-only for the live pages, searches the audit log, and runs commands over RCON when the server
has it on. Every save is checked as YAML first (PyYAML) and the old file is kept in <dir>/backups/studio/.
Minecraft's textures and font for the previews are downloaded from Mojang on first launch into cache/ (see
mcassets.py); without them the previews fall back to flat colours. Standard library only, plus PyYAML for checking
saves.
"""
import argparse
import datetime
import glob
import gzip
import io
import json
import os
import re
import shutil
import socket
import sqlite3
import struct
import sys
import threading
import time
import webbrowser
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

import mcassets

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.join(HERE, "web")
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))           # the Headcount/ folder
RESOURCES = os.path.join(PROJECT, "src", "main", "resources")        # the bundled defaults
DOCS = os.path.join(PROJECT, "docs")
CACHE = os.path.join(HERE, "cache")                                  # downloaded textures and skins (git-ignored)
DEFAULT_DIR = os.path.abspath(os.path.join(PROJECT, "..", "TestServer", "plugins", "Headcount"))
FILES = ["config.yml", "integrations.yml", "statuses.yml", "trade.yml", "scoring.yml", "outcomes.yml",
         "shop.yml", "titles.yml", "actions.yml", "lang/en_US.yml"]
TYPES = {".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8",
         ".json": "application/json; charset=utf-8", ".svg": "image/svg+xml", ".png": "image/png",
         ".ttf": "font/ttf", ".woff2": "font/woff2", ".txt": "text/plain; charset=utf-8"}

try:
    import yaml  # PyYAML: checks a file before it is saved
except ImportError:  # pragma: no cover
    yaml = None

LAST_PING = [time.time()]
APP_ID = "headcount-studio"


def code_stamp():
    """Changes whenever Studio's own files change, so a launch can tell an instance running older code."""
    newest = max(os.path.getmtime(os.path.abspath(__file__)), os.path.getmtime(os.path.join(HERE, "mcassets.py")))
    for p in glob.glob(os.path.join(WEB, "**", "*"), recursive=True):
        newest = max(newest, os.path.getmtime(p))
    return str(int(newest))


CODE_STAMP = code_stamp()
PORT = [8765]


def check_yaml(text):
    if yaml is None:
        return None
    try:
        yaml.safe_load(text)
        return None
    except yaml.YAMLError as e:
        mark = getattr(e, "problem_mark", None)
        where = f" (line {mark.line + 1}, column {mark.column + 1})" if mark else ""
        return f"{getattr(e, 'problem', None) or e}{where}"


# ---------------------------------------------------------------- the server folder

class Server:
    def __init__(self, data_dir):
        self.data_dir = os.path.abspath(data_dir)
        self.root = os.path.abspath(os.path.join(self.data_dir, "..", ".."))   # the Minecraft server folder

    def props(self):
        out = {}
        p = os.path.join(self.root, "server.properties")
        if os.path.exists(p):
            with open(p, encoding="utf-8", errors="replace") as f:
                for line in f:
                    if "=" in line and not line.lstrip().startswith("#"):
                        k, v = line.rstrip("\n").split("=", 1)
                        out[k.strip()] = v.strip()
        return out

    def db_path(self):
        name = "headcount.db"
        try:
            with open(os.path.join(self.data_dir, "config.yml"), encoding="utf-8") as f:
                m = re.search(r"^storage:\s*\n(?:\s+#.*\n)*\s+file:\s*(\S+)", f.read(), re.M)
                if m:
                    name = m.group(1).strip("\"'")
        except OSError:
            pass
        return os.path.join(self.data_dir, name)

    def db(self):
        path = self.db_path()
        if not os.path.exists(path):
            raise FileNotFoundError("No Headcount database at " + path)
        con = sqlite3.connect(f"file:{path}?mode=ro", uri=True, timeout=3)
        con.row_factory = sqlite3.Row
        return con

    def plugins(self):
        return sorted(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(self.root, "plugins", "*.jar")))

    def rcon_conf(self):
        p = self.props()
        if p.get("enable-rcon") != "true" or not p.get("rcon.password"):
            return None
        return (p.get("server-ip") or "127.0.0.1", int(p.get("rcon.port") or 25575), p["rcon.password"])


# ---------------------------------------------------------------- RCON (Minecraft's Source RCON protocol)

def rcon(conf, command, timeout=5.0):
    host, port, password = conf

    def send(sock, rid, kind, body):
        data = body.encode("utf-8") + b"\x00\x00"
        sock.sendall(struct.pack("<iii", len(data) + 8, rid, kind) + data)

    def recv(sock):
        head = b""
        while len(head) < 4:
            chunk = sock.recv(4 - len(head))
            if not chunk:
                raise ConnectionError("RCON closed the connection")
            head += chunk
        (length,) = struct.unpack("<i", head)
        body = b""
        while len(body) < length:
            chunk = sock.recv(length - len(body))
            if not chunk:
                raise ConnectionError("RCON closed the connection")
            body += chunk
        rid, kind = struct.unpack("<ii", body[:8])
        return rid, kind, body[8:-2].decode("utf-8", errors="replace")

    with socket.create_connection((host, port), timeout=timeout) as s:
        send(s, 1, 3, password)
        rid, _, _ = recv(s)
        if rid == -1:
            raise PermissionError("RCON refused the password in server.properties")
        send(s, 2, 2, command)
        out = []
        rid, _, body = recv(s)
        out.append(body)
        # Long replies come in several packets; read whatever else arrives promptly.
        s.settimeout(0.25)
        try:
            while True:
                _, _, body = recv(s)
                out.append(body)
        except (socket.timeout, OSError, ConnectionError):
            pass
        return "".join(out)


# ---------------------------------------------------------------- audit log

AUDIT_LINE = re.compile(r"^(\S+)\s+([A-Z_]+)\s*(.*)$")


def parse_audit(line):
    m = AUDIT_LINE.match(line.strip())
    if not m:
        return None
    fields = {}
    for part in m.group(3).split(" "):
        if "=" in part:
            k, v = part.split("=", 1)
            fields[k] = v
    return {"ts": m.group(1), "type": m.group(2), "fields": fields}


def audit_files(folder):
    files = glob.glob(os.path.join(folder, "audit-*"))
    return sorted(files, key=lambda f: os.path.getmtime(f), reverse=True)


def read_lines(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "HeadcountStudio/1.1"
    srv: Server = None
    assets: mcassets.Assets = None

    def log_message(self, fmt, *args):
        pass

    def _send(self, code, body, ctype="application/json; charset=utf-8", extra=None):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def _json(self, code, obj):
        self._send(code, json.dumps(obj, default=str))

    def _q(self, name, default=""):
        return parse_qs(urlparse(self.path).query).get(name, [default])[0]

    def _body(self):
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def _file_param(self):
        name = self._q("name")
        if name not in FILES:
            self._json(400, {"error": f"Unknown file {name!r}."})
            return None
        return name

    def _trusted(self, post):
        """Only Studio's own page may talk to it: the Host must be this address (no DNS rebinding), a cross-site
        Origin is refused, and writes need the X-Studio header, which another site can't send without a preflight
        that Studio never answers."""
        hosts = {f"127.0.0.1:{PORT[0]}", f"localhost:{PORT[0]}"}
        origin = self.headers.get("Origin")
        ok = self.headers.get("Host", "") in hosts and (origin is None or origin.removeprefix("http://") in hosts)
        if ok and post:
            ok = self.headers.get("X-Studio") == "1"
        if not ok:
            self._json(403, {"error": "Requests to Headcount Studio must come from its own page."})
        return ok

    def do_GET(self):
        path = urlparse(self.path).path
        if not self._trusted(False):
            return
        try:
            if path.startswith("/mc/"):
                return self.texture(path[4:])
            if path.startswith("/sound/"):
                return self.sound(path[7:])
            if path.startswith("/skin/"):
                return self.skin(path[6:])
            if not path.startswith("/api/"):
                return self.static(path)
            route = {
                "/api/ping": self.ping, "/api/all": self.all, "/api/catalog": self.catalog, "/api/doc": self.doc,
                "/api/backups": self.backups, "/api/backup": self.backup, "/api/export": self.export,
                "/api/live/summary": self.live_summary, "/api/live/players": self.live_players, "/api/live/player": self.live_player,
                "/api/live/history": self.live_history, "/api/live/cycle": self.live_cycle, "/api/live/effects": self.live_effects,
                "/api/audit": self.audit, "/api/audit/head": self.audit_head, "/api/textures": self.textures,
            }.get(path)
            if route is None:
                return self._json(404, {"error": "not found"})
            route()
        except FileNotFoundError as e:
            self._json(404, {"error": str(e)})
        except sqlite3.Error as e:
            self._json(503, {"error": "Database: " + str(e)})
        except Exception as e:  # pragma: no cover - shown to the page
            self._json(500, {"error": f"{type(e).__name__}: {e}"})

    def do_POST(self):
        path = urlparse(self.path).path
        if not self._trusted(True):
            return
        try:
            route = {"/api/save": self.save, "/api/parse": self.parse, "/api/rcon": self.rcon, "/api/import": self.import_zip,
                     "/api/quit": self.quit, "/api/textures/download": self.textures_download}.get(path)
            if route is None:
                return self._json(404, {"error": "not found"})
            route()
        except Exception as e:  # pragma: no cover
            self._json(500, {"error": f"{type(e).__name__}: {e}"})

    # ---- static and files
    def static(self, path):
        name = "index.html" if path in ("/", "") else path.lstrip("/")
        full = os.path.abspath(os.path.join(WEB, name))
        ext = os.path.splitext(full)[1]
        if not full.startswith(WEB + os.sep) or ext not in TYPES or not os.path.isfile(full):
            return self._json(404, {"error": "not found"})
        with open(full, "rb") as f:
            self._send(200, f.read(), TYPES[ext])

    # ---- Minecraft textures (cache/, downloaded from Mojang) and player skins
    def texture(self, rel):
        full = self.assets.path(unquote(rel))
        ext = os.path.splitext(full or "")[1]
        if not full or ext not in TYPES:
            return self._json(404, {"error": "not found"})
        with open(full, "rb") as f:
            self._send(200, f.read(), TYPES[ext], {"Cache-Control": "max-age=86400"})

    def skin(self, rel):
        who = unquote(rel).removesuffix(".png")
        p = mcassets.skin(CACHE, who)
        if not p:
            return self._json(404, {"error": "no skin"})
        with open(p, "rb") as f:
            self._send(200, f.read(), "image/png", {"Cache-Control": "max-age=3600"})

    def sound(self, rel):
        p = self.assets.sound(unquote(rel).removesuffix(".ogg"))
        if not p:
            return self._json(404, {"error": "no such sound"})
        with open(p, "rb") as f:
            self._send(200, f.read(), "audio/ogg", {"Cache-Control": "max-age=86400"})

    def textures(self):
        self._json(200, self.assets.status())

    def textures_download(self):
        body = json.loads(self._body().decode("utf-8") or "{}")
        started = self.assets.start(force=bool(body.get("force")))
        self._json(200, dict(self.assets.status(), started=started))

    def ping(self):
        LAST_PING[0] = time.time()
        self._json(200, {"ok": True, "app": APP_ID, "code": CODE_STAMP, "dir": self.srv.data_dir})

    def all(self):
        out = []
        for n in FILES:
            cur, dflt = os.path.join(self.srv.data_dir, n), os.path.join(RESOURCES, n)
            src = cur if os.path.exists(cur) else dflt
            item = {"name": n, "exists": os.path.exists(cur)}
            if os.path.exists(src):
                with open(src, encoding="utf-8") as f:
                    item["text"] = f.read()
                item["mtime"] = os.path.getmtime(cur) if os.path.exists(cur) else None
            if os.path.exists(dflt):
                with open(dflt, encoding="utf-8") as f:
                    item["defaultText"] = f.read()
            out.append(item)
        self._json(200, {"dir": self.srv.data_dir, "files": out, "yamlCheck": yaml is not None,
                         "live": os.path.exists(self.srv.db_path()), "rcon": self.srv.rcon_conf() is not None,
                         "plugins": self.srv.plugins(), "textures": self.assets.status()})

    def catalog(self):
        p = os.path.join(HERE, "catalog.json")
        if not os.path.exists(p):
            return self._json(404, {"error": "catalog.json is missing. Run build-catalog.py to make it."})
        with open(p, "rb") as f:
            self._send(200, f.read())

    def doc(self):
        name = os.path.basename(self._q("name"))
        if name == "README":
            p = os.path.join(PROJECT, "README.md")
        else:
            p = os.path.join(DOCS, name)
        if not name.endswith(".md") and name != "README" or not os.path.isfile(p):
            return self._json(404, {"error": "no doc " + name})
        with open(p, encoding="utf-8") as f:
            self._json(200, {"name": name, "text": f.read()})

    def save(self):
        name = self._file_param()
        if name is None:
            return
        body = json.loads(self._body().decode("utf-8"))
        text = body.get("text", "")
        problem = check_yaml(text)
        if problem:
            return self._json(422, {"error": f"Not saved, because {name} wouldn't be valid YAML: {problem}"})
        target = os.path.join(self.srv.data_dir, name)
        if os.path.exists(target) and body.get("mtime") and abs(os.path.getmtime(target) - body["mtime"]) > 0.001 and not body.get("force"):
            return self._json(409, {"error": f"{name} changed on disk after Studio loaded it."})
        os.makedirs(os.path.dirname(target), exist_ok=True)
        backup = None
        if os.path.exists(target):
            stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
            bdir = os.path.join(self.srv.data_dir, "backups", "studio")
            os.makedirs(bdir, exist_ok=True)
            backup = os.path.join(bdir, f"{name.replace('/', '_')}.{stamp}")
            shutil.copy2(target, backup)
        tmp = target + ".tmp"
        with open(tmp, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        os.replace(tmp, target)
        self._json(200, {"saved": name, "mtime": os.path.getmtime(target),
                         "backup": os.path.relpath(backup, self.srv.data_dir) if backup else None})

    def parse(self):
        texts = json.loads(self._body().decode("utf-8")).get("texts", {})
        out = {}
        for n, text in texts.items():
            if yaml is None:
                out[n] = {"error": "PyYAML isn't installed."}
                continue
            try:
                out[n] = {"data": json.loads(json.dumps(yaml.safe_load(text), default=str))}
            except yaml.YAMLError:
                out[n] = {"error": check_yaml(text)}
        self._json(200, out)

    # ---- backups, import, export
    def _which_file(self, rel):
        base = os.path.basename(rel)
        for n in FILES:
            flat = n.replace("/", "_")
            if base == os.path.basename(n) or base.startswith(flat + ".") or base == flat:
                return n
        return None

    def backups(self):
        root = os.path.join(self.srv.data_dir, "backups")
        out = []
        for p in glob.glob(os.path.join(root, "**", "*"), recursive=True):
            if os.path.isfile(p) and not p.endswith((".db", ".db-wal", ".db-shm", ".gz", ".log")):
                rel = os.path.relpath(p, self.srv.data_dir)
                out.append({"path": rel, "file": self._which_file(rel), "mtime": os.path.getmtime(p), "size": os.path.getsize(p)})
        out.sort(key=lambda b: b["mtime"], reverse=True)
        self._json(200, out[:400])

    def backup(self):
        rel = self._q("path")
        full = os.path.abspath(os.path.join(self.srv.data_dir, rel))
        root = os.path.abspath(os.path.join(self.srv.data_dir, "backups"))
        if not full.startswith(root + os.sep) or not os.path.isfile(full):
            return self._json(404, {"error": "No such backup."})
        with open(full, encoding="utf-8", errors="replace") as f:
            self._json(200, {"path": rel, "text": f.read()})

    def export(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
            for n in FILES:
                p = os.path.join(self.srv.data_dir, n)
                if os.path.exists(p):
                    z.write(p, n)
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M")
        self._send(200, buf.getvalue(), "application/zip", {"Content-Disposition": f'attachment; filename="headcount-config-{stamp}.zip"'})

    def import_zip(self):
        data = self._body()
        out = {}
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                for info in z.infolist():
                    name = info.filename.replace("\\", "/")
                    for n in FILES:
                        if name == n or name.endswith("/" + n):
                            out[n] = z.read(info).decode("utf-8", errors="replace")
        except zipfile.BadZipFile:
            return self._json(400, {"error": "That isn't a .zip file."})
        self._json(200, {"files": out})

    # ---- live data (read-only)
    def _cycle(self, con):
        cols = [r[1] for r in con.execute("PRAGMA table_info(cycles)")]
        tok = ", token" if "token" in cols else ""
        r = con.execute(f"SELECT id, start_ms, end_ms, length_days, vault_size, starting_heads{tok} FROM cycles WHERE state='active' ORDER BY id DESC LIMIT 1").fetchone()
        return dict(r) if r else None

    def _players(self, con, cycle_id):
        rows = [dict(r) for r in con.execute("SELECT * FROM players")]
        counts = {r["uuid"]: r for r in con.execute(
            "SELECT uuid, COUNT(DISTINCT head_owner) AS u, COUNT(*) AS t, SUM(CASE WHEN head_owner=uuid THEN 1 ELSE 0 END) AS o FROM vault_items WHERE cycle=? GROUP BY uuid", (cycle_id or -1,))}
        stats = {r["uuid"]: dict(r) for r in con.execute("SELECT * FROM cycle_players WHERE cycle=?", (cycle_id or -1,))}
        out = []
        for p in rows:
            c, s = counts.get(p["uuid"]), stats.get(p["uuid"], {})
            out.append({"uuid": p["uuid"], "name": p["name"], "points": p["points"], "points_next": p["points_next"], "streak": p["streak"],
                        "last_score": p["last_score"], "last_join": p["last_join"], "last_quit": p["last_quit"], "online": (p["last_join"] or 0) > (p["last_quit"] or 0),
                        "vault_size": p["vault_size"], "score_bonus": p["score_bonus"], "bedrock": bool(p["bedrock"]), "equipped_title": p["equipped_title"],
                        "unique": c["u"] if c else 0, "total": c["t"] if c else 0, "own": c["o"] if c else 0,
                        "active": bool(s.get("active")), "playtime_ms": s.get("playtime_ms", 0), "kills": s.get("kills", 0), "deaths": s.get("deaths", 0), "trades": s.get("trades", 0)})
        return out

    def live_summary(self):
        with self.srv.db() as con:
            c = self._cycle(con)
            players = self._players(con, c["id"] if c else None)
            warnings = [r[0] for r in con.execute("SELECT warning FROM cycle_warnings WHERE cycle=?", (c["id"],))] if c else []
            effects = con.execute("SELECT COUNT(*) FROM active_actions").fetchone()[0]
        recent = []
        for f in audit_files(os.path.join(self.srv.data_dir, "audit"))[:2]:
            for line in reversed(read_lines(f)):
                a = parse_audit(line)
                if a:
                    recent.append(a)
                if len(recent) >= 15:
                    break
            if len(recent) >= 15:
                break
        top = sorted([p for p in players if p["unique"]], key=lambda p: -p["unique"])[:10]
        self._json(200, {"cycle": c, "playerCount": len(players), "activeCount": sum(1 for p in players if p["active"]),
                         "online": [{"uuid": p["uuid"], "name": p["name"]} for p in players if p["online"]],
                         "top": [{"uuid": p["uuid"], "name": p["name"], "unique": p["unique"], "total": p["total"]} for p in top],
                         "warnings": warnings, "effects": effects, "recent": recent})

    def live_players(self):
        with self.srv.db() as con:
            c = self._cycle(con)
            self._json(200, self._players(con, c["id"] if c else None))

    def live_player(self):
        uuid = self._q("uuid")
        with self.srv.db() as con:
            p = con.execute("SELECT * FROM players WHERE uuid=?", (uuid,)).fetchone()
            if not p:
                return self._json(404, {"error": "No such player."})
            c = self._cycle(con)
            cid = c["id"] if c else -1
            vault = [dict(r) for r in con.execute("SELECT slot, head_id, head_owner, head_owner_name, source, created_ms FROM vault_items WHERE uuid=? ORDER BY slot", (uuid,))]
            stats = con.execute("SELECT * FROM cycle_players WHERE cycle=? AND uuid=?", (cid, uuid)).fetchone()
            results = [dict(r) for r in con.execute("SELECT * FROM results WHERE uuid=? ORDER BY cycle DESC", (uuid,))]
            titles = [r[0] for r in con.execute("SELECT title FROM titles WHERE uuid=?", (uuid,))]
            flags = [r[0] for r in con.execute("SELECT flag FROM flags WHERE uuid=?", (uuid,))]
            effects = [dict(r) for r in con.execute("SELECT id, type, source, state, duration_kind, ends_ms, remaining_ms, cycles_left FROM active_actions WHERE uuid=?", (uuid,))]
            purchases = [dict(r) for r in con.execute("SELECT entry, tier, price, ms, cycle FROM purchases WHERE uuid=? ORDER BY ms DESC", (uuid,))]
        pd = {k: p[k] for k in p.keys() if k not in ("texture_value", "texture_signature")}
        self._json(200, {"player": pd, "vault": vault, "stats": dict(stats) if stats else None, "results": results, "titles": titles, "flags": flags,
                         "effects": effects, "purchases": purchases})

    def live_history(self):
        with self.srv.db() as con:
            cycles = [dict(r) for r in con.execute("SELECT id, start_ms, end_ms, ended_ms, state FROM cycles ORDER BY id DESC")]
            for c in cycles:
                rs = [dict(r) for r in con.execute("SELECT name, active, score, unique_heads FROM results WHERE cycle=?", (c["id"],))]
                act = [r for r in rs if r["active"] and r["score"] is not None]
                c["active"] = len(act)
                c["avg"] = round(sum(r["score"] for r in act) / len(act), 2) if act else None
                c["rewarded"] = sum(1 for r in act if r["score"] > 0)
                c["punished"] = sum(1 for r in act if r["score"] < 0)
                best = sorted(act, key=lambda r: -r["score"])[:3]
                c["top"] = ", ".join(f"{r['name']} ({r['score']})" for r in best)
        self._json(200, cycles)

    def live_cycle(self):
        with self.srv.db() as con:
            rows = [dict(r) for r in con.execute("SELECT * FROM results WHERE cycle=? ORDER BY rank IS NULL, rank, score DESC", (int(self._q("id", "0")),))]
        self._json(200, rows)

    def live_effects(self):
        with self.srv.db() as con:
            rows = [dict(r) for r in con.execute(
                "SELECT a.id, a.uuid, COALESCE(p.name, a.uuid) AS name, a.type, a.source, a.state, a.duration_kind, a.ends_ms, a.remaining_ms, a.cycles_left "
                "FROM active_actions a LEFT JOIN players p ON p.uuid = a.uuid ORDER BY a.ends_ms")]
        self._json(200, rows)

    # ---- audit search
    def _uuid_of(self, name):
        try:
            with self.srv.db() as con:
                r = con.execute("SELECT uuid FROM players WHERE name_lower=?", (name.lower(),)).fetchone()
                return r[0] if r else None
        except (FileNotFoundError, sqlite3.Error):
            return None

    def audit(self):
        q = self._q("q").strip()
        typ = self._q("type").strip().upper()
        page = max(1, int(self._q("page", "1") or 1))
        size = 40
        terms = []
        for t in q.split():
            u = self._uuid_of(t) if "-" not in t else None
            terms.append((t.lower(), u.lower() if u else None))
        want = page * size + 1
        found = []
        for f in audit_files(os.path.join(self.srv.data_dir, "audit")):
            for line in reversed(read_lines(f)):
                low = line.lower()
                if typ and not re.match(r"^\S+\s+" + re.escape(typ) + r"(\s|$)", line):
                    continue
                if all(t in low or (u and u in low) for t, u in terms):
                    a = parse_audit(line)
                    if a:
                        found.append(a)
                if len(found) >= want:
                    break
            if len(found) >= want:
                break
        start = (page - 1) * size
        self._json(200, {"lines": found[start:start + size], "more": len(found) > start + size})

    def audit_head(self):
        hid = self._q("id").strip().lower()
        if not re.match(r"^[0-9a-f-]{8,}$", hid):
            return self._json(400, {"error": "That isn't a head ID."})
        found = []
        for f in reversed(audit_files(os.path.join(self.srv.data_dir, "audit"))):
            for line in read_lines(f):
                if hid in line.lower():
                    a = parse_audit(line)
                    if a:
                        found.append(a)
        found.sort(key=lambda a: a["ts"])
        self._json(200, {"lines": found})

    # ---- RCON
    def rcon(self):
        conf = self.srv.rcon_conf()
        if not conf:
            return self._json(400, {"error": "RCON is off. Set enable-rcon and rcon.password in server.properties."})
        cmd = json.loads(self._body().decode("utf-8")).get("command", "").strip().lstrip("/")
        if not cmd:
            return self._json(400, {"error": "No command given."})
        try:
            self._json(200, {"reply": rcon(conf, cmd)})
        except (ConnectionRefusedError, socket.timeout, OSError) as e:
            self._json(503, {"error": f"Couldn't reach RCON on port {conf[1]}. Check that the server is running and was restarted after RCON was turned on. ({e})"})
        except PermissionError as e:
            self._json(403, {"error": str(e)})

    def quit(self):
        self._json(200, {"ok": True})
        threading.Thread(target=lambda: (time.sleep(0.3), os._exit(0)), daemon=True).start()


def peek(port):
    """What answers /api/ping on this port, or None."""
    import urllib.request
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{port}/api/ping", headers={"Host": f"127.0.0.1:{port}"})
        with urllib.request.urlopen(req, timeout=2) as r:
            j = json.loads(r.read().decode("utf-8"))
        return j if j.get("app") == APP_ID else None
    except Exception:
        return None


def stop_other(port):
    """Ask the Studio on this port to quit, and wait until the port is free."""
    import urllib.request
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{port}/api/quit", data=b"{}", method="POST",
                                     headers={"Content-Type": "application/json", "X-Studio": "1"})
        urllib.request.urlopen(req, timeout=2).read()
    except Exception:
        return False
    for _ in range(30):
        time.sleep(0.1)
        with socket.socket() as s:
            if s.connect_ex(("127.0.0.1", port)) != 0:
                return True
    return False


def main():
    ap = argparse.ArgumentParser(description="Headcount Studio (local web app)")
    ap.add_argument("--dir", default=DEFAULT_DIR, help="the server's plugins/Headcount folder (default: the repository's test server)")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--no-browser", action="store_true", help="don't open a browser tab")
    ap.add_argument("--offline", action="store_true", help="don't download Minecraft's textures on first launch")
    ap.add_argument("--exit-when-idle", action="store_true", help="stop when no Studio page has been open for 2 minutes (the launchers use this)")
    args = ap.parse_args()
    data_dir = os.path.abspath(args.dir)
    if not os.path.isdir(data_dir):
        os.makedirs(data_dir, exist_ok=True)  # a server that hasn't started yet: edit the defaults
    Handler.srv = Server(data_dir)
    Handler.assets = mcassets.Assets(os.path.join(CACHE, "minecraft"), Handler.srv.root)
    httpd, url = None, None
    for port in range(args.port, args.port + 10):
        url = f"http://127.0.0.1:{port}/"
        try:
            httpd = ThreadingHTTPServer(("127.0.0.1", port), Handler)
            PORT[0] = port
            break
        except OSError:
            pass
        # Something has the port. Studio on the same folder with the same code: just open it. Studio running older
        # code: ask it to quit and take its place. Anything else (or an instance that doesn't answer): next port.
        other = peek(port)
        if other and other.get("dir") == data_dir and other.get("code") == CODE_STAMP:
            print(f"Headcount Studio is already running at {url}")
            if not args.no_browser:
                webbrowser.open(url)
            return
        if other and other.get("dir") == data_dir and stop_other(port):
            try:
                httpd = ThreadingHTTPServer(("127.0.0.1", port), Handler)
                PORT[0] = port
                print("Replaced a Headcount Studio that was running older code.")
                break
            except OSError:
                pass
    if httpd is None:
        print(f"Ports {args.port}-{args.port + 9} are all in use. Close something, or use --port.")
        sys.exit(1)
    print(f"Headcount Studio: {url}")
    print(f"Server folder: {data_dir}")
    if yaml is None:
        print("PyYAML isn't installed, so saves aren't checked as YAML first. Install it with pip install pyyaml.")
    if not args.offline and Handler.assets.start():
        print("Downloading Minecraft's textures for the previews (once) into " + CACHE)
    if args.exit_when_idle:
        def watchdog():
            while True:
                time.sleep(15)
                if time.time() - LAST_PING[0] > 120:
                    os._exit(0)
        threading.Thread(target=watchdog, daemon=True).start()
    if not args.no_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
