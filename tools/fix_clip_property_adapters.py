"""Fix corrupted adapter files after incomplete method removal."""
from pathlib import Path
import re

def find_method_end(lines, method_name):
    pattern = re.compile(
        rf"^\s+private boolean {re.escape(method_name)}\s*\("
    )
    start = None
    for i, line in enumerate(lines):
        if pattern.match(line):
            start = i
            break
    if start is None:
        return None
    brace = 0
    started = False
    for i in range(start, len(lines)):
        for ch in lines[i]:
            if ch == "{":
                brace += 1
                started = True
            elif ch == "}":
                brace -= 1
        if started and brace == 0:
            return i
    return None


def clean_header(lines):
    out = []
    skip_until_override = False
    for line in lines:
        if skip_until_override:
            if line.strip().startswith("@Override"):
                skip_until_override = False
                out.append(line)
            continue
        if line.strip() == "}" and "class" not in line and "switch" not in line:
            # orphan brace after field section
            prev = out[-1].strip() if out else ""
            if prev.startswith("//") or prev == "" or "缓存" in prev:
                skip_until_override = True
                continue
        out.append(line)
    return out


def fix_adapter(path: Path):
    lines = path.read_text(encoding="utf-8").splitlines()
    lines = clean_header(lines)
    end = find_method_end(lines, "renderAnimationSettingsInternal")
    if end is None:
        raise SystemExit(f"renderAnimationSettingsInternal not found in {path}")
    lines = lines[: end + 1] + ["}"]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Fixed {path} -> {len(lines)} lines")


if __name__ == "__main__":
    fix_adapter(Path("src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyAdapter.java"))
    fix_adapter(Path("src/main/java/com/director/ui/properties/adapters/EraseTimelineClipPropertyAdapter.java"))
