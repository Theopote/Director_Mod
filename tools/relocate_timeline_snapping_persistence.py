#!/usr/bin/env python3
"""Batch 5: fix package declarations and replace snapping/persistence imports."""
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"

REPLACEMENTS = [
    ("com.director.ui.timeline.snapping.", "com.director.timeline.snapping."),
    ("com.director.ui.timeline.persistence.", "com.director.timeline.persistence."),
    ("package com.director.ui.timeline.snapping;", "package com.director.timeline.snapping;"),
    ("package com.director.ui.timeline.persistence;", "package com.director.timeline.persistence;"),
]


def main() -> None:
    for p in ROOT.rglob("*.java"):
        txt = p.read_text(encoding="utf-8")
        new_txt = txt
        for old, new in REPLACEMENTS:
            new_txt = new_txt.replace(old, new)
        if new_txt != txt:
            p.write_text(new_txt, encoding="utf-8")
    print("Done.")


if __name__ == "__main__":
    main()
