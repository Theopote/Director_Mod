#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
MOVED_DIR = ROOT / "com" / "director" / "timeline" / "playback" / "entity"


def main() -> None:
    old_pkg = "package com.director.ui.timeline.playback.entity;"
    new_pkg = "package com.director.timeline.playback.entity;"

    # 1) Fix package declaration in moved files
    for p in MOVED_DIR.glob("*.java"):
        txt = p.read_text(encoding="utf-8")
        if old_pkg in txt:
            p.write_text(txt.replace(old_pkg, new_pkg, 1), encoding="utf-8")

    # 2) Replace imports / fully qualified references across codebase
    old = "com.director.ui.timeline.playback.entity."
    new = "com.director.timeline.playback.entity."
    for p in ROOT.rglob("*.java"):
        txt = p.read_text(encoding="utf-8")
        if old in txt:
            p.write_text(txt.replace(old, new), encoding="utf-8")


if __name__ == "__main__":
    main()

