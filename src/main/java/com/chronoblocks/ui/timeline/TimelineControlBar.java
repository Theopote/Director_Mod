package com.chronoblocks.ui.timeline;

import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * 时间线控制栏
 * <p>
 * 职责：专门负责渲染顶部工具栏的 UI
 * - 轨道管理按钮（新建、删除）
 * - 撤销/重做按钮
 * - 播放控制
 * - 时间显示和跳转
 * - 吸附控制
 * - 缩放控制
 * 
 * @author ChronoBlocks Team
 * @version 7.4
 */
public class TimelineControlBar {

    private final TimelineModel model;
    private final TimelineCommandHistory commandHistory;
    private final Callbacks callbacks;

    // （已移除）缩放预设按钮

    /**
     * 控制栏动作回调接口
     */
    public interface Callbacks {
        void onAddTrackClicked();
        void onDeleteSelectedClicked();
        void onFitToViewClicked();
    }

    public TimelineControlBar(TimelineModel model, TimelineCommandHistory commandHistory, Callbacks callbacks) {
        this.model = model;
        this.commandHistory = commandHistory;
        this.callbacks = callbacks;
    }

    /**
     * 渲染控制栏
     */
    public void render() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);

        // ========== 轨道管理按钮 ==========
        if (ImGui.button(Icons.ADD + " 新建轨道", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            callbacks.onAddTrackClicked();
        }

        ImGui.sameLine();

        boolean hasSelection = model.getSelectedClip() != null;
        if (!hasSelection) ImGui.beginDisabled();
        if (ImGui.button(Icons.DELETE + " 删除", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            callbacks.onDeleteSelectedClicked();
        }
        if (!hasSelection) ImGui.endDisabled();

        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();

        // ========== 撤销/重做按钮 ==========
        boolean canUndo = commandHistory.canUndo();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(Icons.UNDO + " 撤销", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.undo();
        }
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        boolean canRedo = commandHistory.canRedo();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(Icons.REDO + " 重做", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.redo();
        }
        if (!canRedo) ImGui.endDisabled();

        ImGui.sameLine();
        UITheme.label(String.format("(%d/%d)", 
            commandHistory.getUndoSize(), 
            commandHistory.getRedoSize()), UITheme.DarkTheme.TEXT_DISABLED);

        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();

        // ========== 播放控制 ==========
        if (ImGui.button(Icons.SKIP_BACK, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.jumpToStart();
        }
        ImGui.sameLine();
        if (ImGui.button(model.isPlaying() ? (Icons.PAUSE + " 暂停") : (Icons.PLAY + " 播放"), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.togglePlay();
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.SKIP_FORWARD, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.jumpToEnd();
        }
        ImGui.sameLine();
        
        // 循环控制
        boolean loopEnabled = model.isLoopEnabled();
        if (ImGui.checkbox(Icons.LOOP + " 循环", loopEnabled)) {
            model.setLoopEnabled(!loopEnabled);
        }
        ImGui.sameLine();
        ImGui.text("时间:");

        // ========== 当前时间显示 ==========
        ImGui.sameLine();
        ImGui.text(String.format("%.2f", model.getCurrentTime()));

        ImGui.sameLine();
        ImGui.text("/");

        // ========== 总时长输入（可编辑）==========
        ImGui.sameLine();
        float[] durationRef = {model.getDuration()};
        ImGui.pushItemWidth(100);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.15f, 0.25f, 0.45f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.2f, 0.35f, 0.6f, 0.9f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0.25f, 0.4f, 0.7f, 1.0f);
        if (ImGui.dragFloat("##duration", durationRef, 0.5f, 1.0f, 3600.0f, "%.1fs")) {
            float newDuration = Math.max(1.0f, Math.min(3600.0f, durationRef[0]));
            model.setDuration(newDuration);
        }
        ImGui.popStyleColor(3);
        ImGui.popItemWidth();

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(Icons.EDIT + " 可编辑总时长");
            ImGui.separator();
            ImGui.text("• 左键拖动调整");
            ImGui.text("• Ctrl+点击输入数字");
            ImGui.text("• 范围: 1.0s - 3600.0s");
            ImGui.endTooltip();
        }

        // ========== 精确时间输入 ==========
        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();
        ImGui.text("跳转:");
        ImGui.sameLine();
        float[] timeRef = {model.getCurrentTime()};
        ImGui.pushItemWidth(70);
        if (ImGui.dragFloat("##currentTime", timeRef, 0.01f, 0, model.getDuration(), "%.2fs")) {
            model.setCurrentTime(timeRef[0]);
        }
        ImGui.popItemWidth();

        ImGui.sameLine();

        // ========== 吸附控制 ==========
        boolean snapEnabled = model.isSnapEnabled();
        if (ImGui.checkbox("吸附 (Ctrl切换)", snapEnabled)) {
            model.setSnapEnabled(!snapEnabled);
        }

        ImGui.sameLine();
        ImGui.text(String.format("缩放: %.0f px/s", model.getPixelsPerSecond()));

        ImGui.sameLine();
        // ========== 适应视图按钮 ==========
        if (ImGui.button("适应")) {
            callbacks.onFitToViewClicked();
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(Icons.FIT_SCREEN + " 自动缩放以查看整个时间线");
            ImGui.endTooltip();
        }

        ImGui.sameLine();

        // ========== 缩放滑块 ==========
        float[] ppsRef = {model.getPixelsPerSecond()};
        float availWidth = ImGui.getContentRegionAvailX();
        if (availWidth > 0) {
            ImGui.pushItemWidth(availWidth);
        }
        if (ImGui.sliderFloat("##zoom", ppsRef, model.getMinPps(), model.getMaxPps(), "%.0f")) {
            model.setZoom(ppsRef[0]);
        }
        if (availWidth > 0) {
            ImGui.popItemWidth();
        }

        ImGui.popStyleVar(2);
    }
}