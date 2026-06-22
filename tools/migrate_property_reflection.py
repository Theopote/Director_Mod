#!/usr/bin/env python3
"""Replace duplicated reflection helpers with PropertyReflection in property adapters."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADAPTERS = ROOT / "src/main/java/com/director/ui/properties/adapters"

REPLACEMENTS = [
    ("getStringProperty(", "PropertyReflection.getString("),
    ("setStringProperty(", "PropertyReflection.setString("),
    ("getBooleanProperty(", "PropertyReflection.getBoolean("),
    ("setBooleanProperty(", "PropertyReflection.setBoolean("),
    ("getIntProperty(", "PropertyReflection.getInt("),
    ("setIntProperty(", "PropertyReflection.setInt("),
    ("getFloatProperty(", "PropertyReflection.getFloat("),
    ("setFloatProperty(", "PropertyReflection.setFloat("),
    ("getFloatArrayProperty(", "PropertyReflection.getFloatArray("),
    ("setFloatArrayProperty(", "PropertyReflection.setFloatArray("),
    ("callMethod(", "PropertyReflection.invokeVoid("),
]

HELPER_START = re.compile(r"\n\s*// =+ 反射辅助方法 =+\s*\n")
HELPER_END = re.compile(r"\n\s*@Override\n")


def migrate_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "getStringProperty(" not in text and "getBooleanProperty(" not in text:
        return False

    original = text
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)

    text = re.sub(
        r"getObjectProperty\((\w+)\)",
        r"PropertyReflection.readProperty(\1, \"materials\", \"getMaterials\")",
        text,
    )

    if "import com.director.ui.properties.PropertyReflection;" not in text:
        text = text.replace(
            "import com.director.ui.properties.IPropertyAdapter;",
            "import com.director.ui.properties.IPropertyAdapter;\nimport com.director.ui.properties.PropertyReflection;",
        )

    m = HELPER_START.search(text)
    if m:
        rest = text[m.end() :]
        end = HELPER_END.search(rest)
        if end:
            text = text[: m.start()] + "\n" + rest[end.start() + 1 :]

    if text != original:
        path.write_text(text, encoding="utf-8")
        print("migrated", path.name)
        return True
    return False


def main() -> None:
    count = 0
    for path in sorted(ADAPTERS.glob("*.java")):
        if migrate_file(path):
            count += 1
    print(f"done: {count} files")


if __name__ == "__main__":
    main()
