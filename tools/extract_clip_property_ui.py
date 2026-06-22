from pathlib import Path

src_path = Path("src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyAdapter.java")
text = src_path.read_text(encoding="utf-8")
lines = text.splitlines()

start_idx = None
for i, line in enumerate(lines):
    if line.strip().startswith("private float getFloatParam(Map<String, Object> params) {"):
        start_idx = i
        break
if start_idx is None:
    raise SystemExit("start not found")

end_idx = len(lines) - 1
while end_idx > start_idx and lines[end_idx].strip() != "}":
    end_idx -= 1
method_lines = lines[start_idx:end_idx]

helper_start = None
for i, line in enumerate(lines):
    if "private static String[] getEasingFunctionLabels()" in line:
        helper_start = i
        break
helper_end = helper_start
while helper_end < len(lines) and not (
    lines[helper_end].strip().startswith("public Class<TimelineClip>")
):
    helper_end += 1

helper_lines = lines[helper_start:helper_end]

field_lines = [
    "    private final ImInt addEffectSelectedIndex = new ImInt(0);",
    "    private final ImBoolean showSequenceAdvanced = new ImBoolean(false);",
    "    private static final int EASING_CURVE_SAMPLES = 100;",
    "    private float[] easingCurveCache = new float[EASING_CURVE_SAMPLES];",
    "    private EasingFunction cachedEasingFunction = null;",
    "    private static String[] EASING_FUNCTION_LABELS = null;",
    "",
]

header = """package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.easing.EasingFunction;
import com.director.core.container.AnimationMode;
import com.director.core.animation.effect.BlockEffectRegistry;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.EffectType;
import com.director.core.animation.effect.IBlockEffect;
import com.director.core.animation.effect.preset.EffectPreset;
import com.director.core.animation.effect.preset.EffectPresetRegistry;
import com.director.core.animation.sequence.ConcurrencyMode;
import com.director.core.animation.sequence.SequenceParameters;
import com.director.core.animation.sequence.TimeMappingStrategy;
import com.director.core.animation.sequence.evaluator.FillType;
import com.director.core.animation.sequence.evaluator.FlowType;
import com.director.core.animation.sequence.evaluator.FloodSeedMode;
import com.director.core.animation.sequence.evaluator.OriginType;
import com.director.ui.util.EffectClipboardManager;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.theme.Icons;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Shared ImGui rendering for container and demolition {@link TimelineClip} property panels.
 */
public class TimelineClipPropertyUi {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipPropertyUi.class);

"""

variant_enum = """package com.director.ui.properties.adapters;

/**
 * Distinguishes container (appear) vs demolition (erase) clip property UI variants.
 */
public enum TimelineClipUiVariant {
    CONTAINER,
    DEMOLITION
}
"""

transformed = []
for line in helper_lines + method_lines:
    if line.startswith("    private static "):
        line = "    static " + line[len("    private static "):]
    elif line.startswith("    private "):
        line = "    " + line[len("    private "):]
    transformed.append(line)

body = header + "\n".join(field_lines) + "\n".join(transformed) + "\n}\n"

out_ui = Path("src/main/java/com/director/ui/properties/adapters/TimelineClipPropertyUi.java")
out_ui.write_text(body, encoding="utf-8")
Path("src/main/java/com/director/ui/properties/adapters/TimelineClipUiVariant.java").write_text(
    variant_enum, encoding="utf-8"
)
print(f"Wrote {out_ui} with {len(body.splitlines())} lines")
