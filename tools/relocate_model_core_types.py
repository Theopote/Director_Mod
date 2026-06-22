#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
TARGET_DIR = ROOT / "com" / "director" / "timeline" / "model"


def main() -> None:
    # Fix package declarations in moved files
    replacements = {
        "package com.director.ui.timeline.model;": "package com.director.timeline.model;",
    }
    for p in [TARGET_DIR / "TrackType.java", TARGET_DIR / "TimelineInterpolation.java"]:
        txt = p.read_text(encoding="utf-8")
        for old, new in replacements.items():
            if old in txt:
                txt = txt.replace(old, new, 1)
        p.write_text(txt, encoding="utf-8")

    # Replace imports/FQNs across project for these two types only
    exact_map = {
        "com.director.ui.timeline.model.TrackType": "com.director.timeline.model.TrackType",
        "com.director.ui.timeline.model.TimelineInterpolation": "com.director.timeline.model.TimelineInterpolation",
    }
    short_imports = {
        "import com.director.ui.timeline.model.TrackType;": "import com.director.timeline.model.TrackType;",
        "import com.director.ui.timeline.model.TimelineInterpolation;": "import com.director.timeline.model.TimelineInterpolation;",
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

