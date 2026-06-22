#!/usr/bin/env python3
"""Batch 4: replace ui.timeline.container imports with timeline.container."""
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"

OLD = "com.director.ui.timeline.container.ContainerTimelineService"
NEW = "com.director.timeline.container.ContainerTimelineService"

OLD_ADAPTER = "com.director.core.container.commands.ContainerTimelineCommandAdapter"
NEW_ADAPTER = "com.director.ui.container.ContainerTimelineCommandAdapter"


def main() -> None:
    for p in ROOT.rglob("*.java"):
        txt = p.read_text(encoding="utf-8")
        new_txt = txt.replace(OLD, NEW).replace(OLD_ADAPTER, NEW_ADAPTER)
        new_txt = new_txt.replace(
            f"import {OLD};",
            f"import {NEW};",
        )
        if new_txt != txt:
            p.write_text(new_txt, encoding="utf-8")
    print("Done.")


if __name__ == "__main__":
    main()
