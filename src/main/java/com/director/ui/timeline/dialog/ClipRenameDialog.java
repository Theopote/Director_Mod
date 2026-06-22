package com.director.ui.timeline.dialog;

import com.director.ui.timeline.commands.RenameClipCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.ui.theme.UITheme;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 片段重命名对话框
 */
public final class ClipRenameDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipRenameDialog.class);
    
    private static TimelineClip target;
    private static final ImString buffer = new ImString(256);
    private static boolean shouldOpen = false;
    private static TimelineModel model;
    private static TimelineCommandHistory commandHistory;

    public static void initialize(TimelineModel m, TimelineCommandHistory h) {
        model = m;
        commandHistory = h;
    }

    public static void open(TimelineClip clip) {
        if (clip == null) {
            return;
        }
        target = clip;
        buffer.set(clip.getLabel());
        shouldOpen = true;
    }

    public static void renderIfNeeded() {
        if (target == null) {
            return;
        }
        
        final String popupId = "重命名片段##ClipRenamePopup";
        if (shouldOpen) {
            ImGui.openPopup(popupId);
            shouldOpen = false;
        }
        
        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("输入新的片段名称：");
            ImGui.pushItemWidth(UITheme.Spacing.INPUT_WIDTH_LARGE);
            boolean enterPressed = ImGui.inputText("##ClipRenameInput", buffer, 
                ImGuiInputTextFlags.EnterReturnsTrue);
            ImGui.popItemWidth();
            
            if (ImGui.button("确定", 0, UITheme.Spacing.BUTTON_HEIGHT) || enterPressed) {
                applyRename();
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

    private static void applyRename() {
        if (target == null || model == null || commandHistory == null) {
            close();
            return;
        }
        
        String newName = cleanName(buffer.get());
        if (!newName.isBlank() && !newName.equals(target.getLabel())) {
            commandHistory.executeCommand(new RenameClipCommand(model, target, newName));
            LOGGER.debug("[Timeline] 重命名片段: {} -> {}", target.getLabel(), newName);
        }
        close();
    }

    private static String cleanName(String input) {
        if (input == null) {
            return "";
        }
        return input.trim();
    }

    private static void close() {
        target = null;
        buffer.set("");
        shouldOpen = false;
    }
}

