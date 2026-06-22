#!/usr/bin/env python3
"""Batch 3.3: fix package declarations and replace all ui.timeline.model imports/FQNs."""
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
NEW_MODEL = ROOT / "com" / "director" / "timeline" / "model"

# Subpackages moved
SUBPKGS = [
    "",          # root
    "audio",
    "camera",
    "camera/path",
    "clip",
    "entity",
    "season",
    "selection",
    "time",
    "weather",
]

def fix_package_decls() -> None:
    for sub in SUBPKGS:
        pkg_dir = NEW_MODEL / sub if sub else NEW_MODEL
        old_pkg = ("com.director.ui.timeline.model" + ("." + sub.replace("/", ".") if sub else ""))
        new_pkg = ("com.director.timeline.model"    + ("." + sub.replace("/", ".") if sub else ""))
        for p in pkg_dir.glob("*.java"):
            txt = p.read_text(encoding="utf-8")
            old_decl = f"package {old_pkg};"
            new_decl = f"package {new_pkg};"
            if old_decl in txt:
                p.write_text(txt.replace(old_decl, new_decl, 1), encoding="utf-8")

def fix_imports() -> None:
    # All subpackage patterns we need to rewrite
    patterns: list[tuple[str, str]] = []
    for sub in SUBPKGS:
        old_base = "com.director.ui.timeline.model" + ("." + sub.replace("/", ".") if sub else "")
        new_base = "com.director.timeline.model"    + ("." + sub.replace("/", ".") if sub else "")
        patterns.append((f"import {old_base}.", f"import {new_base}."))
        patterns.append((f"import {old_base};", f"import {new_base};"))
        patterns.append((old_base + ".", new_base + "."))

    for p in ROOT.rglob("*.java"):
        txt = p.read_text(encoding="utf-8")
        new_txt = txt
        for old, new in patterns:
            new_txt = new_txt.replace(old, new)
        if new_txt != txt:
            p.write_text(new_txt, encoding="utf-8")

if __name__ == "__main__":
    fix_package_decls()
    fix_imports()
    print("Done.")
