#!/usr/bin/env python3
"""Migrate hot-path singleton access to DirectorServices (phase 2)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"

SKIP_FILES = {
    "DirectorServices.java",
    "SelectionManager.java",
}

REPLACEMENTS = [
    (
        r"Objects\.requireNonNull\(com\.director\.core\.container\.ContainerManager\.getInstance\(\)\)",
        "DirectorServices.get().containers()",
    ),
    (
        r"Objects\.requireNonNull\(ContainerManager\.getInstance\(\)\)",
        "DirectorServices.get().containers()",
    ),
    (
        r"com\.director\.core\.container\.ContainerManager\.getInstance\(\)",
        "DirectorServices.get().containers()",
    ),
    (r"ContainerManager\.getInstance\(\)", "DirectorServices.get().containers()"),
    (
        r"com\.director\.ui\.timeline\.container\.ContainerTimelineService\.getInstance\(\)",
        "DirectorServices.get().containerTimeline()",
    ),
    (
        r"ContainerTimelineService\.getInstance\(\)",
        "DirectorServices.get().containerTimeline()",
    ),
    (
        r"com\.director\.core\.selection\.SelectionManager\.get\(\)",
        "DirectorServices.get().selection()",
    ),
]

IMPORT_LINE = "import com.director.core.DirectorServices;\n"


def add_import(text: str) -> str:
    if "import com.director.core.DirectorServices" in text:
        return text
    match = re.match(r"(package [^;]+;\n)(\n)?", text)
    if not match:
        return IMPORT_LINE + text
    end = match.end()
    return text[:end] + ("\n" if not match.group(2) else "") + IMPORT_LINE + text[end:]


def migrate_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text

    for pattern, replacement in REPLACEMENTS:
        text = re.sub(pattern, replacement, text)

    if (
        "import com.director.core.selection.SelectionManager" in text
        and path.name != "SelectionManager.java"
    ):
        text = re.sub(
            r"\bSelectionManager\.get\(\)",
            "DirectorServices.get().selection()",
            text,
        )

    if text == original:
        return False

    if "DirectorServices.get()" in text:
        text = add_import(text)

    path.write_text(text, encoding="utf-8")
    return True


def main() -> None:
    changed = 0
    for path in sorted(ROOT.rglob("*.java")):
        if path.name in SKIP_FILES:
            continue
        if migrate_file(path):
            print(path.relative_to(ROOT.parents[1]))
            changed += 1
    print(f"Updated {changed} files")


if __name__ == "__main__":
    main()
