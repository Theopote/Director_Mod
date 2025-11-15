package com.chronoblocks.ui.panels;

import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.Keyframe.Easing;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 新动画系统关键帧属性面板。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class KeyframePropertiesPanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyframePropertiesPanel.class);

    private Keyframe selectedKeyframe;

    @Override
    public String getPanelName() {
        return "Keyframe Properties";
    }

    @Override
    public String getWindowName() {
        return Icons.KEYFRAME + " 关键帧属性###KeyframeProperties";
    }

    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.None;
    }

    @Override
    protected void doRender() {
        if (selectedKeyframe == null) {
            UITheme.spacingLarge();
            UITheme.label("未选择关键帧", UITheme.DarkTheme.TEXT_DISABLED);
            return;
        }

        UITheme.title(Icons.KEYFRAME + " 关键帧");

        renderTime();
        UITheme.separator();
        renderValue();
        UITheme.separator();
        renderEasing();
    }

    private void renderTime() {
        UITheme.beginGroup("时间");
        float[] time = {(float) selectedKeyframe.time()};
        if (ImGui.dragFloat("时间 (秒)", time, 0.01f, 0.0f, Float.MAX_VALUE, "%.3f")) {
            selectedKeyframe.setTime(time[0]);
            LOGGER.debug("[KeyframePanel] 更新时间: {}", time[0]);
        }
        UITheme.endGroup();
    }

    private void renderValue() {
        UITheme.beginGroup("数值");
        Object value = selectedKeyframe.value();
        if (value instanceof Vec3d vec) {
            float[] components = {(float) vec.x, (float) vec.y, (float) vec.z};
            if (ImGui.dragFloat3("向量", components, 0.01f)) {
                selectedKeyframe.setValue(new Vec3d(components[0], components[1], components[2]));
                LOGGER.debug("[KeyframePanel] 更新向量值: {}, {}, {}", components[0], components[1], components[2]);
            }
        } else if (value instanceof Number number) {
            float[] scalar = {number.floatValue()};
            if (ImGui.dragFloat("数值", scalar, 0.01f, -Float.MAX_VALUE, Float.MAX_VALUE, "%.3f")) {
                selectedKeyframe.setValue(scalar[0]);
                LOGGER.debug("[KeyframePanel] 更新数值: {}", scalar[0]);
            }
        } else {
            ImGui.text("值: " + String.valueOf(value));
        }
        UITheme.endGroup();
    }

    private void renderEasing() {
        UITheme.beginGroup("插值");
        Easing[] easings = Easing.values();
        String[] labels = new String[easings.length];
        int current = 0;
        for (int i = 0; i < easings.length; i++) {
            labels[i] = easings[i].name();
            if (easings[i] == selectedKeyframe.easing()) {
                current = i;
            }
        }
        ImInt holder = new ImInt(current);
        if (ImGui.combo("模式", holder, labels)) {
            selectedKeyframe.setEasing(easings[holder.get()]);
            LOGGER.debug("[KeyframePanel] 插值模式: {}", easings[holder.get()]);
        }
        UITheme.endGroup();
    }

    public void setSelectedKeyframe(Keyframe keyframe) {
        this.selectedKeyframe = keyframe;
        if (keyframe != null) {
            LOGGER.debug("[KeyframePanel] 选中关键帧: t={}", keyframe.time());
        }
    }

    public Keyframe getSelectedKeyframe() {
        return selectedKeyframe;
    }

    public void clearSelection() {
        selectedKeyframe = null;
    }
}


