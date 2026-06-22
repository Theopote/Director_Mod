#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
TARGET_DIR = ROOT / "com" / "director" / "timeline" / "model"


def main() -> None:
    replacements = {
        "package com.director.ui.timeline.model;": "package com.director.timeline.model;",
    }
    for name in ("TimelineSelectable.java", "TimelineClip.java"):
        p = TARGET_DIR / name
        txt = p.read_text(encoding="utf-8")
        for old, new in replacements.items():
            if old in txt:
                txt = txt.replace(old, new, 1)
        p.write_text(txt, encoding="utf-8")

    exact_map = {
        "com.director.ui.timeline.model.TimelineClip": "com.director.timeline.model.TimelineClip",
        "com.director.ui.timeline.model.TimelineSelectable": "com.director.timeline.model.TimelineSelectable",
    }
    short_imports = {
        "import com.director.ui.timeline.model.TimelineClip;": "import com.director.timeline.model.TimelineClip;",
        "import com.director.ui.timeline.model.TimelineSelectable;": "import com.director.timeline.model.TimelineSelectable;",
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
