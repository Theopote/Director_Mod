package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.ChronoBlocksUIManager;
import com.chronoblocks.ui.aware.IPanelRegistryAware;
import com.chronoblocks.ui.aware.IUIManagerAware;
import com.chronoblocks.ui.di.IPanelRegistry;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;

/**
 * ChronoBlocks MenuBar v4.5 - 企业级菜单栏 + 状态栏系统
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>顶部菜单栏 - File / Edit / Animation / Container / Window / Tools / Help</li>
 *   <li>底部状态栏 - FPS / 播放状态 / 时间轴 / 面板数量</li>
 *   <li>动态状态同步 - 与 PlaybackController、Timeline 服务实时联动</li>
 *   <li>面板管理集成 - 与 PanelRegistry 深度集成</li>
 *   <li>专业对话框 - About / Settings / Shortcuts</li>
 * </ul>
 * 
 * <h3>新增菜单</h3>
 * <ul>
 *   <li><strong>Container</strong> - 容器管理专属菜单（新增）</li>
 *   <li><strong>Animation</strong> - 增强的动画控制</li>
 *   <li><strong>Window</strong> - 面板布局管理</li>
 * </ul>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class MenuBarPanel extends AbstractPanel implements IPanelRegistryAware, IUIManagerAware {

    // ==================== 对话框状态 ====================
    
    private final ImBoolean showAboutDialog = new ImBoolean(false);
    private final ImBoolean showSettingsDialog = new ImBoolean(false);
    private final ImBoolean showShortcutsDialog = new ImBoolean(false);
    private boolean showNewAnimationConfirm = false;
    
    // ==================== 依赖注入 ====================
    
    private IPanelRegistry panelRegistry;
    private ChronoBlocksUIManager uiManager;
    
    // ==================== FPS 计算 ====================
    
    private long lastFrameTime = System.nanoTime();
    private float fps = 60.0f; // 初始值
    private float fpsSmoothing = 0.9f; // 平滑系数
    
    // ==================== 面板配置 ====================

    @Override
    public String getPanelName() {
        return "MenuBar";
    }
    
    @Override
    public String getWindowName() {
        return "MainMenuBar###MenuBarPanel";
    }

    @Override
    protected boolean shouldUseStandardWindow() {
        return false; // 使用 MainMenuBar
    }
    
    @Override
    public int getPriority() {
        return 10; // 最高优先级
    }

    // ==================== 主渲染方法 ====================

    @Override
    protected void doRender() {
        renderMainMenuBar();
        renderStatusBar();
        renderDialogs();
    }

    // ==================== 顶部菜单栏 ====================

    /**
     * 渲染主菜单栏
     */
    private void renderMainMenuBar() {
        if (ImGui.beginMainMenuBar()) {
                try {
                    renderFileMenu();
                    renderEditMenu();
                    renderAnimationMenu();
                renderContainerMenu();  // 🆕 容器管理菜单
                    renderWindowMenu();
                    renderToolsMenu();
                    renderHelpMenu();
                } catch (Exception e) {
                logger.error("[MenuBar] 菜单渲染错误: {}", e.getMessage(), e);
        } finally {
                    ImGui.endMainMenuBar();
            }
        }
    }

    // ==================== File 菜单 ====================
    
    /**
     * 文件菜单 - 工程与文件管理
     */
    private void renderFileMenu() {
        if (ImGui.beginMenu(Icons.FOLDER + " File")) {
                    if (ImGui.menuItem("New Animation", "Ctrl+N")) {
                        handleNewAnimation();
                    }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建新的动画工程");
            }
            
                    if (ImGui.menuItem("Open...", "Ctrl+O")) {
                        handleOpenAnimation();
                    }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("打开现有动画文件");
            }
            
            UITheme.spacingSmall();
            
                    if (ImGui.menuItem("Save", "Ctrl+S")) {
                        handleSaveAnimation();
                    }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("保存当前动画");
            }
            
                    if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) {
                        handleSaveAsAnimation();
                    }
            
                    ImGui.separator();
            
                    if (ImGui.menuItem("Import Resources...")) {
                        handleImportResources();
                    }
            if (ImGui.menuItem("Export Animation...", "Ctrl+E")) {
                        handleExportAnimation();
                    }
            if (ImGui.menuItem(Icons.VIDEO + " Export Video...")) {
                        handleExportVideo();
                    }
            
                    ImGui.separator();
            
                    if (ImGui.menuItem("Exit", "Alt+F4")) {
                        handleExit();
                    }
            
                    ImGui.endMenu();
        }
    }

    // ==================== Edit 菜单 ====================
    
    /**
     * 编辑菜单 - 基础编辑功能
     */
    private void renderEditMenu() {
        if (ImGui.beginMenu(Icons.EDIT + " Edit")) {
                    if (ImGui.menuItem("Undo", "Ctrl+Z")) {
                        handleUndo();
                    }
                    if (ImGui.menuItem("Redo", "Ctrl+Y")) {
                        handleRedo();
                    }
            
                    ImGui.separator();
            
                    if (ImGui.menuItem("Cut", "Ctrl+X")) {
                        handleCut();
                    }
                    if (ImGui.menuItem("Copy", "Ctrl+C")) {
                        handleCopy();
                    }
                    if (ImGui.menuItem("Paste", "Ctrl+V")) {
                        handlePaste();
                    }
            if (ImGui.menuItem("Delete", "Del")) {
                        handleDelete();
                    }
            
            ImGui.separator();
            
                    if (ImGui.menuItem("Select All", "Ctrl+A")) {
                        handleSelectAll();
                    }
            if (ImGui.menuItem("Deselect All", "Ctrl+Shift+A")) {
                handleDeselectAll();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Group Keyframes", "Ctrl+G")) {
                handleGroupKeyframes();
            }
            if (ImGui.menuItem("Ungroup Keyframes", "Ctrl+Shift+G")) {
                handleUngroupKeyframes();
            }
            
                    ImGui.endMenu();
        }
    }

    // ==================== Animation 菜单 ====================
    
    /**
     * 动画菜单 - 动画控制与管理
     */
    private void renderAnimationMenu() {
        PlaybackController pc = PlaybackController.get();
            
        if (ImGui.beginMenu(Icons.ANIMATION + " Animation")) {
            // 播放控制
            String playLabel = pc.isPlaying() ? (Icons.PAUSE + " Pause") : (Icons.PLAY + " Play");
            if (ImGui.menuItem(playLabel, "Space")) {
                if (pc.isPlaying()) {
                    pc.pause();
                } else {
                    pc.play();
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(pc.isPlaying() ? "暂停动画播放" : "开始播放动画");
            }
            
            if (ImGui.menuItem(Icons.STOP + " Stop", "Ctrl+Space")) {
                pc.stop();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("停止播放并回到起点");
            }
            
            ImGui.separator();
            
            // 关键帧操作
            if (ImGui.menuItem("Add Keyframe", "K")) {
                handleAddKeyframe();
            }
            if (ImGui.menuItem("Delete Keyframe", "Shift+K")) {
                handleDeleteKeyframe();
            }
            if (ImGui.menuItem("Edit Keyframe Properties", "Enter")) {
                handleEditKeyframe();
            }
            
            ImGui.separator();
            
            // 动画片段
            if (ImGui.menuItem("New Animation Clip")) {
                handleNewClip();
            }
            if (ImGui.menuItem("Duplicate Clip")) {
                handleDuplicateClip();
            }
            
            ImGui.separator();
            
            // 播放选项
            boolean looping = pc.isLoop();
            if (ImGui.menuItem(Icons.LOOP + " Loop Playback", "", looping)) {
                pc.toggleLoop();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("开启/关闭循环播放");
            }
            
            if (ImGui.beginMenu("Playback Speed")) {
                if (ImGui.menuItem("0.25x")) pc.setSpeed(0.25f);
                if (ImGui.menuItem("0.5x")) pc.setSpeed(0.5f);
                if (ImGui.menuItem("1.0x (Normal)")) pc.setSpeed(1.0f);
                if (ImGui.menuItem("1.5x")) pc.setSpeed(1.5f);
                if (ImGui.menuItem("2.0x")) pc.setSpeed(2.0f);
                ImGui.endMenu();
            }
            
            ImGui.separator();
            
            // 时间轴导航
            if (ImGui.menuItem(Icons.SKIP_BACK + " Go to Start", "Home")) {
                pc.seek(0f);
            }
            if (ImGui.menuItem(Icons.SKIP_FORWARD + " Go to End", "End")) {
                pc.seek(pc.getDuration());
            }
            
            ImGui.separator();
            
            // 高级功能
            if (ImGui.menuItem("Bake Animation")) {
                handleBakeAnimation();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("烘焙动画为可播放序列");
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Container 菜单 ====================

    /**
     * 容器菜单 - 容器管理专属（🆕 新增）
     */
    private void renderContainerMenu() {
        if (ImGui.beginMenu(Icons.CONTAINER + " Container")) {
            if (ImGui.menuItem("Add Container", "Ctrl+Shift+C")) {
                handleAddContainer();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建新的容器");
            }
            
            if (ImGui.menuItem("Duplicate Container", "Ctrl+D")) {
                handleDuplicateContainer();
            }
            if (ImGui.menuItem("Rename Container", "F2")) {
                handleRenameContainer();
            }
            if (ImGui.menuItem("Delete Container", "Delete")) {
                handleDeleteContainer();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Assign Selection to Container", "Ctrl+G")) {
                handleAssignSelectionToContainer();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将选中的方块指派给容器");
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Import Container...")) {
                handleImportContainer();
            }
            if (ImGui.menuItem("Export Container...", "Ctrl+E")) {
                handleExportContainer();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Sync With World")) {
                handleSyncContainer();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("同步容器与Minecraft世界");
            }
            
            if (ImGui.menuItem("Refresh All Containers")) {
                handleRefreshContainers();
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Window 菜单 ====================

    /**
     * 窗口菜单 - 面板与布局管理
     */
    private void renderWindowMenu() {
        if (ImGui.beginMenu(Icons.WINDOW + " Window")) {
            // 面板显示控制
            if (ImGui.menuItem("Show All Panels")) {
                showAllPanels();
            }
            if (ImGui.menuItem("Hide All Panels")) {
                hideAllPanels();
            }
            
            ImGui.separator();
            
            // 面板可见性切换
            if (panelRegistry != null && !panelRegistry.isEmpty()) {
                if (ImGui.beginMenu("Toggle Panels")) {
                    for (IPanel panel : panelRegistry.getAllPanels()) {
                        if (panel != this) { // 不显示 MenuBar 自己
                            boolean visible = panel.isVisible();
                            if (ImGui.menuItem(panel.getPanelName(), "", visible)) {
                                panel.setVisible(!visible);
                            }
                        }
                    }
                ImGui.endMenu();
                }
            }
            
            ImGui.separator();
            
            // 布局管理
            if (ImGui.menuItem("Save Current Layout")) {
                handleSaveCurrentLayout();
            }
            if (ImGui.menuItem("Restore Default Layout")) {
                handleResetLayout();
            }
            
            if (ImGui.beginMenu("Layout Presets")) {
                if (ImGui.menuItem("Compact Layout")) {
                    handleCompactLayout();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("紧凑布局 - 适合小屏幕");
                }
                
                if (ImGui.menuItem("Wide Layout")) {
                    handleWideLayout();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("宽屏布局 - 适合大显示器");
                }
                
                if (ImGui.menuItem("Animation Focus")) {
                    handleAnimationFocusLayout();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("动画编辑优先布局");
                }
                
                ImGui.endMenu();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Toggle Fullscreen", "F11")) {
                handleToggleFullscreen();
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Tools 菜单 ====================

    /**
     * 工具菜单 - 开发与调试
     */
    private void renderToolsMenu() {
        if (ImGui.beginMenu(Icons.TOOLBOX + " Tools")) {
            if (ImGui.menuItem("Settings...", "Ctrl+,")) {
                showSettingsDialog.set(true);
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Developer Console", "`")) {
                handleDeveloperConsole();
            }
            if (ImGui.menuItem("Performance Monitor")) {
                handleShowPerformanceMonitor();
            }
            if (ImGui.menuItem("Resource Inspector")) {
                handleResourceInspector();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("Scene Profiler")) {
                handleSceneProfiler();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("分析场景性能");
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Help 菜单 ====================

    /**
     * 帮助菜单 - 帮助与关于
     */
    private void renderHelpMenu() {
        if (ImGui.beginMenu(Icons.HELP + " Help")) {
            if (ImGui.menuItem("Keyboard Shortcuts", "F1")) {
                showShortcutsDialog.set(true);
            }
            if (ImGui.menuItem("User Manual")) {
                handleShowUserManual();
            }
            if (ImGui.menuItem("Video Tutorials")) {
                handleShowVideoTutorials();
            }
            
        ImGui.separator();
        
            if (ImGui.menuItem("Check for Updates")) {
                handleCheckUpdates();
        }
            if (ImGui.menuItem("Report Bug")) {
                handleReportBug();
        }
        
        ImGui.separator();
            
            if (ImGui.menuItem("About ChronoBlocks")) {
                showAboutDialog.set(true);
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== 底部状态栏 ====================

    /**
     * 渲染底部状态栏
     */
    private void renderStatusBar() {
        // 计算 FPS（平滑处理）
        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = currentTime;
        
        if (deltaTime > 0 && deltaTime < 1.0f) { // 防止异常值
            float currentFps = 1.0f / deltaTime;
            fps = fps * fpsSmoothing + currentFps * (1.0f - fpsSmoothing);
        }
        
        // 获取状态信息
        PlaybackController pc = PlaybackController.get();
        
        int visiblePanels = 0;
        int totalPanels = 0;
        if (panelRegistry != null) {
            totalPanels = panelRegistry.size();
            for (IPanel panel : panelRegistry.getAllPanels()) {
                if (panel.isVisible()) {
                    visiblePanels++;
                }
            }
        }
        
        float currentTimeline = pc.getCurrentTime();
        float totalDuration = pc.getDuration();
        
        // 渲染状态栏窗口
        float statusBarHeight = 22.0f;
        ImGui.setNextWindowPos(0, ImGui.getIO().getDisplaySizeY() - statusBarHeight);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), statusBarHeight);
        
        int flags = ImGuiWindowFlags.NoTitleBar 
                  | ImGuiWindowFlags.NoResize 
                  | ImGuiWindowFlags.NoMove
                  | ImGuiWindowFlags.NoScrollbar 
                  | ImGuiWindowFlags.NoScrollWithMouse
                  | ImGuiWindowFlags.NoCollapse;
        
        if (ImGui.begin("##StatusBar", flags)) {
            try {
                
                // 播放状态
                String playStatus = pc.isPlaying() ? (Icons.PLAY + " Playing") : (Icons.PAUSE + " Paused");
                int statusColor = pc.isPlaying() 
                    ? rgba(51, 204, 51)  // 绿色
                    : UITheme.DarkTheme.TEXT;
                UITheme.label(playStatus, statusColor);
                ImGui.sameLine(150);
                
                // 时间轴
                UITheme.label(String.format("Time: %.2fs / %.2fs", currentTimeline, totalDuration), 
                    UITheme.DarkTheme.TEXT);
                ImGui.sameLine(350);
                
                // 面板数量
                UITheme.label(String.format("Panels: %d/%d visible", visiblePanels, totalPanels), 
                    UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.sameLine(550);
                
                // 循环状态
                if (pc.isLoop()) {
                    UITheme.label(Icons.LOOP + " Loop", rgba(61, 133, 224));
                    ImGui.sameLine(750);
                }
                
                // 播放速度
                float playbackSpeed = pc.getSpeed();
                if (Math.abs(playbackSpeed - 1.0f) > 0.01f) {
                    UITheme.label(String.format("Speed: %.2fx", playbackSpeed), 
                        UITheme.DarkTheme.TEXT);
                }
                
            } catch (Exception e) {
                logger.error("[StatusBar] 渲染错误: {}", e.getMessage());
            } finally {
                ImGui.end();
            }
        }
    }
    
    // RGBA颜色打包辅助方法
    private static int rgba(int r, int g, int b) {
        return (r << 24) | (g << 16) | (b << 8) | 255;
    }

    // ==================== 对话框 ====================

    /**
     * 渲染所有对话框
     */
    private void renderDialogs() {
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
            
            // 使用自动宽度
            if (ImGui.button("OK", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                        showAboutDialog.set(false);
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
            
            // 使用自动宽度
            if (ImGui.button("OK", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                        showSettingsDialog.set(false);
                    }
                    ImGui.sameLine();
            if (ImGui.button("Cancel", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                        showSettingsDialog.set(false);
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
            
            // 使用自动宽度
            if (ImGui.button("Close", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                showShortcutsDialog.set(false);
            }
            
            ImGui.endPopup();
        }
    }

    /**
     * “新建动画”确认弹窗，防止误操作清空当前工作区。
     */
    private void renderNewAnimationDialog() {
        if (!showNewAnimationConfirm) {
            return;
        }
        ImGui.openPopup("Reset Workspace");
        ImGui.setNextWindowSize(420, 190);
        if (ImGui.beginPopupModal("Reset Workspace", (imgui.type.ImBoolean) null, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textColored(1.0f, 0.47f, 0.31f, 1.0f, Icons.WARNING + " 警告：该操作会清空当前动画与容器！");
            ImGui.spacing();
            ImGui.textWrapped("将删除当前存档中的时间线、关键帧和容器引用，并写入全新的空白工程。请确认已备份重要内容。");
            ImGui.spacing();

            if (ImGui.button("确认重置", 150, UITheme.Spacing.BUTTON_HEIGHT)) {
                performNewAnimationReset();
                showNewAnimationConfirm = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                showNewAnimationConfirm = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    // ==================== 事件处理方法 ====================
    
    // File 菜单
    private void handleNewAnimation() {
        logger.info("[MenuBar] New Animation");
        showNewAnimationConfirm = true;
    }
    
    private void performNewAnimationReset() {
        logger.info("[MenuBar] Reset workspace and timeline for new animation");
        try {
            TimelinePanel timelinePanel = panelRegistry != null ? panelRegistry.getPanel(TimelinePanel.class) : null;
            if (timelinePanel != null) {
                timelinePanel.resetWorkspace(true);
            } else {
                // 兜底：清空容器并删除时间线存档
                ContainerManager.getInstance().clear();
                com.chronoblocks.ui.timeline.persistence.TimelineModelPersistence.resetForCurrentWorld();
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 新建动画重置失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleOpenAnimation() {
        logger.info("[MenuBar] Open Animation");
    }
    
    private void handleSaveAnimation() {
        logger.info("[MenuBar] Save Animation");
    }
    
    private void handleSaveAsAnimation() {
        logger.info("[MenuBar] Save As Animation");
    }
    
    private void handleImportResources() {
        logger.info("[MenuBar] Import Resources");
    }
    
    private void handleExportAnimation() {
        logger.info("[MenuBar] Export Animation");
    }

    private void handleExportVideo() {
        logger.info("[MenuBar] Export Video");
        if (uiManager != null) {
            uiManager.openVideoExportDialog();
        } else {
            logger.warn("[MenuBar] UIManager 未注入，无法打开视频导出对话框");
        }
    }
    
    private void handleExit() {
        logger.info("[MenuBar] Exit");
        // TODO: 实现退出逻辑
    }
    
    // Edit 菜单
    private void handleUndo() {
        logger.info("[MenuBar] Undo");
    }
    
    private void handleRedo() {
        logger.info("[MenuBar] Redo");
    }
    
    private void handleCut() {
        logger.info("[MenuBar] Cut");
    }
    
    private void handleCopy() {
        logger.info("[MenuBar] Copy");
    }
    
    private void handlePaste() {
        logger.info("[MenuBar] Paste");
    }
    
    private void handleDelete() {
        logger.info("[MenuBar] Delete");
    }
    
    private void handleSelectAll() {
        logger.info("[MenuBar] Select All");
    }
    
    private void handleDeselectAll() {
        logger.info("[MenuBar] Deselect All");
    }
    
    private void handleGroupKeyframes() {
        logger.info("[MenuBar] Group Keyframes");
    }
    
    private void handleUngroupKeyframes() {
        logger.info("[MenuBar] Ungroup Keyframes");
    }
    
    // Animation 菜单
    private void handleAddKeyframe() {
        logger.info("[MenuBar] Add Keyframe");
    }
    
    private void handleDeleteKeyframe() {
        logger.info("[MenuBar] Delete Keyframe");
    }
    
    private void handleEditKeyframe() {
        logger.info("[MenuBar] Edit Keyframe");
    }
    
    private void handleNewClip() {
        logger.info("[MenuBar] New Clip");
    }
    
    private void handleDuplicateClip() {
        logger.info("[MenuBar] Duplicate Clip");
    }
    
    private void handleBakeAnimation() {
        logger.info("[MenuBar] Bake Animation");
    }
    
    // Container 菜单
    private void handleAddContainer() {
        logger.info("[MenuBar] Add Container");
    }
    
    private void handleDuplicateContainer() {
        logger.info("[MenuBar] Duplicate Container");
    }
    
    private void handleRenameContainer() {
        logger.info("[MenuBar] Rename Container");
    }
    
    private void handleDeleteContainer() {
        logger.info("[MenuBar] Delete Container");
    }
    
    private void handleAssignSelectionToContainer() {
        logger.info("[MenuBar] Assign Selection to Container");
    }
    
    private void handleImportContainer() {
        logger.info("[MenuBar] Import Container");
    }
    
    private void handleExportContainer() {
        logger.info("[MenuBar] Export Container");
    }
    
    private void handleSyncContainer() {
        logger.info("[MenuBar] Sync Container");
    }
    
    private void handleRefreshContainers() {
        logger.info("[MenuBar] Refresh Containers");
    }
    
    // Window 菜单
    private void showAllPanels() {
        if (panelRegistry == null) return;
        for (IPanel panel : panelRegistry.getAllPanels()) {
            panel.setVisible(true);
        }
        logger.info("[MenuBar] Show All Panels");
    }
    
    private void hideAllPanels() {
        if (panelRegistry == null) return;
        for (IPanel panel : panelRegistry.getAllPanels()) {
            if (panel != this) { // 不隐藏 MenuBar
                panel.setVisible(false);
            }
        }
        logger.info("[MenuBar] Hide All Panels");
    }
    
    private void handleSaveCurrentLayout() {
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            layoutManager.saveLayout();
            logger.info("[MenuBar] 当前布局已保存");
        } catch (Exception e) {
            logger.error("[MenuBar] 保存布局失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleResetLayout() {
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            
            // 删除ImGui INI文件
            java.nio.file.Path iniPath = layoutManager.getImGuiIniPath();
            if (java.nio.file.Files.exists(iniPath)) {
                java.nio.file.Files.delete(iniPath);
            }
            
            // 重置布局
            layoutManager.resetToDefault();
            
            // 重置DockSpace布局状态
            com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
            
            logger.info("[MenuBar] 布局已重置为默认");
        } catch (Exception e) {
            logger.error("[MenuBar] 重置布局失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleCompactLayout() {
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            if (layoutManager.loadLayoutPreset("compact")) {
                logger.info("[MenuBar] 已加载紧凑布局预设");
            } else {
                logger.warn("[MenuBar] 紧凑布局预设不存在，将创建默认紧凑布局");
                // TODO: 创建紧凑布局预设
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 加载紧凑布局失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleWideLayout() {
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            if (layoutManager.loadLayoutPreset("wide")) {
                logger.info("[MenuBar] 已加载宽屏布局预设");
            } else {
                logger.warn("[MenuBar] 宽屏布局预设不存在，将创建默认宽屏布局");
                // TODO: 创建宽屏布局预设
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 加载宽屏布局失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleAnimationFocusLayout() {
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            if (layoutManager.loadLayoutPreset("animation_focus")) {
                logger.info("[MenuBar] 已加载动画焦点布局预设");
            } else {
                logger.warn("[MenuBar] 动画焦点布局预设不存在，将创建默认动画焦点布局");
                // TODO: 创建动画焦点布局预设
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 加载动画焦点布局失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleToggleFullscreen() {
        logger.info("[MenuBar] Toggle Fullscreen");
    }
    
    // Tools 菜单
    private void handleDeveloperConsole() {
        logger.info("[MenuBar] Developer Console");
    }
    
    private void handleShowPerformanceMonitor() {
        logger.info("[MenuBar] Performance Monitor");
    }
    
    private void handleResourceInspector() {
        logger.info("[MenuBar] Resource Inspector");
    }
    
    private void handleSceneProfiler() {
        logger.info("[MenuBar] Scene Profiler");
    }
    
    // Help 菜单
    private void handleShowUserManual() {
        logger.info("[MenuBar] User Manual");
    }
    
    private void handleShowVideoTutorials() {
        logger.info("[MenuBar] Video Tutorials");
    }
    
    private void handleCheckUpdates() {
        logger.info("[MenuBar] Check Updates");
    }
    
    private void handleReportBug() {
        logger.info("[MenuBar] Report Bug");
    }

    // ==================== 依赖注入接口实现 ====================

    @Override
    public void setPanelRegistry(IPanelRegistry panelRegistry) {
        this.panelRegistry = panelRegistry;
    }

    @Override
    public void setUIManager(ChronoBlocksUIManager uiManager) {
        this.uiManager = uiManager;
    }
}
