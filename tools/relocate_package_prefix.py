#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"


def replace_package_decl(file_path: Path, old_pkg_line: str, new_pkg_line: str) -> None:
    txt = file_path.read_text(encoding="utf-8")
    if old_pkg_line in txt:
        file_path.write_text(txt.replace(old_pkg_line, new_pkg_line, 1), encoding="utf-8")


def replace_prefix_in_tree(old_prefix: str, new_prefix: str) -> int:
    changed = 0
    for p in ROOT.rglob("*.java"):
        txt = p.read_text(encoding="utf-8")
        if old_prefix in txt:
            p.write_text(txt.replace(old_prefix, new_prefix), encoding="utf-8")
            changed += 1
    return changed


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--moved-dir", required=True, help="Directory containing moved .java files (relative to repo root)")
    ap.add_argument("--old-package", required=True, help="Old package name, e.g. com.foo.bar")
    ap.add_argument("--new-package", required=True, help="New package name, e.g. com.foo.baz")
    ap.add_argument("--old-prefix", required=True, help="Old prefix for imports/FQN, typically oldPackage + '.'")
    ap.add_argument("--new-prefix", required=True, help="New prefix for imports/FQN, typically newPackage + '.'")
    args = ap.parse_args()

    moved_dir = Path(__file__).resolve().parents[1] / args.moved_dir
    old_pkg_line = f"package {args.old_package};"
    new_pkg_line = f"package {args.new_package};"

    for p in moved_dir.glob("*.java"):
        replace_package_decl(p, old_pkg_line, new_pkg_line)

    replace_prefix_in_tree(args.old_prefix, args.new_prefix)


if __name__ == "__main__":
    main()

