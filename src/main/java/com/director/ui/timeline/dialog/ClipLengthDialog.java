package com.director.ui.timeline.dialog;

import com.director.ui.timeline.commands.SetClipLengthCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.theme.UITheme;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 片段时间长度设置对话框
 */
public final class ClipLengthDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipLengthDialog.class);
    
    private static TimelineClip target;
    private static final ImString buffer = new ImString(32);
    private static boolean shouldOpen = false;
    private static TimelineCommandHistory commandHistory;
    private static TimelineModel model;

    public static void initialize(TimelineCommandHistory h, TimelineModel m) {
        commandHistory = h;
        model = m;
    }

    public static void open(TimelineClip clip) {
        if (clip == null) {
            return;
        }
        target = clip;
        buffer.set(String.format("%.2f", clip.getLength()));
        shouldOpen = true;
    }

    public static void renderIfNeeded() {
        if (target == null) {
            return;
        }
        
        final String popupId = "设置时间长度##ClipLengthPopup";
        if (shouldOpen) {
            ImGui.openPopup(popupId);
            shouldOpen = false;
        }
        
        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("输入新的时间长度（秒）：");
            ImGui.pushItemWidth(UITheme.Spacing.INPUT_WIDTH_LARGE);
            boolean enterPressed = ImGui.inputText("##ClipLengthInput", buffer, 
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CharsDecimal);
            ImGui.popItemWidth();
            
            if (ImGui.button("确定", 0, UITheme.Spacing.BUTTON_HEIGHT) || enterPressed) {
                applyLength();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                close();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        } else if (!shouldOpen) {
            close();
        }
    }

    private static void applyLength() {
        if (target == null || commandHistory == null) {
            close();
            return;
        }
        
        try {
            String input = buffer.get().trim();
            if (input.isEmpty()) {
                close();
                return;
            }
            
            float newLength = Float.parseFloat(input);
            // 确保时间长度在合理范围内（最少0.05秒，最多600秒）
            newLength = Math.max(0.05f, Math.min(600.0f, newLength));
            
            if (model != null) {
                commandHistory.executeCommand(new SetClipLengthCommand(model, target, newLength));
            } else {
                LOGGER.warn("[Timeline] 设置片段时长失败：model 未初始化");
            }
            LOGGER.info("[Timeline] 设置片段 {} 的时间长度为 {}s", target.getLabel(), newLength);
        } catch (NumberFormatException e) {
            LOGGER.warn("[Timeline] 无效的时间长度输入: {}", buffer.get());
        }
        
        close();
    }

    private static void close() {
        target = null;
        buffer.set("");
        shouldOpen = false;
    }
}

