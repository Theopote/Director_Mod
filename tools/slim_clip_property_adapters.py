"""Slim TimelineClip property adapters to delegate shared UI to TimelineClipPropertyUi."""
from pathlib import Path
import re

METHODS_TO_REMOVE = [
    "renderBasicProperties",
    "getFloatParam",
    "getBooleanParam",
    "getStringParam",
    "renderSequenceBehavior",
    "renderSequenceTimingSection",
    "renderSequenceFlowSection",
    "renderSequenceOriginSection",
    "renderSequenceFillSection",
    "getStrings",
    "renderSequenceWeightAndModifiers",
    "renderEffectsSection",
    "getEffectInstance",
    "hasEffectType",
    "ensureEffect",
    "removeEffects",
    "renderSingleEffectTimingUI",
    "renderEasingCurvePreview",
    "drawHelpBox",
    "colorIntToFloat",
    "colorFloatToInt",
    "renderEffectParams",
    "renderAxisButton",
    "isAxisSelected",
    "renderAnimationParamsDirectly",
    "getEasingFunctionLabels",
    "buildEasingLabels",
    "buildEnumLabels",
    "getAllEnumDisplayNames",
]

FIELDS_TO_REMOVE = [
    "addEffectSelectedIndex",
    "showSequenceAdvanced",
    "EASING_CURVE_SAMPLES",
    "easingCurveCache",
    "cachedEasingFunction",
    "EASING_FUNCTION_LABELS",
]


def find_method_range(lines, method_name):
    pattern = re.compile(
        rf"^\s+(?:public|private|protected)(?: static)? .*\b{re.escape(method_name)}\s*\("
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
            return start, i
    return None


def remove_methods(lines):
    removed = set()
    while True:
        found = None
        for name in METHODS_TO_REMOVE:
            if name in removed:
                continue
            rng = find_method_range(lines, name)
            if rng:
                found = (name, rng)
                break
        if not found:
            break
        name, (start, end) = found
        lines = lines[:start] + lines[end + 1 :]
        removed.add(name)
    return lines


def remove_fields(lines):
    out = []
    for line in lines:
        if any(f in line for f in FIELDS_TO_REMOVE):
            continue
        out.append(line)
    return out


def patch_adapter(path: Path, variant: str):
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    if "TimelineClipPropertyUi propertyUi" not in text:
        insert_at = None
        for i, line in enumerate(lines):
            if "private InteractionCallbacks callbacks" in line or "private final TimelineRenderer renderer" in line:
                insert_at = i
                break
        if insert_at is None:
            for i, line in enumerate(lines):
                if line.strip().startswith("private final TimelineModel"):
                    insert_at = i + 1
                    break
        if insert_at is None:
            for i, line in enumerate(lines):
                if "private static final Logger LOGGER" in line:
                    insert_at = i + 1
                    break
        lines.insert(insert_at, "    private final TimelineClipPropertyUi propertyUi = new TimelineClipPropertyUi();")
        lines.insert(insert_at, "")

    lines = remove_fields(lines)
    lines = remove_methods(lines)

    text = "\n".join(lines)
    text = text.replace(
        "modified |= renderBasicProperties(clip);",
        f"modified |= propertyUi.renderBasicProperties(clip, TimelineClipUiVariant.{variant});",
    )
    text = text.replace(
        "sequenceModified = renderSequenceBehavior(clip);",
        f"sequenceModified = propertyUi.renderSequenceBehavior(clip, TimelineClipUiVariant.{variant});",
    )
    text = text.replace(
        "modified |= renderEffectsSection(clip);",
        "modified |= propertyUi.renderEffectsSection(clip);",
    )
    text = text.replace("drawHelpBox(", "propertyUi.drawHelpBox(")
    text = text.replace(
        "renderAnimationParamsDirectly(", "propertyUi.renderAnimationParamsDirectly("
    )
    text = text.replace("renderEasingCurvePreview(", "propertyUi.renderEasingCurvePreview(")
    text = text.replace("getEasingFunctionLabels()", "TimelineClipPropertyUi.getEasingFunctionLabels()")
    text = text.replace("getBooleanParam(params)", "propertyUi.getBooleanParam(params)")
    text = text.replace("getFloatParam(params)", "propertyUi.getFloatParam(params)")

    path.write_text(text + "\n", encoding="utf-8")
    print(f"Patched {path} -> {len(text.splitlines())} lines")


def add_render_basic_properties():
    ui_path = Path("src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyUi.java")
    text = ui_path.read_text(encoding="utf-8")
    if "renderBasicProperties" in text:
        return

    method = '''
    boolean renderBasicProperties(TimelineClip clip, TimelineClipUiVariant variant) {
        boolean modified = false;
        ClipAnimationConfig config = clip.getAnimationConfig();

        float contentWidth = ImGui.getContentRegionAvailX();
        float labelWidth = 80.0f;
        float inputWidth = (contentWidth - labelWidth * 2 - ImGui.getStyle().getItemSpacingX() * 2) / 2;

        ImGui.alignTextToFramePadding();
        ImGui.text("起始时间");
        ImGui.sameLine();
        ImGui.pushItemWidth(inputWidth);
        float[] startVal = { clip.getStartTime() };
        if (ImGui.dragFloat("##StartTime", startVal, 0.05f, 0f, 10000f, "%.2fs")) {
            clip.setStartTime(Math.max(0f, startVal[0]));
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段开始时间");
        }

        ImGui.sameLine();

        ImGui.alignTextToFramePadding();
        ImGui.text("持续时长");
        ImGui.sameLine();
        ImGui.pushItemWidth(inputWidth);
        float[] lengthVal = { clip.getLength() };
        if (ImGui.dragFloat("##Length", lengthVal, 0.05f, 0.05f, 10000f, "%.2fs")) {
            clip.setLength(lengthVal[0]);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            String lengthTooltip = variant == TimelineClipUiVariant.DEMOLITION
                    ? "方块从完全显示到被清除的持续时长"
                    : "方块从隐藏到完全出现的持续时长";
            ImGui.setTooltip(lengthTooltip);
        }

        int clipColor = clip.getColor();
        float[] colorRGBA = colorIntToFloat(clipColor);
        if (ImGui.colorEdit4("片段颜色", colorRGBA,
                imgui.flag.ImGuiColorEditFlags.NoAlpha |
                        imgui.flag.ImGuiColorEditFlags.DisplayRGB |
                        imgui.flag.ImGuiColorEditFlags.InputRGB)) {
            int newColor = colorFloatToInt(colorRGBA);
            clip.setColor(newColor);
            modified = true;
        }

        ImGui.separator();

        ImBoolean autoFade = new ImBoolean(clip.isAutoFadePreviousClips());
        if (ImGui.checkbox("自动淡化之前片段", autoFade)) {
            clip.setAutoFadePreviousClips(autoFade.get());
            modified = true;
        }

        if (clip.isAutoFadePreviousClips()) {
            float[] opacity = { clip.getAutoFadeOpacity() };
            if (ImGui.sliderFloat("淡化透明度", opacity, 0.05f, 0.95f, "%.2f")) {
                clip.setAutoFadeOpacity(opacity[0]);
                modified = true;
            }
        }

        if (config != null) {
            ImBoolean occlusionTransparency = new ImBoolean(config.isOcclusionTransparencyEnabled());
            if (ImGui.checkbox("遮挡时透明显示", occlusionTransparency)) {
                config.setOcclusionTransparencyEnabled(occlusionTransparency.get());
                modified = true;
            }
        }

        ImGui.separator();

        if (ImGui.collapsingHeader("动画后淡化 (Post-Animation Fade)", imgui.flag.ImGuiTreeNodeFlags.None)) {
            ImGui.indent();
            ImGui.textDisabled("片段动画结束后，可以设置一个淡化阶段，让方块逐渐变透明。");
            UITheme.spacingSmall();

            float[] fadeStart = { clip.getPostAnimationFadeStart() };
            if (ImGui.dragFloat("淡化开始时间 (秒)", fadeStart, 0.1f, 0.0f, 10.0f, "%.1fs")) {
                clip.setPostAnimationFadeStart(fadeStart[0]);
                modified = true;
            }

            float[] fadeEnd = { clip.getPostAnimationFadeEnd() };
            if (ImGui.dragFloat("淡化结束时间 (秒)", fadeEnd, 0.1f, fadeStart[0], 30.0f, "%.1fs")) {
                clip.setPostAnimationFadeEnd(fadeEnd[0]);
                modified = true;
            }

            if (fadeEnd[0] > fadeStart[0]) {
                float fadeDuration = fadeEnd[0] - fadeStart[0];
                ImGui.textDisabled(String.format("淡化持续时间: %.1f 秒 (从 %.1f 到 %.1f 秒)",
                        fadeDuration, fadeStart[0], fadeEnd[0]));
            } else if (fadeEnd[0] == fadeStart[0] && fadeStart[0] > 0.0f) {
                ImGui.textColored(0.8f, 0.6f, 0.2f, 1.0f, "淡化开始和结束时间相同，淡化效果将立即完成");
            } else {
                ImGui.textDisabled("淡化功能已禁用（开始和结束时间均为 0）");
            }

            ImGui.unindent();
        }

        return modified;
    }

'''
    anchor = "    boolean applySequencePreset"
    text = text.replace(anchor, method + anchor)
    ui_path.write_text(text, encoding="utf-8")
    print("Added renderBasicProperties to TimelineClipPropertyUi")


if __name__ == "__main__":
    add_render_basic_properties()
    patch_adapter(
        Path("src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyAdapter.java"),
        "CONTAINER",
    )
    patch_adapter(
        Path("src/main/java/com/director/ui/properties/adapters/EraseTimelineClipPropertyAdapter.java"),
        "DEMOLITION",
    )
