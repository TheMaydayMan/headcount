#!/usr/bin/env python3
"""Generates docs/config-reference.md from the bundled, commented config files.

Every option is listed with its default and the comment written above it in the file, so the reference always matches
the shipped defaults. Run from the project root:  python3 tools/gen-config-docs.py
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
OUT = os.path.join(ROOT, "docs", "config-reference.md")

# File -> (title, subtrees documented on their own pages instead of key by key)
FILES = [
    ("config.yml", "config.yml", {"hooks.cycle-start", "hooks.cycle-end", "hooks.warnings", "hooks.player", "audit.types"},
     "Hooks are explained in [hooks.md](hooks.md)."),
    ("trade.yml", "trade.yml", set(), ""),
    ("scoring.yml", "scoring.yml", set(), "Every formula variable and function is in [formula.md](formula.md)."),
    ("integrations.yml", "integrations.yml", set(), "What each integration does is in [integrations.md](integrations.md)."),
    ("shop.yml", "shop.yml", {"tiers"}, "Tiers and entries are explained in [shop.md](shop.md)."),
    ("titles.yml", "titles.yml", {"titles"}, "Defining titles is explained in [titles.md](titles.md)."),
    ("statuses.yml", "statuses.yml", {"statuses"}, "Every status source is in [statuses.md](statuses.md)."),
    ("outcomes.yml", "outcomes.yml", {"rules"}, "Writing rules is explained in [outcomes.md](outcomes.md)."),
    ("actions.yml", "actions.yml", {"lists"}, "Every action type is in [actions.md](actions.md)."),
]

KEY = re.compile(r"^(\s*)([A-Za-z0-9_.\-\"']+):(?:\s+(.*))?$")


def clean(value):
    if value is None:
        return ""
    v = value.strip()
    return v


def parse(path, skip):
    lines = open(path, encoding="utf-8").read().splitlines()
    rows = []
    stack = []  # (indent, key)
    comments = []
    header = []
    seen_key = False
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith("#"):
            text = stripped.lstrip("#").strip()
            if not seen_key:
                if not text or not set(text) <= set("=-"):
                    header.append(stripped[3:] if stripped.startswith("#  ") else stripped[2:] if stripped.startswith("# ") else stripped[1:])
            elif not set(text) <= set("=-"):
                comments.append(text)
            i += 1
            continue
        if not stripped:
            if seen_key:
                comments = [] if not comments else comments
            i += 1
            continue
        m = KEY.match(line)
        if not m or stripped.startswith("- "):
            i += 1
            continue
        seen_key = True
        indent, key, value = len(m.group(1)), m.group(2).strip("\"'"), m.group(3)
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        full = ".".join(k for _, k in stack)
        if full == "config-version":
            comments = []
            i += 1
            continue
        skipped = any(full == s or full.startswith(s + ".") for s in skip)
        # Gather a block list default ("- item" lines) under this key.
        default = clean(value)
        if not default:
            items = []
            j = i + 1
            while j < len(lines) and (lines[j].strip().startswith("- ") or lines[j].strip().startswith("#") or not lines[j].strip()):
                if lines[j].strip().startswith("- ") and len(lines[j]) - len(lines[j].lstrip()) > indent - 2:
                    items.append(lines[j].strip()[2:].strip())
                j += 1
            nxt = lines[i + 1] if i + 1 < len(lines) else ""
            nxt_indent = len(nxt) - len(nxt.lstrip())
            if items and all(len(x) < 120 for x in items):
                default = "[" + ", ".join(items) + "]" if len(items) <= 6 else f"list of {len(items)}"
            elif nxt.strip() and nxt_indent > indent and not nxt.strip().startswith("- "):
                default = "(section)"
        if not skipped or full in skip:
            desc = " ".join(comments).strip()
            if full in skip:
                desc = (desc + " " if desc else "") + "(see the linked page)"
                default = "(section)"
            rows.append((full, default, desc))
        comments = []
        i += 1
    return header, rows


def md_escape(s):
    return s.replace("|", "\\|").replace("<", "&lt;").replace(">", "&gt;")


def main():
    out = ["# Config reference", "",
           "Generated from the bundled config files by `tools/gen-config-docs.py`. Each option shows its default and the",
           "comment above it in the file. Durations accept `30s`, `10m`, `2h`, `3d`, `1w` or a number of seconds; text uses",
           "MiniMessage and placeholders (see [text.md](text.md)).", ""]
    out.append("Files: " + " · ".join(f"[{f}](#{f.replace('.', '')})" for f, *_ in FILES))
    out.append("")
    for fname, title, skip, note in FILES:
        header, rows = parse(os.path.join(RES, fname), skip)
        out.append(f"## {title}")
        out.append("")
        intro = [h for h in header if not h.startswith("Headcount -")]
        while intro and not intro[0]:
            intro.pop(0)
        if intro:
            out.append("```text")
            out.extend(intro)
            out.append("```")
            out.append("")
        if note:
            out.append(note)
            out.append("")
        out.append("| Option | Default | Description |")
        out.append("|---|---|---|")
        for key, default, desc in rows:
            d = md_escape(default)
            if len(d) > 70:
                d = d[:67] + "..."
            if len(desc) > 300:
                desc = desc[:297].rsplit(" ", 1)[0] + "... (the full comment is in the file)"
            out.append(f"| `{key}` | `{d}` | {md_escape(desc)} |" if d else f"| `{key}` |  | {md_escape(desc)} |")
        out.append("")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(out))
    print("wrote", OUT, sum(1 for l in out if l.startswith("| `")), "options")


if __name__ == "__main__":
    main()
