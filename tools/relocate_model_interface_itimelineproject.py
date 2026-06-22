#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
FILE = ROOT / "com" / "director" / "timeline" / "model" / "ITimelineProject.java"


def main() -> None:
    txt = FILE.read_text(encoding="utf-8")
    txt = txt.replace(
        "package com.director.ui.timeline.model;",
        "package com.director.timeline.model;",
        1,
    )
    FILE.write_text(txt, encoding="utf-8")

    old = "com.director.ui.timeline.model.ITimelineProject"
    new = "com.director.timeline.model.ITimelineProject"
    for p in ROOT.rglob("*.java"):
        t = p.read_text(encoding="utf-8")
        nt = t.replace(old, new).replace(
            "import com.director.ui.timeline.model.ITimelineProject;",
            "import com.director.timeline.model.ITimelineProject;",
        )
        if nt != t:
            p.write_text(nt, encoding="utf-8")


if __name__ == "__main__":
    main()

