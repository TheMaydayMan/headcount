#!/usr/bin/env python3
"""Builds catalog.json for Headcount Studio from the plugin's own files, so the app's reference always matches the
plugin: commands and permissions (plugin.yml, docs/commands.md), placeholders (Placeholders.java), action types
(docs/actions.md), status sources (docs/statuses.md), formula variables and functions (ScoreService.java,
scoring.yml), every config option with its default and comment (the bundled configs), recipes (docs/examples.md)
and the docs pages. Runs with every ./gradlew build; run it by hand with:  python3 tools/studio/build-catalog.py
"""
import importlib.util
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))   # Headcount/
RES = os.path.join(ROOT, "src", "main", "resources")
DOCS = os.path.join(ROOT, "docs")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "gradychastain", "headcount")
OUT = os.path.join(HERE, "catalog.json")


def read(*p):
    with open(os.path.join(*p), encoding="utf-8") as f:
        return f.read()


def strip_md(s):
    return re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", s).strip()


def md_tables(text):
    """(heading path, header cells, rows) for every markdown table, with the nearest ## and ### headings."""
    h2 = h3 = ""
    out = []
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("## "):
            h2, h3 = line[3:].strip(), ""
        elif line.startswith("### "):
            h3 = line[4:].strip()
        elif line.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|$", lines[i + 1].strip()):
            header = split_row(line)
            rows = []
            i += 2
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            out.append((h2, h3, header, rows))
            continue
        i += 1
    return out


def split_row(line):
    cells, cur, tick = [], "", False
    body = line.strip()[1:-1] if line.strip().endswith("|") else line.strip()[1:]
    j = 0
    while j < len(body):
        c = body[j]
        if c == "`":
            tick = not tick
        if c == "\\" and j + 1 < len(body) and body[j + 1] == "|":
            cur += "|"
            j += 2
            continue
        if c == "|" and not tick:
            cells.append(cur.strip())
            cur = ""
        else:
            cur += c
        j += 1
    cells.append(cur.strip())
    return cells


def ticks(s):
    return re.findall(r"`([^`]+)`", s)


def settings_of(text):
    """
    Setting names from a docs 'Settings' cell. In "`audience`: `player`, `all`, `world`" the words after a "key:" are
    its values; in "`effect`, `amplifier: 0`" each part names a setting (a tick with "name: default" inside, too).
    """
    out = []
    depth = 0
    part = ""
    after_colon = False  # the last key was written `key`: so bare words that follow are its values
    for c in text + ",":
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == "," and depth <= 0:
            m = re.match(r"\s*`([^`]+)`(:?)", part)
            if m:
                tok, colon = m.group(1), m.group(2) == ":"
                inner = re.match(r"^([a-z][a-z0-9-]*)\s*:\s", tok + " ")
                if inner and "<" not in tok:
                    name, after_colon = inner.group(1), False
                elif re.match(r"^[a-z][a-z0-9-]*$", tok) and (colon or not after_colon):
                    name, after_colon = tok, colon
                else:
                    name = None
                if name and name not in out:
                    out.append(name)
            part = ""
        else:
            part += c
    return out


def commands():
    text = read(DOCS, "commands.md")
    out = []
    for h2, _, header, rows in md_tables(text):
        if not header or header[0] != "Command":
            continue
        for r in rows:
            if len(r) < 3:
                continue
            syn = [t for t in ticks(r[0])]
            perm = ticks(r[1])
            out.append({"group": "player" if h2.startswith("Players") else "admin", "syntax": syn, "permission": perm,
                        "permissionText": strip_md(r[1]), "does": strip_md(r[2])})
    out.append({"group": "player", "syntax": ["/pvp [on|off|status]", "/pvp <player> [on|off]"], "permission": ["headcount.pvp", "headcount.pvp.others"],
                "permissionText": "headcount.pvp (everyone); headcount.pvp.others (op)",
                "does": "Turns your PvP on or off with the included PvP system (integrations.yml pvp.provider: included). Admins can see and set anyone's."})
    out.append({"group": "admin", "syntax": ["/hca reset cycle [delete-heads|keep-heads] confirm"], "permission": ["headcount.admin.reset.cycle"],
                "permissionText": "headcount.admin.reset.cycle", "does": "Starts the current cycle over under the same number: vaults refilled, older heads stop counting (deleted or defunct)."})
    out.append({"group": "admin", "syntax": ["/hca reset hard [delete-heads|keep-heads]", "/hca reset hard <code>"], "permission": ["headcount.admin.reset.hard"],
                "permissionText": "headcount.admin.reset.hard (op, not part of headcount.admin; the console always may)",
                "does": "Deletes all Headcount data and starts again from cycle 1, after typing a new random code for each confirmation."})
    bypass = []
    for h2, _, header, rows in md_tables(text):
        if header and header[0] == "Permission":
            for r in rows:
                bypass.append({"permission": ticks(r[0]), "bypasses": strip_md(r[1])})
    return out, bypass


def permissions():
    # A tiny reader for plugin.yml's permissions block (no PyYAML needed).
    lines = read(RES, "plugin.yml").splitlines()
    perms, cur, in_perms, in_children = {}, None, False, False
    for line in lines:
        if line.startswith("permissions:"):
            in_perms = True
            continue
        if not in_perms or not line.strip():
            continue
        if re.match(r"^  [a-z]", line):
            cur = line.strip().rstrip(":")
            perms[cur] = {"name": cur, "description": "", "default": "op", "children": []}
            in_children = False
        elif cur and line.strip().startswith("description:"):
            perms[cur]["description"] = line.split(":", 1)[1].strip().strip("'\"")
        elif cur and line.strip().startswith("default:"):
            perms[cur]["default"] = line.split(":", 1)[1].strip()
        elif cur and line.strip() == "children:":
            in_children = True
        elif cur and in_children and re.match(r"^      [a-z]", line):
            perms[cur]["children"].append(line.strip().split(":")[0])
    return list(perms.values())


def placeholders():
    src = read(JAVA, "placeholder", "Placeholders.java")
    return [{"name": n, "description": d} for n, d in re.findall(r'ALL\.put\("([^"]+)",\s*"([^"]*)"\)', src)]


def actions():
    out = []
    for h2, h3, header, rows in md_tables(read(DOCS, "actions.md")):
        if not header or header[0] != "Type":
            continue
        for r in rows:
            names = ticks(r[0])
            cols = dict(zip([h.lower() for h in header], r))
            for name in names:
                out.append({"type": name, "group": h3 or h2, "main": (ticks(cols.get("main", "")) or [""])[0],
                            "settingsText": strip_md(cols.get("settings", "")), "settings": settings_of(cols.get("settings", "")),
                            "online": cols.get("online", ""), "undo": strip_md(cols.get("undo", ""))})
    return out


def statuses():
    out = []
    for h2, h3, header, rows in md_tables(read(DOCS, "statuses.md")):
        if not header or header[0] != "Source":
            continue
        for r in rows:
            cols = dict(zip([h.lower() for h in header], r))
            for name in ticks(r[0]):
                out.append({"source": name, "group": h3 or h2, "settingsText": strip_md(cols.get("settings", "")),
                            "settings": settings_of(cols.get("settings", "")), "trueWhen": strip_md(cols.get("true when", ""))})
    # 1.1 renamed essentials to player-state (both work).
    for s in out:
        if s["source"] == "essentials":
            out.append({**s, "source": "player-state", "trueWhen": "The player-state provider (integrations.yml: included or EssentialsX) reports that state."})
            break
    return out


def formula():
    src = read(JAVA, "scoring", "ScoreService.java")
    variables = [{"name": n, "description": d} for n, d in re.findall(r'VARIABLES\.put\("([^"]+)",\s*"([^"]*)"\)', src)]
    funcs = ["min", "max", "avg", "floor", "ceil", "round", "trunc", "abs", "sign", "pow", "sqrt", "cbrt", "exp", "log", "ln",
             "log10", "log2", "clamp", "if", "sin", "cos", "tan"]
    ideas = re.findall(r"^#\s+(.+?):\s{2,}(.+)$", read(RES, "scoring.yml"), re.M)
    return {"variables": variables, "functions": funcs, "ideas": [{"name": a.strip(), "formula": b.strip()} for a, b in ideas]}


def options():
    spec = importlib.util.spec_from_file_location("gen", os.path.join(ROOT, "tools", "gen-config-docs.py"))
    gen = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gen)
    out = []
    for name in ["config.yml", "integrations.yml", "statuses.yml", "trade.yml", "scoring.yml", "outcomes.yml", "shop.yml",
                 "titles.yml", "actions.yml"]:
        _, rows = gen.parse(os.path.join(RES, name), set())
        for path, default, desc in rows:
            out.append({"file": name, "path": path, "default": default, "help": desc})
    return out


def recipes():
    text = read(DOCS, "examples.md")
    out = []
    for block in re.split(r"^## ", text, flags=re.M)[1:]:
        title, _, body = block.partition("\n")
        codes = re.findall(r"```yaml\n(.*?)```", body, re.S)
        prose = re.sub(r"```.*?```", "", body, flags=re.S).strip()
        out.append({"title": title.strip(), "text": strip_md(prose), "yaml": codes})
    return out


def docs():
    out = []
    for f in sorted(os.listdir(DOCS)):
        if f.endswith(".md"):
            first = read(DOCS, f).splitlines()[0].lstrip("# ").strip()
            out.append({"name": f, "title": first})
    return out


def main():
    cmds, bypass = commands()
    cat = {"commands": cmds, "bypass": bypass, "permissions": permissions(), "placeholders": placeholders(),
           "actions": actions(), "statuses": statuses(), "formula": formula(), "options": options(),
           "recipes": recipes(), "docs": docs()}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(cat, f, indent=1, ensure_ascii=False)
    counts = ", ".join(f"{len(v) if isinstance(v, list) else len(v['variables'])} {k}" for k, v in cat.items())
    print(f"wrote {os.path.relpath(OUT, ROOT)}: {counts}")


if __name__ == "__main__":
    main()
