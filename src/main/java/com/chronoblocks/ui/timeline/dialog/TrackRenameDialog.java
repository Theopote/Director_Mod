package com.chronoblocks.ui.timeline.dialog;

import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.commands.RenameTrackCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 轨道重命名对话框
 * 支持弹窗模式和原位编辑模式
 */
public final class TrackRenameDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackRenameDialog.class);
    
    // 弹窗模式
    private static TimelineTrack popupTarget;
    private static final ImString popupBuffer = new ImString(256);
    private static boolean popupShouldOpen = false;
    
    // 原位编辑模式
    private static TimelineTrack inlineTarget;
    private static final ImString inlineBuffer = new ImString(256);
    private static boolean isInlineEditing = false;
    
    private static TimelineModel model;
    private static TimelineCommandHistory commandHistory;
    private static Runnable onDataChanged;

    public static void initialize(TimelineModel m, TimelineCommandHistory h, Runnable dataChangedCallback) {
        model = m;
        commandHistory = h;
        onDataChanged = dataChangedCallback;
    }

    /**
     * 打开弹窗模式
     */
    public static void open(TimelineTrack track) {
        if (track == null) {
            return;
        }
        popupTarget = track;
        popupBuffer.set(track.getName());
        popupShouldOpen = true;
    }

    /**
     * 开始原位编辑模式
     */
    public static void startInlineEdit(TimelineTrack track) {
        if (track == null) {
            return;
        }
        inlineTarget = track;
        inlineBuffer.set("");
        inlineBuffer.set(track.getName());
        isInlineEditing = true;
        LOGGER.debug("[Timeline] 开始原位编辑轨道: '{}'", track.getName());
    }

    /**
     * 完成原位编辑
     */
    public static void finishInlineEdit(boolean apply) {
        if (!isInlineEditing || inlineTarget == null) {
            return;
        }
        
        if (apply) {
            String newName = cleanName(inlineBuffer.get());
            if (!newName.isEmpty() && !newName.equals(inlineTarget.getName())) {
                if (model != null && commandHistory != null) {
                    commandHistory.executeCommand(new RenameTrackCommand(model, inlineTarget, newName));
                    if (onDataChanged != null) {
                        onDataChanged.run();
                    }
                    LOGGER.info("[Timeline] 轨道重命名: '{}' -> '{}'", inlineTarget.getName(), newName);
                } else {
                    // 降级：直接设置名称
                    inlineTarget.setName(newName);
                    if (onDataChanged != null) {
                        onDataChanged.run();
                    }
                }
            } else if (newName.isEmpty()) {
                LOGGER.warn("[Timeline] 重命名失败：名称为空");
            }
        }
        
        clearInlineEdit();
    }

    /**
     * 获取原位编辑的缓冲区（用于渲染）
     */
    public static ImString getInlineBuffer() {
        return inlineBuffer;
    }

    /**
     * 检查是否正在原位编辑指定轨道
     */
    public static boolean isEditing(TimelineTrack track) {
        return isInlineEditing && inlineTarget == track;
    }

    /**
     * 检查是否正在原位编辑任何轨道
     */
    public static boolean isEditingAny() {
        return isInlineEditing;
    }

    /**
     * 获取正在编辑的轨道
     */
    public static TimelineTrack getEditingTrack() {
        return inlineTarget;
    }

    /**
     * 渲染弹窗（如果需要）
     */
    public static void renderPopupIfNeeded() {
        if (popupTarget == null) {
            return;
        }
        
        final String popupId = "重命名轨道##TrackRenamePopup";
        if (popupShouldOpen) {
            ImGui.openPopup(popupId);
            popupShouldOpen = false;
        }
        
        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("输入新的轨道名称：");
            ImGui.pushItemWidth(UITheme.Spacing.INPUT_WIDTH_LARGE);
            boolean enterPressed = ImGui.inputText("##TrackRenameInput", popupBuffer, 
                ImGuiInputTextFlags.EnterReturnsTrue);
            ImGui.popItemWidth();
            
            if (ImGui.button("确定", 0, UITheme.Spacing.BUTTON_HEIGHT) || enterPressed) {
                applyRename();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closePopup();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        } else if (!popupShouldOpen) {
            closePopup();
        }
    }

    private static void applyRename() {
        if (popupTarget == null || model == null || commandHistory == null) {
            closePopup();
            return;
        }
        
        String newName = cleanName(popupBuffer.get());
        if (!newName.isBlank() && !newName.equals(popupTarget.getName())) {
            commandHistory.executeCommand(new RenameTrackCommand(model, popupTarget, newName));
            if (onDataChanged != null) {
                onDataChanged.run();
            }
            LOGGER.debug("[Timeline] 重命名轨道: {} -> {}", popupTarget.getName(), newName);
        }
        closePopup();
    }

    private static void closePopup() {
        popupTarget = null;
        popupBuffer.set("");
        popupShouldOpen = false;
    }

    private static void clearInlineEdit() {
        inlineTarget = null;
        inlineBuffer.set("");
        isInlineEditing = false;
    }

    private static String cleanName(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // 在第一个空字符处截断（ImString 缓冲区可能包含空字符）
        int nullIndex = input.indexOf('\0');
        if (nullIndex >= 0) {
            input = input.substring(0, nullIndex);
        }
        
        // 去除所有不可打印字符（ASCII < 32 或 >= 127）
        StringBuilder cleaned = new StringBuilder();
        for (char c : input.toCharArray()) {
            // 保留可打印字符（包括中文等 Unicode 字符）
            if (c >= 32 && c != 127) {
                cleaned.append(c);
            }
        }
        
        // 去除首尾空格
        return cleaned.toString().trim();
    }
}

