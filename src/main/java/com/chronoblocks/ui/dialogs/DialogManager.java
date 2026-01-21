package com.chronoblocks.ui.dialogs;

import imgui.type.ImBoolean;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;

/**
 * 对话框管理器 - 集中管理所有对话框的显示状态和生命周期
 * 
 * <p>职责：
 * <ul>
 *   <li>管理对话框的显示状态</li>
 *   <li>提供对话框的打开/关闭方法</li>
 *   <li>统一对话框的生命周期管理</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>对话框状态集中管理，不分散在各个面板中</li>
 *   <li>UI 组件（如 MenuBarPanel）只负责触发命令，不管理状态</li>
 *   <li>支持 Toolbar / Shortcut 等组件复用同一套对话框</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class DialogManager {
    
    // ==================== 对话框状态 ====================
    
    private final ImBoolean showAboutDialog = new ImBoolean(false);
    private final ImBoolean showSettingsDialog = new ImBoolean(false);
    private final ImBoolean showShortcutsDialog = new ImBoolean(false);
    private boolean showNewAnimationConfirm = false;
    
    // ==================== 对话框控制方法 ====================
    
    /**
     * 显示关于对话框
     */
    public void showAboutDialog() {
        showAboutDialog.set(true);
    }
    
    /**
     * 关闭关于对话框
     */
    public void closeAboutDialog() {
        showAboutDialog.set(false);
    }
    
    /**
     * 显示设置对话框
     */
    public void showSettingsDialog() {
        showSettingsDialog.set(true);
    }
    
    /**
     * 关闭设置对话框
     */
    public void closeSettingsDialog() {
        showSettingsDialog.set(false);
    }
    
    /**
     * 显示快捷键对话框
     */
    public void showShortcutsDialog() {
        showShortcutsDialog.set(true);
    }
    
    /**
     * 关闭快捷键对话框
     */
    public void closeShortcutsDialog() {
        showShortcutsDialog.set(false);
    }
    
    /**
     * 显示新建动画确认对话框
     */
    public void showNewAnimationConfirm() {
        showNewAnimationConfirm = true;
    }
    
    /**
     * 关闭新建动画确认对话框
     */
    public void closeNewAnimationConfirm() {
        showNewAnimationConfirm = false;
    }
    
    // ==================== 对话框渲染方法 ====================
    
    /**
     * 渲染所有对话框
     */
    public void renderDialogs() {
        if (showAboutDialog.get()) {
            renderAboutDialog();
        }
        if (showSettingsDialog.get()) {
            renderSettingsDialog();
        }
        if (showShortcutsDialog.get()) {
            renderShortcutsDialog();
        }
        renderNewAnimationDialog();
    }
    
    /**
     * 关于对话框
     */
    private void renderAboutDialog() {
        ImGui.openPopup("About ChronoBlocks");
        ImGui.setNextWindowSize(400, 300);
        
        if (ImGui.beginPopupModal("About ChronoBlocks", showAboutDialog)) {
            UITheme.title(Icons.ANIMATION + " ChronoBlocks Editor");
            
            UITheme.beginGroup("Version Info");
            ImGui.text("Version: 4.5.0");
            ImGui.text("Minecraft: Fabric 1.21.x");
            ImGui.text("Java: 21");
            UITheme.endGroup();
            
            UITheme.separator();
            
            UITheme.beginGroup("Description");
            ImGui.textWrapped("A professional timeline-based animation editor for Minecraft block structures.");
            ImGui.textWrapped("Create stunning animations with intuitive keyframe editing and powerful container management.");
            UITheme.endGroup();
            
            UITheme.separator();
            
            UITheme.beginGroup("Credits");
            ImGui.text("© 2025 ChronoBlocks Team");
            ImGui.text("Built with ImGui, Fabric API");
            UITheme.endGroup();
            
            UITheme.separator();
            
            if (ImGui.button("OK", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeAboutDialog();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 设置对话框
     */
    private void renderSettingsDialog() {
        ImGui.openPopup("Settings");
        ImGui.setNextWindowSize(500, 400);
        
        if (ImGui.beginPopupModal("Settings", showSettingsDialog)) {
            UITheme.title(Icons.SETTINGS + " User Preferences");
            
            UITheme.beginGroup("General");
            ImGui.text("Auto-save interval: 5 minutes");
            ImGui.text("Default FPS: 60");
            UITheme.endGroup();
            
            UITheme.separator();
            
            UITheme.beginGroup("Theme");
            ImGui.text("Current Theme: Dark (UITheme v4.5)");
            UITheme.endGroup();
            
            UITheme.separator();
            
            if (ImGui.button("OK", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeSettingsDialog();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeSettingsDialog();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 快捷键对话框
     */
    private void renderShortcutsDialog() {
        ImGui.openPopup("Keyboard Shortcuts");
        ImGui.setNextWindowSize(600, 500);
        
        if (ImGui.beginPopupModal("Keyboard Shortcuts", showShortcutsDialog)) {
            UITheme.title(Icons.KEYBOARD + " Keyboard Shortcuts");
            
            UITheme.beginGroup("File");
            ImGui.text("Ctrl+N         - New Animation");
            ImGui.text("Ctrl+O         - Open");
            ImGui.text("Ctrl+S         - Save");
            ImGui.text("Ctrl+Shift+S   - Save As");
            ImGui.text("Ctrl+E         - Export");
            UITheme.endGroup();
            
            UITheme.separator();
            
            UITheme.beginGroup("Edit");
            ImGui.text("Ctrl+Z         - Undo");
            ImGui.text("Ctrl+Y         - Redo");
            ImGui.text("Ctrl+C/X/V     - Copy/Cut/Paste");
            ImGui.text("Del            - Delete");
            UITheme.endGroup();
            
            UITheme.separator();
            
            UITheme.beginGroup("Animation");
            ImGui.text("Space          - Play/Pause");
            ImGui.text("Ctrl+Space     - Stop");
            ImGui.text("K              - Add Keyframe");
            ImGui.text("Shift+K        - Delete Keyframe");
            ImGui.text("Home/End       - Go to Start/End");
            UITheme.endGroup();
            
            UITheme.separator();
            
            if (ImGui.button("Close", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeShortcutsDialog();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * "新建动画"确认弹窗，防止误操作清空当前工作区。
     */
    private void renderNewAnimationDialog() {
        if (!showNewAnimationConfirm) {
            return;
        }
        ImGui.openPopup("Reset Workspace");
        ImGui.setNextWindowSize(420, 190);
        ImBoolean popupOpen = new ImBoolean(true);
        if (ImGui.beginPopupModal("Reset Workspace", popupOpen, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textColored(1.0f, 0.47f, 0.31f, 1.0f, Icons.WARNING + " 警告：该操作会清空当前动画与容器！");
            ImGui.spacing();
            ImGui.textWrapped("将删除当前存档中的时间线、关键帧和容器引用，并写入全新的空白工程。请确认已备份重要内容。");
            ImGui.spacing();

            if (ImGui.button("确认重置", 150, UITheme.Spacing.BUTTON_HEIGHT)) {
                // 执行重置逻辑（通过回调或命令）
                onNewAnimationConfirm();
                closeNewAnimationConfirm();
                popupOpen.set(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeNewAnimationConfirm();
                popupOpen.set(false);
                ImGui.closeCurrentPopup();
            }
            
            // 如果用户通过其他方式关闭弹窗（如点击外部或ESC），更新状态
            if (!popupOpen.get()) {
                closeNewAnimationConfirm();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 新建动画确认回调（由外部实现）
     */
    private Runnable newAnimationConfirmCallback;
    
    /**
     * 设置新建动画确认回调
     */
    public void setNewAnimationConfirmCallback(Runnable callback) {
        this.newAnimationConfirmCallback = callback;
    }
    
    private void onNewAnimationConfirm() {
        if (newAnimationConfirmCallback != null) {
            newAnimationConfirmCallback.run();
        }
    }
}
