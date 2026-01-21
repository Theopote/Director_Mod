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
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

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
                    renderTimelineMenu();
                    renderCameraMenu();
                    renderPlaybackMenu();
                    renderWindowMenu();
                    renderHelpMenu();

                    // 右侧：全局状态 + 窗口级操作（FPS + 关闭）
                    renderRightSideControls();
                } catch (Exception e) {
                logger.error("[MenuBar] 菜单渲染错误: {}", e.getMessage(), e);
        } finally {
                    ImGui.endMainMenuBar();
            }
        }
    }

    /**
     * 菜单栏右侧控件：FPS + 关闭按钮
     *
     * <p>说明：ToolbarPanel 中的 FPS/关闭已迁移到这里，避免工具栏布局被分割线/换行干扰。</p>
     */
    private void renderRightSideControls() {
        // 估算宽度并把光标移动到右侧
        float spacing = ImGui.getStyle().getItemSpacing().x;
        float paddingX = ImGui.getStyle().getFramePadding().x * 2.0f;

        float fpsValue = ImGui.getIO().getFramerate();
        String fpsText = String.format("FPS: %.0f", fpsValue);
        float fpsW = ImGui.calcTextSize(fpsText).x;

        String closeIcon = Icons.CLOSE;
        float closeW = ImGui.calcTextSize(closeIcon).x + paddingX;

        float totalW = fpsW + spacing + closeW;
        float rightEdge = ImGui.getWindowWidth() - ImGui.getStyle().getWindowPadding().x;
        float targetX = Math.max(0.0f, rightEdge - totalW);
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), targetX));

        // FPS：按性能用颜色区分
        int fpsColor = fpsValue >= 60 ? UITheme.DarkTheme.FPS_GOOD
                : (fpsValue >= 30 ? UITheme.DarkTheme.FPS_WARN : UITheme.DarkTheme.FPS_BAD);
        float[] col = UITheme.toFloatArrayPublic(fpsColor);
        ImGui.textColored(col[0], col[1], col[2], col[3], fpsText);

        ImGui.sameLine();

        // 关闭：用危险按钮颜色
        UITheme.pushDangerButtonColors();
        try {
            if (ImGui.button(closeIcon + "##closeChronoBlocks")) {
                closeChronoBlocksWindow();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("关闭 ChronoBlocks 窗口");
            }
        } finally {
            ImGui.popStyleColor(3);
        }
    }

    private void closeChronoBlocksWindow() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.currentScreen instanceof com.chronoblocks.ui.ChronoBlocksScreen screen) {
                screen.close();
                client.setScreen(null);
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 关闭 ChronoBlocks 窗口失败: {}", e.getMessage(), e);
        }
    }

    // ==================== File 菜单 ====================
    
    /**
     * 文件菜单 - 工程与文件管理
     */
    private void renderFileMenu() {
        if (ImGui.beginMenu(Icons.FOLDER + " File")) {
            if (ImGui.menuItem("新建项目", "Ctrl+N")) {
                handleNewAnimation();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建新的动画工程");
            }
            
            if (ImGui.menuItem("打开项目", "Ctrl+O")) {
                handleOpenAnimation();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("打开现有动画文件");
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("保存", "Ctrl+S")) {
                handleSaveAnimation();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("保存当前动画");
            }
            
            if (ImGui.menuItem("另存为", "Ctrl+Shift+S")) {
                handleSaveAsAnimation();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem(Icons.VIDEO + " 导出视频")) {
                handleExportVideo();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("退出", "Alt+F4")) {
                handleExit();
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Timeline 菜单 ====================
    
    /**
     * 时间线菜单 - 时间线设置与导航
     */
    private void renderTimelineMenu() {
        if (ImGui.beginMenu(Icons.TIMELINE + " Timeline")) {
            if (ImGui.menuItem("时间线设置…")) {
                handleTimelineSettings();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("对齐到播放头")) {
                handleAlignToPlayhead();
            }
            if (ImGui.menuItem("缩放到全部内容")) {
                handleZoomToFit();
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Camera 菜单 ====================
    
    /**
     * 摄像机菜单 - 摄像机路径预设与操作
     */
    private void renderCameraMenu() {
        if (ImGui.beginMenu(Icons.CAMERA + " Camera")) {
            if (ImGui.beginMenu("摄像机路径预设")) {
                if (ImGui.menuItem("Orbit")) {
                    handleCreateOrbitPath();
                }
                if (ImGui.menuItem("Dolly")) {
                    handleCreateDollyPath();
                }
                if (ImGui.menuItem("Crane")) {
                    handleCreateCranePath();
                }
                ImGui.endMenu();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("对齐摄像机到视角")) {
                handleAlignCameraToView();
            }
            if (ImGui.menuItem("从当前视角创建镜头")) {
                handleCreateCameraFromView();
            }
            
            ImGui.endMenu();
        }
    }

    // ==================== Playback 菜单 ====================
    
    /**
     * 播放菜单 - 播放控制与管理
     */
    private void renderPlaybackMenu() {
        PlaybackController pc = PlaybackController.get();
            
        if (ImGui.beginMenu(Icons.PLAY + " Playback")) {
            // 播放控制
            String playLabel = pc.isPlaying() ? (Icons.PAUSE + " 播放 / 暂停") : (Icons.PLAY + " 播放 / 暂停");
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
            
            if (ImGui.menuItem(Icons.PAUSE + " 暂停", "Ctrl+Space")) {
                pc.pause();
            }
            if (ImGui.menuItem(Icons.STOP + " 停止", "Ctrl+Shift+Space")) {
                pc.stop();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("停止播放并回到起点");
            }
            
            ImGui.separator();
            
            // 播放速度
            if (ImGui.beginMenu("播放速度")) {
                if (ImGui.menuItem("0.25x")) pc.setSpeed(0.25f);
                if (ImGui.menuItem("0.5x")) pc.setSpeed(0.5f);
                if (ImGui.menuItem("1x")) pc.setSpeed(1.0f);
                if (ImGui.menuItem("2x")) pc.setSpeed(2.0f);
                if (ImGui.menuItem("3x")) pc.setSpeed(3.0f);
                ImGui.endMenu();
            }
            
            // 循环播放
            boolean looping = pc.isLoop();
            if (ImGui.menuItem(Icons.LOOP + " 循环播放", "", looping)) {
                pc.toggleLoop();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("开启/关闭循环播放");
            }
            
            // 播放范围
            if (ImGui.menuItem("播放范围（In / Out）")) {
                handlePlaybackRange();
            }
            
            ImGui.separator();
            
            // 时间轴导航
            if (ImGui.menuItem(Icons.SKIP_BACK + " Go to Start", "Home")) {
                pc.seek(0f);
            }
            if (ImGui.menuItem(Icons.SKIP_FORWARD + " Go to End", "End")) {
                pc.seek(pc.getDuration());
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
                            // ⚠️ ActorsPanel 已隐藏，等待下一版本
                            if ("Actors".equals(panel.getPanelName())) {
                                continue;
                            }
                            boolean visible = panel.isVisible();
                            String panelName = panel.getPanelName();
                            // 根据用户要求显示特定的面板名称
                            String displayName = switch (panelName) {
                                case "Timeline" -> "Timeline";
                                case "ToolProperties" -> "Tool Properties";
                                case "Container" -> "Container";
                                case "RenderSettings" -> "RenderSettings";
                                case "Toolbar" -> "Toolbar";
                                case "SelectionFilter" -> "SelectionFiler";
                                case "Properties" -> "Properties";
                                case "SelectionHistory" -> "SelectionHistory";
                                default -> panelName;
                            };
                            if (ImGui.menuItem(displayName, "", visible)) {
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
            
            ImGui.separator();
            
            // UI 缩放
            if (ImGui.menuItem("UI 缩放")) {
                handleUIScale();
            }
            
            // 主题切换
            if (ImGui.beginMenu("主题切换（Dark / Light）")) {
                if (ImGui.menuItem("Dark", "", true)) {
                    handleThemeSwitch("Dark");
                }
                if (ImGui.menuItem("Light", "", false)) {
                    handleThemeSwitch("Light");
                }
                ImGui.endMenu();
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
            if (ImGui.menuItem("快捷键一览", "F1")) {
                showShortcutsDialog.set(true);
            }
            if (ImGui.menuItem("User Manual")) {
                handleShowUserManual();
            }
            if (ImGui.menuItem("Video Tutorial")) {
                handleShowVideoTutorials();
            }
            
            ImGui.separator();
            
            // Report Bug 子菜单
            if (ImGui.beginMenu("Report Bug")) {
                if (ImGui.menuItem("反馈")) {
                    handleReportBugFeedback();
                }
                if (ImGui.menuItem("Discord")) {
                    handleReportBugDiscord();
                }
                if (ImGui.menuItem("GitHub")) {
                    handleReportBugGitHub();
                }
                ImGui.endMenu();
            }
            
            ImGui.separator();
            
            if (ImGui.menuItem("关于 ChronoBlocks")) {
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
        // 计算 FPS（平滑处理）- 每帧实时更新
        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = currentTime;
        
        if (deltaTime > 0 && deltaTime < 1.0f) { // 防止异常值
            float currentFps = 1.0f / deltaTime;
            fps = fps * fpsSmoothing + currentFps * (1.0f - fpsSmoothing);
        }
        
        // 获取状态信息 - 每帧实时获取最新状态
        PlaybackController pc = PlaybackController.get();
        
        // 实时统计面板数量
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
        
        // 实时获取播放状态和时间信息
        boolean isPlaying = pc.isPlaying();
        boolean isLoop = pc.isLoop();
        float playbackSpeed = pc.getSpeed();
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
        
        // 保存并设置窗口样式：内边距为0，圆角为0（无圆角）
        float[] oldPadding = new float[2];
        oldPadding[0] = ImGui.getStyle().getWindowPadding().x;
        oldPadding[1] = ImGui.getStyle().getWindowPadding().y;
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 0.0f);
        
        if (ImGui.begin("##StatusBar", flags)) {
            try {
                // 计算垂直居中位置：状态栏高度的一半减去文字高度的一半
                float textHeight = ImGui.getTextLineHeight();
                float centerY = (statusBarHeight - textHeight) * 0.5f;
                ImGui.setCursorPosY(centerY);
                
                // 添加左侧内边距
                ImGui.setCursorPosX(ImGui.getStyle().getFramePadding().x);
                
                // 1. FPS显示（左侧）- 实时更新
                int fpsColor = fps >= 60 ? rgba(51, 204, 51)  // 绿色（高FPS）
                        : (fps >= 30 ? rgba(255, 204, 51)     // 黄色（中等FPS）
                        : rgba(255, 102, 102));                // 红色（低FPS）
                UITheme.label(String.format("FPS: %.0f", fps), fpsColor);
                ImGui.sameLine();
                ImGui.text("|");
                ImGui.sameLine();
                
                // 2. 播放状态 - 实时更新
                String playStatus = isPlaying ? (Icons.PLAY + " Playing") : (Icons.PAUSE + " Paused");
                int statusColor = isPlaying 
                    ? rgba(51, 204, 51)  // 绿色（播放中）
                    : UITheme.DarkTheme.TEXT;  // 灰色（暂停）
                UITheme.label(playStatus, statusColor);
                ImGui.sameLine();
                ImGui.text("|");
                ImGui.sameLine();
                
                // 3. 时间轴信息 - 实时更新（显示更精确的时间）
                // 格式化时间：如果总时长小于60秒，显示到小数点后2位；否则显示到整数秒
                String timeFormat = totalDuration < 60.0f ? "%.2fs / %.2fs" : "%.0fs / %.0fs";
                UITheme.label(String.format("Time: " + timeFormat, currentTimeline, totalDuration), 
                    UITheme.DarkTheme.TEXT);
                ImGui.sameLine();
                ImGui.text("|");
                ImGui.sameLine();
                
                // 4. 播放速度（如果非1.0x）- 实时更新
                if (Math.abs(playbackSpeed - 1.0f) > 0.01f) {
                    UITheme.label(String.format("Speed: %.2fx", playbackSpeed), 
                        UITheme.DarkTheme.TEXT);
                    ImGui.sameLine();
                    ImGui.text("|");
                    ImGui.sameLine();
                }
                
                // 5. 循环状态 - 实时更新
                if (isLoop) {
                    UITheme.label(Icons.LOOP + " Loop", rgba(61, 133, 224));
                    ImGui.sameLine();
                    ImGui.text("|");
                    ImGui.sameLine();
                }
                
                // 6. 面板数量
                UITheme.label(String.format("Panels: %d/%d", visiblePanels, totalPanels), 
                    UITheme.DarkTheme.TEXT_DISABLED);
                
                // 7. 容器信息（如果有容器管理器）
                try {
                    ContainerManager containerManager = ContainerManager.getInstance();
                    if (containerManager != null) {
                        int containerCount = containerManager.getContainerCount();
                        if (containerCount > 0) {
                            ImGui.sameLine();
                            ImGui.text("|");
                            ImGui.sameLine();
                            UITheme.label(String.format("Containers: %d", containerCount), 
                                UITheme.DarkTheme.TEXT_DISABLED);
                        }
                    }
                } catch (Exception ignored) {
                    // 忽略容器管理器获取失败
                }
                
            } catch (Exception e) {
                logger.error("[StatusBar] 渲染错误: {}", e.getMessage());
            } finally {
                ImGui.end();
                // 恢复窗口样式（圆角和内边距）
                ImGui.popStyleVar(); // WindowRounding
                ImGui.popStyleVar(); // WindowPadding
            }
        } else {
            // 如果窗口未开始，也要恢复样式
            ImGui.popStyleVar(); // WindowRounding
            ImGui.popStyleVar(); // WindowPadding
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
        // 使用ImBoolean对象而不是null，避免NullPointerException
        ImBoolean popupOpen = new ImBoolean(true);
        if (ImGui.beginPopupModal("Reset Workspace", popupOpen, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textColored(1.0f, 0.47f, 0.31f, 1.0f, Icons.WARNING + " 警告：该操作会清空当前动画与容器！");
            ImGui.spacing();
            ImGui.textWrapped("将删除当前存档中的时间线、关键帧和容器引用，并写入全新的空白工程。请确认已备份重要内容。");
            ImGui.spacing();

            if (ImGui.button("确认重置", 150, UITheme.Spacing.BUTTON_HEIGHT)) {
                performNewAnimationReset();
                showNewAnimationConfirm = false;
                popupOpen.set(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                showNewAnimationConfirm = false;
                popupOpen.set(false);
                ImGui.closeCurrentPopup();
            }
            
            // 如果用户通过其他方式关闭弹窗（如点击外部或ESC），更新状态
            if (!popupOpen.get()) {
                showNewAnimationConfirm = false;
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
                Objects.requireNonNull(ContainerManager.getInstance()).clear();
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
    
    // Timeline 菜单
    private void handleTimelineSettings() {
        logger.info("[MenuBar] Timeline Settings");
        showSettingsDialog.set(true);
    }
    
    private void handleAlignToPlayhead() {
        logger.info("[MenuBar] Align to Playhead");
        try {
            TimelinePanel timelinePanel = panelRegistry != null ? panelRegistry.getPanel(TimelinePanel.class) : null;
            if (timelinePanel != null) {
                // 通过调用 TimelinePanel 的 onFitToViewClicked 来触发对齐
                timelinePanel.onFitToViewClicked();
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 对齐到播放头失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleZoomToFit() {
        logger.info("[MenuBar] Zoom to Fit");
        try {
            TimelinePanel timelinePanel = panelRegistry != null ? panelRegistry.getPanel(TimelinePanel.class) : null;
            if (timelinePanel != null) {
                timelinePanel.onFitToViewClicked();
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 缩放到全部内容失败: {}", e.getMessage(), e);
        }
    }
    
    // Camera 菜单
    private void handleCreateOrbitPath() {
        logger.info("[MenuBar] Create Orbit Path");
        try {
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
            if (model != null) {
                float currentTime = model.getCurrentTime();
                net.minecraft.util.math.Vec3d center = net.minecraft.util.math.Vec3d.ZERO;
                float radius = 10.0f;
                float height = 64.0f;
                com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip orbitClip = 
                    new com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip(
                        "Orbit @" + String.format("%.2f", currentTime),
                        currentTime,
                        currentTime + 5.0f,
                        center,
                        radius,
                        height
                    );
                // TODO: 添加到时间线
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 创建 Orbit 路径失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleCreateDollyPath() {
        logger.info("[MenuBar] Create Dolly Path");
        try {
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
            if (model != null) {
                float currentTime = model.getCurrentTime();
                com.chronoblocks.ui.timeline.model.camera.path.DollyPathClip dollyClip = 
                    new com.chronoblocks.ui.timeline.model.camera.path.DollyPathClip(
                        "Dolly @" + String.format("%.2f", currentTime),
                        currentTime,
                        currentTime + 5.0f
                    );
                // TODO: 添加到时间线
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 创建 Dolly 路径失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleCreateCranePath() {
        logger.info("[MenuBar] Create Crane Path");
        try {
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
            if (model != null) {
                float currentTime = model.getCurrentTime();
                com.chronoblocks.ui.timeline.model.camera.path.CranePathClip craneClip = 
                    new com.chronoblocks.ui.timeline.model.camera.path.CranePathClip(
                        "Crane @" + String.format("%.2f", currentTime),
                        currentTime,
                        currentTime + 5.0f
                    );
                // TODO: 添加到时间线
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 创建 Crane 路径失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleAlignCameraToView() {
        logger.info("[MenuBar] Align Camera to View");
        // TODO: 实现对齐摄像机到视角
    }
    
    private void handleCreateCameraFromView() {
        logger.info("[MenuBar] Create Camera from View");
        try {
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
            if (model != null) {
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController cameraController = 
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = cameraController.getActiveCameraTrack();
                if (activeTrack != null) {
                    float currentTime = model.getCurrentTime();
                    com.chronoblocks.ui.timeline.model.camera.CameraKeyframe keyframe = 
                        new com.chronoblocks.ui.timeline.model.camera.CameraKeyframe(
                            "Camera @" + String.format("%.2f", currentTime),
                            currentTime
                        );
                    cameraController.captureFromCurrentCamera(keyframe);
                    // TODO: 添加到轨道
                }
            }
        } catch (Exception e) {
            logger.error("[MenuBar] 从当前视角创建镜头失败: {}", e.getMessage(), e);
        }
    }
    
    // Playback 菜单
    private void handlePlaybackRange() {
        logger.info("[MenuBar] Playback Range");
        // TODO: 实现播放范围设置
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
    
    private void handleUIScale() {
        logger.info("[MenuBar] UI Scale");
        // TODO: 实现 UI 缩放功能
    }
    
    private void handleThemeSwitch(String theme) {
        logger.info("[MenuBar] Theme Switch: {}", theme);
        if ("Dark".equals(theme)) {
            UITheme.applyDarkTheme();
        } else if ("Light".equals(theme)) {
            // TODO: 实现浅色主题
            logger.warn("[MenuBar] 浅色主题尚未实现");
        }
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
    
    private void handleReportBugFeedback() {
        logger.info("[MenuBar] Report Bug - Feedback");
        // TODO: 打开反馈页面
    }
    
    private void handleReportBugDiscord() {
        logger.info("[MenuBar] Report Bug - Discord");
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://discord.gg/chronoblocks"));
        } catch (Exception e) {
            logger.error("[MenuBar] 打开 Discord 失败: {}", e.getMessage(), e);
        }
    }
    
    private void handleReportBugGitHub() {
        logger.info("[MenuBar] Report Bug - GitHub");
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/ChronoBlocks/chronoblocks/issues"));
        } catch (Exception e) {
            logger.error("[MenuBar] 打开 GitHub 失败: {}", e.getMessage(), e);
        }
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
