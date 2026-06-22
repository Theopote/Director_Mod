#!/usr/bin/env python3
"""Migrate property adapter reflection helpers to PropertyReflection."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADAPTERS = ROOT / "src/main/java/com/director/ui/properties/adapters"

GENERIC = [
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
    ("getLongProperty(", "PropertyReflection.getLong("),
    ("callMethod(", "PropertyReflection.invokeVoid("),
    ("getObjectProperty(", "PropertyReflection.readProperty("),
]

FILE_LITERALS = {
    "CameraPropertyAdapter.java": [
        ("PropertyReflection.getInt(camera)", 'PropertyReflection.getInt(camera, "bokehQuality", "getBokehQuality", 2)'),
        ("PropertyReflection.setInt(camera, bokehQuality[0])", 'PropertyReflection.setInt(camera, "bokehQuality", "setBokehQuality", bokehQuality[0])'),
        ("PropertyReflection.getString(camera)", 'PropertyReflection.getString(camera, "animationName", "getAnimationName", "")'),
        ("PropertyReflection.setString(camera, nameBuffer.get())", 'PropertyReflection.setString(camera, "animationName", "setAnimationName", nameBuffer.get())'),
    ],
    "ProjectEnvironmentPropertyAdapter.java": [
        ("PropertyReflection.getInt(env)", 'PropertyReflection.getInt(env, "timeOfDay", "getTimeOfDay", 6000)'),
        ("PropertyReflection.setInt(env, timeOfDay[0])", 'PropertyReflection.setInt(env, "timeOfDay", "setTimeOfDay", timeOfDay[0])'),
        ("PropertyReflection.setInt(env, TIME_PRESETS[i])", 'PropertyReflection.setInt(env, "timeOfDay", "setTimeOfDay", TIME_PRESETS[i])'),
        ("PropertyReflection.setInt(env, 6000)", 'PropertyReflection.setInt(env, "timeOfDay", "setTimeOfDay", 6000)'),
        ("PropertyReflection.setInt(env, 12000)", 'PropertyReflection.setInt(env, "timeOfDay", "setTimeOfDay", 12000)'),
        ("PropertyReflection.setInt(env, 18000)", 'PropertyReflection.setInt(env, "timeOfDay", "setTimeOfDay", 18000)'),
        ("PropertyReflection.invokeVoid(env)", 'PropertyReflection.invokeVoid(env, "resetToDefault")'),
    ],
    "PostProcessingPropertyAdapter.java": [
        ('PropertyReflection.getBoolean(profile, "bloomEnabled", "isBloomEnabled")', 'PropertyReflection.getBoolean(profile, "bloomEnabled", "isBloomEnabled", false)'),
        ('PropertyReflection.getBoolean(profile, "depthOfField", "isDepthOfField")', 'PropertyReflection.getBoolean(profile, "depthOfField", "isDepthOfField", false)'),
        ('PropertyReflection.getBoolean(profile, "ssaoEnabled", "isSSAOEnabled")', 'PropertyReflection.getBoolean(profile, "ssaoEnabled", "isSSAOEnabled", false)'),
        ('PropertyReflection.getBoolean(profile, "ssrEnabled", "isSSREnabled")', 'PropertyReflection.getBoolean(profile, "ssrEnabled", "isSSREnabled", false)'),
        ("PropertyReflection.getInt(profile)", 'PropertyReflection.getInt(profile, "toneMapMode", "getToneMapMode", 0)'),
        ("PropertyReflection.setInt(profile, modeIdx.get())", 'PropertyReflection.setInt(profile, "toneMapMode", "setToneMapMode", modeIdx.get())'),
        ("PropertyReflection.invokeVoid(profile)", 'PropertyReflection.invokeVoid(profile, "resetToDefault")'),
    ],
    "TimelineSequencePropertyAdapter.java": [
        ("PropertyReflection.setString(sequence, nameBuffer.get())", 'PropertyReflection.setString(sequence, "name", "setName", nameBuffer.get())'),
        ("PropertyReflection.setBoolean(sequence, loop.get())", 'PropertyReflection.setBoolean(sequence, "loop", "setLoop", loop.get())'),
        ("PropertyReflection.getInt(sequence, \"playMode\", \"getPlayMode\")", 'PropertyReflection.getInt(sequence, "playMode", "getPlayMode", 0)'),
        ("PropertyReflection.setInt(sequence, playModeIdx.get())", 'PropertyReflection.setInt(sequence, "playMode", "setPlayMode", playModeIdx.get())'),
        ("PropertyReflection.readProperty(sequence)", 'PropertyReflection.readProperty(sequence, "tracks", "getTracks")'),
    ],
    "TimelineTrackPropertyAdapter.java": [
        ("PropertyReflection.getFloatArray(track)", 'PropertyReflection.getFloatArray(track, "color", "getColor")'),
        ("PropertyReflection.setFloatArray(track, colorRGBA)", 'PropertyReflection.setFloatArray(track, "color", "setColor", colorRGBA)'),
        ("PropertyReflection.readProperty(track)", 'PropertyReflection.readProperty(track, "visualOffset", "getVisualOffset")'),
    ],
    "EffectPropertyAdapter.java": [
        ("PropertyReflection.setString(effect, nameBuffer.get())", 'PropertyReflection.setString(effect, "name", "setName", nameBuffer.get())'),
        ("PropertyReflection.getFloatArray(effect)", 'PropertyReflection.getFloatArray(effect, "color", "getColor")'),
        ("PropertyReflection.setFloatArray(effect, colorRGBA)", 'PropertyReflection.setFloatArray(effect, "color", "setColor", colorRGBA)'),
    ],
    "MaterialLibraryPropertyAdapter.java": [
        ("PropertyReflection.readProperty(library)", 'PropertyReflection.readProperty(library, "materials", "getMaterials")'),
    ],
    "PhysicsEnvironmentPropertyAdapter.java": [
        ("PropertyReflection.invokeVoid(physics)", 'PropertyReflection.invokeVoid(physics, "resetToDefault")'),
    ],
    "TimelineProjectPropertyAdapter.java": [
        ("PropertyReflection.setFloat(project, fps[0])", 'PropertyReflection.setFloat(project, "fps", "setFps", fps[0])'),
        ('PropertyReflection.getInt(project, "timeUnit", "getTimeUnit")', 'PropertyReflection.getInt(project, "timeUnit", "getTimeUnit", 0)'),
        ('PropertyReflection.getInt(project, "exportFormat", "getExportFormat")', 'PropertyReflection.getInt(project, "exportFormat", "getExportFormat", 0)'),
        ('PropertyReflection.getInt(track, "keyframeCount", "getKeyframeCount")', 'PropertyReflection.getInt(track, "keyframeCount", "getKeyframeCount", 0)'),
    ],
}

HELPER_START = re.compile(r"\n\s*// =+ 反射辅助方法 =+\s*\n")


def migrate_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if not any(
        token in text
        for token in (
            "getStringProperty(",
            "getObjectProperty(",
            "getFloatProperty(",
            "getBooleanProperty(",
            "getIntProperty(",
            "callMethod(",
        )
    ):
        return False

    original = text
    for old, new in GENERIC:
        text = text.replace(old, new)

    for old, new in FILE_LITERALS.get(path.name, []):
        text = text.replace(old, new)

    if "import com.director.ui.properties.PropertyReflection;" not in text:
        text = text.replace(
            "import com.director.ui.properties.IPropertyAdapter;",
            "import com.director.ui.properties.IPropertyAdapter;\nimport com.director.ui.properties.PropertyReflection;",
        )

    m = HELPER_START.search(text)
    if m:
        rest = text[m.end():]
        end = rest.find("\n    @Override")
        if end != -1:
            text = text[: m.start()] + "\n" + rest[end + 1 :]

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
