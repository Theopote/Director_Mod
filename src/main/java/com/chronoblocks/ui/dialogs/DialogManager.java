package com.chronoblocks.ui.dialogs;

import imgui.type.ImBoolean;
import imgui.type.ImString;
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
    private final ImBoolean showTimelineSettingsDialog = new ImBoolean(false);
    private final ImBoolean showShortcutsDialog = new ImBoolean(false);
    private final ImBoolean showSaveLayoutDialog = new ImBoolean(false);
    private final ImBoolean showLoadLayoutDialog = new ImBoolean(false);
    private boolean showNewAnimationConfirm = false;
    
    // 保存布局对话框输入缓冲区
    private final ImString saveLayoutNameBuffer = new ImString("", 256);
    
    // 布局访问器（用于保存/加载布局预设）
    public interface LayoutAccessor {
        boolean saveLayoutPreset(String presetName);
        java.util.List<String> listLayoutPresets();
        boolean loadLayoutPreset(String presetName);
        boolean deleteLayoutPreset(String presetName);
    }
    
    private LayoutAccessor layoutAccessor;
    
    /**
     * 设置布局访问器
     */
    public void setLayoutAccessor(LayoutAccessor accessor) {
        this.layoutAccessor = accessor;
    }
    
    // ==================== 时间线设置回调 ====================
    
    /**
     * 时间线设置访问接口（用于读取和修改设置）
     */
    public interface TimelineSettingsAccessor {
        com.chronoblocks.ui.timeline.snapping.TimelineSnapSettings getSnapSettings();
        
        /**
         * 获取项目帧率（FPS）
         * @return 帧率值，如果不可用则返回默认值 30.0f
         */
        default float getFrameRate() {
            return 30.0f;
        }
        
        /**
         * 设置项目帧率（FPS）
         * @param fps 新的帧率值（1-240）
         * @return 是否设置成功
         */
        default boolean setFrameRate(float fps) {
            return false;
        }
        
        /**
         * 获取时间单位
         * @return 时间单位索引（0=秒, 1=帧, 2=Tick）
         */
        default int getTimeUnit() {
            return 0;
        }
        
        /**
         * 设置时间单位
         * @param unit 时间单位索引（0=秒, 1=帧, 2=Tick）
         * @return 是否设置成功
         */
        default boolean setTimeUnit(int unit) {
            return false;
        }
    }
    
    private TimelineSettingsAccessor timelineSettingsAccessor;
    
    /**
     * 设置时间线设置访问器
     */
    public void setTimelineSettingsAccessor(TimelineSettingsAccessor accessor) {
        this.timelineSettingsAccessor = accessor;
    }
    
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
     * 显示时间线设置对话框
     */
    public void showTimelineSettingsDialog() {
        showTimelineSettingsDialog.set(true);
    }
    
    /**
     * 关闭时间线设置对话框
     */
    public void closeTimelineSettingsDialog() {
        showTimelineSettingsDialog.set(false);
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
    
    /**
     * 显示保存布局对话框
     */
    public void showSaveLayoutDialog() {
        // 清空输入缓冲区
        saveLayoutNameBuffer.set("");
        showSaveLayoutDialog.set(true);
    }
    
    /**
     * 关闭保存布局对话框
     */
    public void closeSaveLayoutDialog() {
        showSaveLayoutDialog.set(false);
    }
    
    /**
     * 显示载入布局对话框
     */
    public void showLoadLayoutDialog() {
        showLoadLayoutDialog.set(true);
    }
    
    /**
     * 关闭载入布局对话框
     */
    public void closeLoadLayoutDialog() {
        showLoadLayoutDialog.set(false);
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
        if (showTimelineSettingsDialog.get()) {
            renderTimelineSettingsDialog();
        }
        if (showShortcutsDialog.get()) {
            renderShortcutsDialog();
        }
        if (showSaveLayoutDialog.get()) {
            renderSaveLayoutDialog();
        }
        if (showLoadLayoutDialog.get()) {
            renderLoadLayoutDialog();
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
     * 时间线设置对话框
     */
    private void renderTimelineSettingsDialog() {
        ImGui.openPopup("Timeline Settings");
        ImGui.setNextWindowSize(500, 550);
        
        if (ImGui.beginPopupModal("Timeline Settings", showTimelineSettingsDialog)) {
            UITheme.title(Icons.TIMELINE + " Timeline Settings");
            
            if (timelineSettingsAccessor == null) {
                ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, "错误：无法访问时间线设置");
                if (ImGui.button("关闭", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeTimelineSettingsDialog();
                }
                ImGui.endPopup();
                return;
            }
            
            com.chronoblocks.ui.timeline.snapping.TimelineSnapSettings snapSettings = 
                    timelineSettingsAccessor.getSnapSettings();
            
            if (snapSettings == null) {
                ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, "错误：时间线设置不可用");
                if (ImGui.button("关闭", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeTimelineSettingsDialog();
                }
                ImGui.endPopup();
                return;
            }
            
            // ========== 项目设置 ==========
            UITheme.beginGroup("项目设置");
            
            float frameRate = timelineSettingsAccessor.getFrameRate();
            float[] frameRateRef = {frameRate};
            boolean frameRateChanged = ImGui.dragFloat("帧率 (FPS)", frameRateRef, 1.0f, 1.0f, 240.0f, "%.0f");
            if (frameRateChanged) {
                boolean success = timelineSettingsAccessor.setFrameRate(frameRateRef[0]);
                if (!success) {
                    // 如果设置失败，恢复原值
                    frameRateRef[0] = frameRate;
                    // 显示提示（可选）
                    ImGui.sameLine();
                    ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, Icons.WARNING);
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("无法修改帧率：项目对象不可用");
                    }
                }
            }
            if (ImGui.isItemHovered() && !frameRateChanged) {
                ImGui.setTooltip("动画播放的每秒帧数（常用: 24, 30, 60）\n影响时间线帧数标签的显示");
            }
            
            int timeUnit = timelineSettingsAccessor.getTimeUnit();
            String[] timeUnitOptions = {"秒 (Seconds)", "帧 (Frames)", "Tick (MC Ticks)"};
            imgui.type.ImInt timeUnitRef = new imgui.type.ImInt(timeUnit);
            if (ImGui.combo("时间单位", timeUnitRef, timeUnitOptions, timeUnitOptions.length)) {
                timelineSettingsAccessor.setTimeUnit(timeUnitRef.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("时间轴显示的单位");
            }
            
            UITheme.endGroup();
            
            UITheme.separator();
            
            // ========== 吸附系统总开关 ==========
            UITheme.beginGroup("吸附系统");
            boolean enabled = snapSettings.isEnabled();
            ImBoolean enabledRef = new ImBoolean(enabled);
            if (ImGui.checkbox("启用吸附", enabledRef)) {
                snapSettings.setEnabled(enabledRef.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("总开关：关闭后所有吸附功能都将禁用");
            }
            UITheme.endGroup();
            
            if (enabled) {
                UITheme.separator();
                
                // ========== 网格设置 ==========
                UITheme.beginGroup("网格吸附");
                boolean snapToGrid = snapSettings.isSnapToGrid();
                ImBoolean snapToGridRef = new ImBoolean(snapToGrid);
                if (ImGui.checkbox("吸附到网格", snapToGridRef)) {
                    snapSettings.setSnapToGrid(snapToGridRef.get());
                }
                
                float gridInterval = snapSettings.getGridInterval();
                float[] gridIntervalRef = {gridInterval};
                if (ImGui.dragFloat("网格间隔 (秒)", gridIntervalRef, 0.01f, 0.001f, 10.0f, "%.3f")) {
                    snapSettings.setGridInterval(gridIntervalRef[0]);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("网格间隔：时间线网格的最小单位（秒）\n常用值：0.1s, 0.25s, 0.5s, 1.0s");
                }
                UITheme.endGroup();
                
                UITheme.separator();
                
                // ========== 其他吸附选项 ==========
                UITheme.beginGroup("其他吸附选项");
                
                boolean snapToClipEdges = snapSettings.isSnapToClipEdges();
                ImBoolean snapToClipEdgesRef = new ImBoolean(snapToClipEdges);
                if (ImGui.checkbox("吸附到片段边缘", snapToClipEdgesRef)) {
                    snapSettings.setSnapToClipEdges(snapToClipEdgesRef.get());
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("拖动片段时自动吸附到其他片段的开始/结束位置");
                }
                
                boolean snapToKeyframes = snapSettings.isSnapToKeyframes();
                ImBoolean snapToKeyframesRef = new ImBoolean(snapToKeyframes);
                if (ImGui.checkbox("吸附到关键帧", snapToKeyframesRef)) {
                    snapSettings.setSnapToKeyframes(snapToKeyframesRef.get());
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("拖动时自动吸附到关键帧位置");
                }
                
                UITheme.endGroup();
                
                UITheme.separator();
                
                // ========== 吸附阈值 ==========
                UITheme.beginGroup("吸附阈值");
                float pixelThreshold = snapSettings.getPixelThreshold();
                float[] pixelThresholdRef = {pixelThreshold};
                if (ImGui.dragFloat("像素阈值 (px)", pixelThresholdRef, 1.0f, 1.0f, 50.0f, "%.0f")) {
                    snapSettings.setPixelThreshold(pixelThresholdRef[0]);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("吸附触发距离（像素）\n鼠标距离吸附目标小于此值时才会触发吸附");
                }
                UITheme.endGroup();
            }
            
            UITheme.separator();
            
            if (ImGui.button("确定", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeTimelineSettingsDialog();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeTimelineSettingsDialog();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 快捷键对话框
     */
    private void renderShortcutsDialog() {
        ImGui.openPopup("Keyboard Shortcuts");
        ImGui.setNextWindowSize(700, 600);
        
        if (ImGui.beginPopupModal("Keyboard Shortcuts", showShortcutsDialog)) {
            UITheme.title(Icons.KEYBOARD + " Keyboard Shortcuts");
            
            // 使用滚动区域以支持更多快捷键
            if (ImGui.beginChild("##ShortcutsScroll", 0, -ImGui.getFrameHeightWithSpacing() - 10)) {
                // 从 ShortcutBindings 获取所有快捷键（按分类）
                var shortcutsByCategory = com.chronoblocks.ui.input.ShortcutBindings.getAllShortcutsByCategory();
                
                boolean firstCategory = true;
                for (var entry : shortcutsByCategory.entrySet()) {
                    if (!firstCategory) {
                        UITheme.separator();
                    }
                    firstCategory = false;
                    
                    UITheme.beginGroup(entry.getKey());
                    for (String shortcut : entry.getValue()) {
                        ImGui.text(shortcut);
                    }
                    UITheme.endGroup();
                }
                
                ImGui.endChild();
            }
            
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
    
    /**
     * 保存布局对话框
     */
    private void renderSaveLayoutDialog() {
        ImGui.openPopup("Save Layout");
        ImGui.setNextWindowSize(450, 200);
        
        if (ImGui.beginPopupModal("Save Layout", showSaveLayoutDialog)) {
            UITheme.title(Icons.SAVE + " Save Layout Preset");
            
            ImGui.spacing();
            ImGui.text("Enter a name for this layout preset:");
            ImGui.spacing();
            
            // 输入框
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##LayoutName", saveLayoutNameBuffer);
            
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            // 按钮
            ImGui.beginGroup();
            float buttonWidth = (ImGui.getContentRegionAvailX() - 10) / 2.0f; // 10px gap
            
            String layoutName = saveLayoutNameBuffer.get().trim();
            boolean canSave = !layoutName.isEmpty() && layoutAccessor != null;
            
            if (ImGui.button("Save", buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (canSave && layoutAccessor.saveLayoutPreset(layoutName)) {
                    closeSaveLayoutDialog();
                }
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel", buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeSaveLayoutDialog();
            }
            
            ImGui.endGroup();
            
            // 如果输入为空，显示提示
            if (layoutName.isEmpty()) {
                ImGui.spacing();
                ImGui.textColored(0.8f, 0.6f, 0.2f, 1.0f, Icons.WARNING + " Please enter a layout name");
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 载入布局对话框
     */
    private void renderLoadLayoutDialog() {
        ImGui.openPopup("Load Layout");
        ImGui.setNextWindowSize(500, 400);
        
        if (ImGui.beginPopupModal("Load Layout", showLoadLayoutDialog)) {
            UITheme.title(Icons.FOLDER + " Load Layout Preset");
            
            ImGui.spacing();
            
            if (layoutAccessor == null) {
                ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, Icons.ERROR + " Layout accessor not available");
                ImGui.spacing();
                if (ImGui.button("Close", -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeLoadLayoutDialog();
                }
                ImGui.endPopup();
                return;
            }
            
            // 获取预设列表
            java.util.List<String> presets = layoutAccessor.listLayoutPresets();
            
            if (presets.isEmpty()) {
                ImGui.textColored(0.8f, 0.6f, 0.2f, 1.0f, Icons.WARNING + " No saved layouts found");
                ImGui.spacing();
                ImGui.text("Save a layout first to see it here.");
                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();
                if (ImGui.button("Close", -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeLoadLayoutDialog();
                }
            } else {
                ImGui.text("Select a layout preset to load:");
                ImGui.spacing();
                
                // 列表区域（带滚动）
                float listHeight = ImGui.getContentRegionAvailY() - UITheme.Spacing.BUTTON_HEIGHT - 20; // 20px for spacing
                if (ImGui.beginChild("##LayoutList", 0, listHeight, true)) {
                    for (String preset : presets) {
                        if (ImGui.selectable(preset, false)) {
                            // 用户选择了这个预设
                            if (layoutAccessor.loadLayoutPreset(preset)) {
                                closeLoadLayoutDialog();
                                // 重新加载 ImGui INI 文件
                                com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                            }
                        }
                        
                        // 右键菜单：删除
                        if (ImGui.beginPopupContextItem("##PresetContext_" + preset)) {
                            if (ImGui.menuItem(Icons.DELETE + " Delete")) {
                                layoutAccessor.deleteLayoutPreset(preset);
                            }
                            ImGui.endPopup();
                        }
                    }
                }
                ImGui.endChild();
                
                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();
                
                // 按钮
                if (ImGui.button("Close", -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeLoadLayoutDialog();
                }
            }
            
            ImGui.endPopup();
        }
    }
}
