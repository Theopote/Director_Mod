package com.chronoblocks.ui.panels;

import com.chronoblocks.core.animation.container.Keyframe;
import com.chronoblocks.core.animation.container.Keyframe.Easing;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于新动画系统的批量关键帧编辑器。
 */
@SuppressWarnings({"rawtypes"})
public class BatchKeyframeEditor extends AbstractPanel {

    private final List<Keyframe> selectedKeyframes = new ArrayList<>();
    private final float[] scaleValue = {1.0f};
    private final float[] offsetValue = {0.0f};
    private int selectedEasingIndex = 0;

    @Override
    public String getPanelName() {
        return "Batch Editor";
    }

    @Override
    public String getWindowName() {
        return "📦 批量编辑器###BatchKeyframeEditor";
    }

    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.None;
    }

    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return false;
    }

    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }

    @Override
    protected void doRender() {
        if (selectedKeyframes.isEmpty()) {
            UITheme.spacingLarge();
            UITheme.label("未选择关键帧", UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.spacingSmall();
            ImGui.textWrapped("在时间线上选择多个关键帧以启用批量编辑功能。");
            return;
        }

        UITheme.title("📦 批量关键帧编辑");

        renderSelectionInfo();
        UITheme.separator();
        renderEasingControls();
        UITheme.separator();
        renderTimeControls();
        UITheme.separator();
        renderAlignmentControls();
    }

    private void renderSelectionInfo() {
        UITheme.beginGroup("选择信息");
        ImGui.text(String.format("已选中关键帧: %d", selectedKeyframes.size()));
        if (selectedKeyframes.size() > 1) {
            double minTime = selectedKeyframes.stream()
                .mapToDouble(Keyframe::time)
                .min()
                .orElse(0.0);
            double maxTime = selectedKeyframes.stream()
                .mapToDouble(Keyframe::time)
                .max()
                .orElse(0.0);
            ImGui.text(String.format("时间范围: %.3fs → %.3fs (%.3fs)",
                minTime, maxTime, Math.max(0.0, maxTime - minTime)));
        }
        UITheme.endGroup();
    }

    private void renderEasingControls() {
        UITheme.beginGroup("插值模式");
        Easing[] easings = Easing.values();
        String[] labels = new String[easings.length];
        for (int i = 0; i < easings.length; i++) {
            labels[i] = easings[i].name();
        }
        ImInt indexHolder = new ImInt(selectedEasingIndex);
        if (ImGui.combo("选择插值", indexHolder, labels)) {
            selectedEasingIndex = indexHolder.get();
        }
        ImGui.sameLine();
        if (ImGui.button("应用", 80, UITheme.Spacing.BUTTON_HEIGHT)) {
            applyEasing(easings[selectedEasingIndex]);
        }
        UITheme.endGroup();
    }

    private void renderTimeControls() {
        UITheme.beginGroup("时间操作");
        ImGui.setNextItemWidth(140);
        ImGui.dragFloat("时间缩放", scaleValue, 0.01f, 0.1f, 10.0f, "%.2f");
        ImGui.sameLine();
        if (ImGui.button("应用缩放", 90, UITheme.Spacing.BUTTON_HEIGHT)) {
            scaleTime(scaleValue[0]);
        }

        ImGui.setNextItemWidth(140);
        ImGui.dragFloat("时间偏移", offsetValue, 0.01f, -20.0f, 20.0f, "%.2f");
        ImGui.sameLine();
        if (ImGui.button("应用偏移", 90, UITheme.Spacing.BUTTON_HEIGHT)) {
            offsetTime(offsetValue[0]);
        }
        UITheme.endGroup();
    }

    private void renderAlignmentControls() {
        UITheme.beginGroup("对齐与分布");
        if (ImGui.button("对齐到最早##alignFirst", 130, UITheme.Spacing.BUTTON_HEIGHT)) {
            alignToExtreme(true);
        }
        ImGui.sameLine();
        if (ImGui.button("对齐到最晚##alignLast", 130, UITheme.Spacing.BUTTON_HEIGHT)) {
            alignToExtreme(false);
        }

        if (selectedKeyframes.size() >= 3) {
            if (ImGui.button(Icons.STATS + " 均匀分布", 130, UITheme.Spacing.BUTTON_HEIGHT)) {
                distributeEvenly();
            }
        }
        UITheme.endGroup();
    }

    private void applyEasing(Easing easing) {
        for (Keyframe keyframe : selectedKeyframes) {
            keyframe.setEasing(easing);
        }
    }

    private void scaleTime(float scale) {
        if (scale <= 0f || selectedKeyframes.isEmpty()) {
            return;
        }
        double pivot = selectedKeyframes.get(0).time();
        for (Keyframe keyframe : selectedKeyframes) {
            double offset = keyframe.time() - pivot;
            keyframe.setTime(pivot + offset * scale);
        }
    }

    private void offsetTime(float offset) {
        for (Keyframe keyframe : selectedKeyframes) {
            keyframe.setTime(Math.max(0.0, keyframe.time() + offset));
        }
    }

    private void alignToExtreme(boolean earliest) {
        if (selectedKeyframes.size() < 2) {
            return;
        }
        double target = earliest
            ? selectedKeyframes.stream().mapToDouble(Keyframe::time).min().orElse(0.0)
            : selectedKeyframes.stream().mapToDouble(Keyframe::time).max().orElse(0.0);
        for (Keyframe keyframe : selectedKeyframes) {
            keyframe.setTime(target);
        }
    }

    private void distributeEvenly() {
        selectedKeyframes.sort(Comparator.comparingDouble(Keyframe::time));
        double start = selectedKeyframes.get(0).time();
        double end = selectedKeyframes.get(selectedKeyframes.size() - 1).time();
        if (end <= start) {
            return;
        }
        double interval = (end - start) / (selectedKeyframes.size() - 1);
        for (int i = 1; i < selectedKeyframes.size() - 1; i++) {
            selectedKeyframes.get(i).setTime(start + interval * i);
        }
    }

    public void setSelectedKeyframes(List<Keyframe> keyframes) {
        selectedKeyframes.clear();
        if (keyframes != null) {
            selectedKeyframes.addAll(keyframes);
        }
    }

    public List<Keyframe> getSelectedKeyframes() {
        return new ArrayList<>(selectedKeyframes);
    }

    public void clearSelection() {
        selectedKeyframes.clear();
    }
}


