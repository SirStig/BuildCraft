import pathlib, re, subprocess, sys

ROOT = pathlib.Path("/home/jkac/Developer/BuildCraft")
BASE = "origin/8.0.x-1.12.2"
PORT_LINE = "Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)"

MIT_API = [
    "The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the",
    'license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.',
]
MIT_PORT = [
    "This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.",
    'Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft',
    "source code distribution.",
]
MPL = [
    "This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL",
    "was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/",
]

# Every path in the 1.12.2 base branch, so provenance is resolved against what actually shipped
# rather than against whatever is still lying around in the working tree.
base_files = set(subprocess.run(
    ["git", "ls-tree", "-r", "--name-only", BASE], cwd=ROOT,
    capture_output=True, text=True, check=True).stdout.splitlines())

# Where a ported buildcraft/... path could have come from upstream.
PREFIXES = ["BuildCraftAPI/api/", "common/", "tests/",
            "sub_projects/expression/src/main/java/",
            "sub_projects/expression/src/generator/java/",
            "sub_projects/expression/src/test/java/"]

# Files the port moved to a different package, so the same-relative-path match below can't
# find their real upstream ancestor on its own. Keyed by the ported file's "buildcraft/..."
# relative path; value is its true 1.12.2 origin, same format upstream_for() would return.
RELOCATED = {
    "buildcraft/lib/tile/item/IAutoCraft.java": "common/buildcraft/lib/tile/craft/IAutoCraft.java",
}

def upstream_for(path: pathlib.Path):
    m = re.search(r"/java/(buildcraft/.*\.java)$", str(path))
    if not m:
        return None
    rel = m.group(1)
    if rel in RELOCATED:
        return RELOCATED[rel]
    for pre in PREFIXES:
        cand = pre + rel
        # BuildCraftAPI is a git submodule, so its files are a single gitlink in the base
        # tree rather than individual paths -- it has to be resolved on disk instead.
        if cand.startswith("BuildCraftAPI/"):
            if (ROOT / cand).exists():
                return cand
        elif cand in base_files:
            return cand
    return None

def show(p):
    if p.startswith("BuildCraftAPI/"):
        return (ROOT / p).read_text()
    return subprocess.run(["git", "show", f"{BASE}:{p}"], cwd=ROOT,
                          capture_output=True, text=True).stdout

def header_of(text):
    m = re.match(r"\s*(/\*.*?\*/)", text, re.S)
    return m.group(1) if m else ""

def copyrights(header):
    out = []
    for line in header.splitlines():
        line = re.sub(r"^\s*/?\*+\s?", "", line).strip()
        line = re.sub(r"\*/\s*$", "", line).strip()
        if line.lower().startswith("copyright"):
            out.append(line)
    return out

def build(crs, body):
    lines = ["/*"] + [" * " + c for c in crs] + [" *"] + [" * " + b for b in body] + [" */"]
    return "\n".join(lines)

rows = []
for path in sorted(ROOT.glob("modules/*/src/**/*.java")) + sorted(ROOT.glob("platforms/*/src/**/*.java")):
    text = path.read_text()
    old = header_of(text)
    up = upstream_for(path)

    if up:
        up_header = header_of(show(up))
        crs = copyrights(up_header)
        if "MIT License" in up_header:
            body, kind = MIT_API, "MIT(api)"
        elif "Mozilla Public License" in up_header:
            body, kind = MPL, "MPL"
        else:
            # No notice upstream: fall back to the tree it came from.
            if up.startswith("BuildCraftAPI/"):
                body, kind = MIT_API, "MIT(api)"
            else:
                body, kind = MPL, "MPL"
        origin = up
    else:
        crs, body, kind, origin = [], MIT_PORT, "MIT(port)", "-- port-authored --"

    crs = [c for c in crs if "Joshua Kac" not in c] + [PORT_LINE]
    new = build(crs, body)
    text = text.replace(old, new, 1) if old else new + "\n" + text.lstrip()
    if text != path.read_text():
        path.write_text(text)
    rows.append((kind, str(path).replace(str(ROOT) + "/", ""), origin))

from collections import Counter
print(Counter(k for k, _, _ in rows))
print()
for kind, p, origin in sorted(rows):
    if kind != "MPL":
        print(f"{kind:10} {p.split('/java/')[-1]:52} <- {origin}")
