#!/usr/bin/env python3
"""Migrate llm-access local caches (<=0.2.x) to the 0.3.0 file naming.

Usage:
    python3 scripts/migrate_cache_0_3_0.py <dir> [--dry-run]
        [--strip-provider-prefix[=openai,ollama]] [--backup-dir PATH]

Old files `<Origin>_<params>.json` become `Chat_<params>.json` or
`Embedding_<params>.json`; files that map to the same target are merged.
Sources are moved (never deleted) to the backup dir (default
<dir>/pre-0.3.0-backup).

WARNING: do not run while the caches are in use by a running application.
"""
import argparse
import json
import os
import re
import shutil
import sys
import tempfile
from dataclasses import dataclass, field

# Bifrost providers that map to a native llm-access platform (used by --strip-provider-prefix without a value).
DEFAULT_PROVIDERS = "openai,ollama"
NAME_RE = re.compile(r"^([A-Za-z0-9$]+)_(.+)\.json$")


@dataclass
class Options:
    strip_providers: frozenset = frozenset()
    backup_dir: str = None


@dataclass
class Plan:
    directory: str
    backup_dir: str
    targets: dict = field(default_factory=dict)    # name -> {"sources": [names], "data": dict, "added": {src: n}}
    conflicts: list = field(default_factory=list)  # (target, key, kept_from, dropped_from)
    skipped: list = field(default_factory=list)    # (name, reason)


def _is_number_list(value):
    try:
        v = json.loads(value)
    except (TypeError, ValueError):
        return False
    return isinstance(v, list) and all(isinstance(x, (int, float)) and not isinstance(x, bool) for x in v)


def _load(path):
    """Returns (data, error); data None with error None means blank file."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if not text.strip():
        return None, "empty file"
    try:
        data = json.loads(text)
    except ValueError as e:
        return None, f"invalid JSON ({e})"
    if not isinstance(data, dict) or not all(isinstance(v, str) for v in data.values()):
        return None, "not a JSON object of strings"
    return data, None


def _target_name(origin, params, options):
    embedding = origin.endswith("EmbeddingCreator")
    seg, sep, rest = params.partition("--")
    if sep and ((origin == "BifrostEmbeddingCreator") or (not embedding and seg in options.strip_providers)):
        params = rest
    return ("Embedding_" if embedding else "Chat_") + params + ".json", embedding


def plan(directory, options=None):
    options = options or Options()
    p = Plan(directory, options.backup_dir or os.path.join(directory, "pre-0.3.0-backup"))
    groups = {}
    for name in sorted(os.listdir(directory)):
        path = os.path.join(directory, name)
        if not name.endswith(".json") or not os.path.isfile(path) or os.path.islink(path):
            continue
        if name.endswith(".tmp.json"):
            p.skipped.append((name, "temporary file"))
            continue
        if name.startswith(("Chat_", "Embedding_")):
            continue
        m = NAME_RE.match(name)
        data, err = _load(path)
        if err:
            p.skipped.append((name, err))
            continue
        if not m:
            p.skipped.append((name, "name does not match <Origin>_<parameters>.json"))
            continue
        target, embedding = _target_name(m.group(1), m.group(2), options)
        if any(_is_number_list(v) != embedding for v in data.values()):
            p.skipped.append((name, "content does not match kind (" + ("embedding" if embedding else "chat") + ")"))
            continue
        groups.setdefault(target, []).append((name, data))
    for target, sources in sorted(groups.items()):
        tpath = os.path.join(directory, target)
        merged, owner = {}, {}
        if os.path.exists(tpath):
            existing, err = _load(tpath)
            if err and err != "empty file":
                p.skipped.extend((n, f"target {target} unreadable: {err}") for n, _ in sources)
                continue
            merged = dict(existing or {})
            owner = {k: target for k in merged}
        added = {}
        for name, data in sources:
            added[name] = 0
            for k, v in data.items():
                if k not in merged:
                    merged[k], owner[k] = v, name
                    added[name] += 1
                elif merged[k] != v:
                    p.conflicts.append((target, k, owner[k], name))
        p.targets[target] = {"sources": [n for n, _ in sources], "data": merged, "added": added}
    return p


def _free_name(directory, name):
    candidate, i = name, 1
    while os.path.exists(os.path.join(directory, candidate)):
        candidate = f"{name}.{i}"
        i += 1
    return candidate


def apply(p):
    """Writes the plan; returns a list of (source, backup name) moves."""
    moved = []
    for target, t in p.targets.items():
        fd, tmp = tempfile.mkstemp(dir=p.directory, prefix=target + ".", suffix=".tmp")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump(t["data"], f, separators=(",", ":"), ensure_ascii=False)
            os.replace(tmp, os.path.join(p.directory, target))
        except BaseException:
            if os.path.exists(tmp):
                os.remove(tmp)
            raise
        os.makedirs(p.backup_dir, exist_ok=True)
        for src in t["sources"]:
            dest = _free_name(p.backup_dir, src)
            shutil.move(os.path.join(p.directory, src), os.path.join(p.backup_dir, dest))
            moved.append((src, dest))
    return moved


def report(p, dry_run, out=print):
    for target, t in p.targets.items():
        out(f"{target}: {len(t['data'])} entries")
        for s in t["sources"]:
            out(f"    <- {s} (+{t['added'][s]} new)")
    for target, key, kept, dropped in p.conflicts:
        out(f"CONFLICT in {target}, key {key}: kept value from {kept}, dropped value from {dropped}")
    for name, reason in p.skipped:
        out(f"SKIPPED {name}: {reason}")
    if not p.targets and not p.skipped:
        out("nothing to migrate")
    if dry_run:
        out("dry run — nothing written")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Migrate llm-access caches to 0.3.0 file names.")
    ap.add_argument("dir")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--strip-provider-prefix", nargs="?", const=DEFAULT_PROVIDERS, default=None, metavar="PROVIDERS")
    ap.add_argument("--backup-dir")
    try:
        args = ap.parse_args(argv)
    except SystemExit as e:
        return 2 if e.code else 0
    if not os.path.isdir(args.dir):
        print(f"error: not a directory: {args.dir}", file=sys.stderr)
        return 2
    providers = frozenset(x.strip() for x in (args.strip_provider_prefix or "").split(",") if x.strip())
    p = plan(args.dir, Options(providers, args.backup_dir))
    if not args.dry_run:
        apply(p)
    report(p, args.dry_run)
    return 1 if p.conflicts or p.skipped else 0


if __name__ == "__main__":
    sys.exit(main())
