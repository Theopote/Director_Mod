#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
TARGET_DIR = ROOT / "com" / "director" / "timeline" / "model"


def main() -> None:
    p = TARGET_DIR / "TimelineTrack.java"
    txt = p.read_text(encoding="utf-8")
    txt = txt.replace(
        "package com.director.ui.timeline.model;",
        "package com.director.timeline.model;",
        1,
    )
    p.write_text(txt, encoding="utf-8")

    exact_map = {
        "com.director.ui.timeline.model.TimelineTrack": "com.director.timeline.model.TimelineTrack",
    }
    short_imports = {
        "import com.director.ui.timeline.model.TimelineTrack;": "import com.director.timeline.model.TimelineTrack;",
    }

    for p in ROOT.rglob("*.java"):
        txt = p.read_text(encoding="utf-8")
        new_txt = txt
        for old, new in exact_map.items():
            new_txt = new_txt.replace(old, new)
        for old, new in short_imports.items():
            new_txt = new_txt.replace(old, new)
        if new_txt != txt:
            p.write_text(new_txt, encoding="utf-8")


if __name__ == "__main__":
    main()
