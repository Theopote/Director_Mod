package com.chronoblocks.ui;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.client.gizmo.GizmoManager;
import com.chronoblocks.client.renderer.SelectionRenderer;
import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.client.renderer.pipeline.ChronoBlocksRendererRegistry;
import com.chronoblocks.ui.cursor.ImGuiCursorController;
import com.chronoblocks.ui.di.UIConfiguration;
import com.chronoblocks.ui.di.UIConfigurationBuilder;
import com.chronoblocks.ui.imgui.ImGuiRenderer;
import com.chronoblocks.ui.input.InputFocusManager;
import com.chronoblocks.world.snapshot.WorldSnapshotManager;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎥 ChronoBlocksScreen v5.1
 * - 统一清理：cleanupRenderSystems()
 * - 渲染注册表自检：RendererRegistry 状态检查与补挂载
 * - ContainerRenderer 延迟启用：onDisplayed() 中启用、removed()/close() 中关闭
 * - 降噪日志：常规帧日志降至 trace 级
 * - 初始化与退出职责分层：removed() 与 close() 各司其职
 */
public class ChronoBlocksScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksScreen.class);

    private final MinecraftClient client;
    private ChronoBlocksUIManager uiManager;
    
    /**
     * 获取 UIManager 实例（供 Mixin 使用）
     * 
     * @return UIManager 实例，如果未初始化则返回 null
     */
    public ChronoBlocksUIManager getUIManager() {
        return uiManager;
    }
    private boolean initializationFailed = false;
    private boolean isInitialized = false;
    private boolean directorModeActive = false;
    private long lastModeResyncMs = 0L;
    private WorldSnapshotManager snapshotManager;

    public ChronoBlocksScreen() {
        super(Text.literal("ChronoBlocks"));
        this.client = MinecraftClient.getInstance();
        LOGGER.info("[ChronoBlocks] 创建新的 ChronoBlocksScreen");

        InputFocusManager.getInstance().setChronoBlocksScreenActive(true);

        ChronoBlocksClientController.getInstance().activateChronoBlocksMode();

        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
            LOGGER.info("[ChronoBlocks] 鼠标光标已解锁");
        }
    }

    @Override
    protected void init() {
        super.init();
        
        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] 🖱️ 鼠标光标已解锁");
            }
        }

        if (isInitialized) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] 已初始化，跳过");
            }
            return;
        }

        if (!ImGuiRenderer.getInstance().isInitialized()) {
            LOGGER.error("[ChronoBlocks] ImGui 未初始化，UI 无法显示");
            initializationFailed = true;
            return;
        }

        try {
            com.chronoblocks.ui.imgui.ImGuiStateChecker.forceCleanupStack("ChronoBlocksScreen.init 清理");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理 ImGui 状态时发生错误", e);
        }

        try {
            UIConfiguration config = UIConfigurationBuilder.builder()
                .withMinecraftClient(client)
                .withDefaultPanels()
                .build();
            this.uiManager = config.createUIManager();
            this.isInitialized = true;
            LOGGER.info("[ChronoBlocks] UI 管理器初始化成功");
            
            // 确保加载当前世界的布局（在UI初始化后，此时世界已加载）
            try {
                com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                    com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
                layoutManager.reloadForCurrentWorld("ChronoBlocksScreen.init()");
                LOGGER.info("[ChronoBlocks] 已同步当前世界的布局配置");
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 同步世界布局失败: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] UI 管理器初始化失败: {}", e.getMessage(), e);
            this.uiManager = null;
            initializationFailed = true;
        }

        try {
            initializeSelectionSystem();
            LOGGER.info("[ChronoBlocks] 选择工具系统初始化完成");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 选择工具系统初始化失败", e);
        }
    }

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        LOGGER.info("[ChronoBlocks] 🎬 进入导演模式界面");
        enterDirectorMode();
        ChronoBlocksRendererRegistry rendererRegistry = ChronoBlocksRendererRegistry.get();
        if (!rendererRegistry.isInitialized()) {
            LOGGER.warn("[ChronoBlocks] 渲染注册表未激活，尝试重新初始化");
            rendererRegistry.ensureInitialized();
        }
        // ContainerRenderer 已删除
        
        // 激活 UI 相关快捷键上下文
        try {
            com.chronoblocks.ui.input.ShortcutInitializer.activateUIContexts();
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 激活 UI 快捷键上下文失败", e);
        }
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (initializationFailed || !isInitialized) {
            renderErrorScreen(context, mouseX, mouseY, delta);
            return;
        }

        try {
            // 获取ImGui渲染器实例
            ImGuiRenderer imGuiRenderer = ImGuiRenderer.getInstance();

            // 更新显示尺寸（处理全屏切换等）
            imGuiRenderer.updateDisplaySize();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] Display size updated");
            }

            // 开始新的ImGui帧
            imGuiRenderer.beginFrame();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] ImGui frame started");
            }

            // 渲染UI管理器（包含DockSpace）
            if (uiManager != null) {
            InputFocusManager.getInstance().beginFrame();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] Rendering UI manager...");
            }
            uiManager.render();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] UI manager rendered successfully");
            }
            } else {
                LOGGER.warn("[ChronoBlocks] UIManager为空，跳过UI渲染");
            }

            // 更新输入焦点管理器状态
            InputFocusManager.getInstance().update();

            // 移除调试状态栏显示（FPS、Panels、Frame#已在工具栏和状态栏显示）
            // renderDebugStatusBar();

            // ✅ 应用 ImGui 光标状态（来自 BoxSelectTool 等工具）
            ImGuiCursorController.get().applyToImGui();

            // 结束ImGui帧
            imGuiRenderer.endFrame();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] ImGui frame ended");
            }

        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] UI渲染异常", e);
            
            // 在异常情况下强制清理ImGui状态
            try {
                com.chronoblocks.ui.imgui.ImGuiStateChecker.forceCleanupStack("ChronoBlocksScreen异常恢复");
            } catch (Exception cleanupException) {
                LOGGER.error("[ChronoBlocks] 清理ImGui状态时发生异常", cleanupException);
            }
        }
    }


    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 禁用ESC键关闭，使用工具栏关闭按钮
    }
    
    /**
     * 控制当打开ChronoBlocks界面时游戏是否暂停
     * 返回false确保游戏继续运行，允许"幽灵相机"模式工作
     */
    @Override
    public boolean shouldPause() {
        return false; // 不暂停游戏，让游戏继续tick
    }
    
    /**
     * 处理键盘输入事件
     * 使用全局快捷键管理器统一处理所有快捷键
     */
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();

        // ⚠️ 注意：KeyboardMixin 已经处理了快捷键事件并 cancel 了
        // 如果 KeyboardMixin 正常工作，这个方法可能不会被调用
        // 但为了安全起见，我们仍然在这里处理（防抖机制会防止重复处理）
        
        // ⭐ 优先使用新的 ShortcutDispatcher（Facade 架构）
        // 无论 ImGui 是否捕获键盘，都先尝试新的系统
        // ⚠️ 注意：由于 KeyboardMixin 已经处理了事件，这里可能不会被调用
        // 但如果被调用，防抖机制会确保不会重复处理
        if (uiManager != null) {
            var shortcutDispatcher = uiManager.getShortcutDispatcher();
            if (shortcutDispatcher != null) {
                // 使用 ShortcutDispatcher 处理（只处理按下事件）
                // 注意：ShortcutDispatcher 内部会检查上下文和文本输入状态，并有防抖机制
                shortcutDispatcher.handleGLFWKeyEvent(keyCode, org.lwjgl.glfw.GLFW.GLFW_PRESS, modifiers);
                // 注意：ShortcutDispatcher 不返回是否已处理，因为它只处理已绑定的快捷键
                // 如果快捷键被处理，会在 ShortcutActions.dispatch() 中执行
            } else {
                // 调试：记录 ShortcutDispatcher 为 null 的情况
                if (org.slf4j.LoggerFactory.getLogger(ChronoBlocksScreen.class).isDebugEnabled()) {
                    org.slf4j.LoggerFactory.getLogger(ChronoBlocksScreen.class)
                        .debug("[ChronoBlocksScreen] ShortcutDispatcher 为 null，无法处理快捷键: key={}", keyCode);
                }
            }
        } else {
            // 调试：记录 uiManager 为 null 的情况
            if (org.slf4j.LoggerFactory.getLogger(ChronoBlocksScreen.class).isDebugEnabled()) {
                org.slf4j.LoggerFactory.getLogger(ChronoBlocksScreen.class)
                    .debug("[ChronoBlocksScreen] uiManager 为 null，无法处理快捷键: key={}", keyCode);
            }
        }

        // 检查ImGui是否想要捕获键盘事件
        if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) {
            // ImGui 捕获键盘时，使用旧的 GlobalShortcutManager 作为后备
            // （用于处理尚未迁移到新系统的快捷键）
            var shortcutManager = com.chronoblocks.ui.input.GlobalShortcutManager.getInstance();
            if (shortcutManager.handleImGuiInput(ImGui.getIO())) {
                return true; // 已处理
            }
            return super.keyPressed(input);
        }
        
        // ImGui 不捕获键盘时，使用 GLFW 输入处理（保留旧系统作为后备）
        var shortcutManager = com.chronoblocks.ui.input.GlobalShortcutManager.getInstance();
        boolean ctrlPressed = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftPressed = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        boolean altPressed = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0;
        return shortcutManager.handleGLFWInput(keyCode, ctrlPressed, shiftPressed, altPressed); // 已处理，阻止游戏处理
// 不拦截游戏按键，让游戏继续处理
    }

    // ⚠️ getModifiers() 方法已移除
    // 快捷键处理已移到 keyPressed() 方法开头，确保优先处理

    /**
     * 处理鼠标点击事件
     * 根据鼠标位置决定是否处理UI事件
     */
    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        // 检查ImGui是否想要捕获鼠标事件
        if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse()) {
            return super.mouseClicked(click, doubleClick);
        }
        return false; // 不拦截游戏鼠标事件
    }
    
    /**
     * 处理鼠标移动事件
     * 允许ImGui处理鼠标移动
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse()) {
            super.mouseMoved(mouseX, mouseY);
        }
    }
    
    @Override
    public void removed() {
        super.removed();
        LOGGER.info("[ChronoBlocks] 🧭 退出导演模式界面");
        exitDirectorMode();
        cleanupRenderSystems(false);
        InputFocusManager.getInstance().setChronoBlocksScreenActive(false);
        
        // ⭐ 修复：不再在这里通知 CameraController，由 close() 统一处理
        // 避免重复调用导致状态混乱
        
        // 停用 UI 相关快捷键上下文
        try {
            com.chronoblocks.ui.input.ShortcutInitializer.deactivateUIContexts();
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 停用 UI 快捷键上下文失败", e);
        }
        
        LOGGER.info("[ChronoBlocks] ChronoBlocksScreen 已关闭，恢复游戏输入模式");
    }
    
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    /**
     * 检查鼠标是否在ChronoBlocks界面上
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @return 如果鼠标在界面上返回true，否则返回false
     */
    public boolean isMouseOverChronoBlocksGui(double mouseX, double mouseY) {
        // 直接使用ImGui的鼠标捕获检测
        // ImGui会自动检测鼠标是否在任何ImGui窗口或控件上
        return ImGui.getIO().getWantCaptureMouse();
    }

    /**
     * 重写renderBackground方法，阻止Minecraft Screen基类渲染半透明背景
     * 这是解决半透明背景问题的关键修复
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 完全禁用背景渲染，让Minecraft世界可见
        // 不再渲染任何背景颜色
    }

    /**
     * 窗口尺寸更新（防止每帧更新）
     */
    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[ChronoBlocks] 窗口大小变更: {}x{}", width, height);
        }
        ImGuiRenderer.getInstance().updateDisplaySize();
    }

    /**
     * 初始化选择工具系统
     */
    private void initializeSelectionSystem() {
        LOGGER.info("[ChronoBlocks] 初始化选择工具系统...");

        ChronoBlocksRendererRegistry registry = ChronoBlocksRendererRegistry.get();
        if (!registry.isInitialized()) {
            LOGGER.warn("[ChronoBlocks] ⚠️ 渲染注册表未初始化，正在初始化...");
            registry.ensureInitialized();
        }

        com.chronoblocks.core.selection.SelectionManager selectionManager =
            com.chronoblocks.core.selection.SelectionManager.get();
        if (selectionManager == null) {
            LOGGER.error("[ChronoBlocks] SelectionManager 为空，无法初始化");
            return;
        }

        if (!selectionManager.hasCurrentTool() && client.world != null) {
            // 统一入口：通过 ToolRegistry 激活默认工具，避免多处直接 setCurrentTool
            com.chronoblocks.ui.tools.ToolRegistry.get().activate("box");
            if (selectionManager.hasCurrentTool()) {
                LOGGER.info("[ChronoBlocks] ✅ 当前工具: {}",
                    selectionManager.getCurrentToolDisplayName());
            } else {
                LOGGER.warn("[ChronoBlocks] ⚠️ 设置默认工具失败");
            }
        } else if (selectionManager.hasCurrentTool() && LOGGER.isTraceEnabled()) {
            LOGGER.trace("[ChronoBlocks] 当前工具: {}",
                selectionManager.getCurrentToolDisplayName());
        }

        try {
            SelectionRenderer.getInstance().setEnabled(true);
        } catch (Throwable ignored) {
            // 由渲染注册表托管，忽略
        }
    }
    
    @Override
    public void close() {
        // 关闭 UI：必须强制停用（否则 controller 会因 UI 打开而忽略 soft-deactivate）
        ChronoBlocksClientController.getInstance().forceDeactivateChronoBlocksMode();
        
        // ⭐ 单一摄像机系统：关闭窗口时通知时间线控制器停止驱动摄像机
        try {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
            LOGGER.info("[ChronoBlocks] 已通知 TimelineCameraController 关闭窗口");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 通知 TimelineCameraController 关闭窗口时出错", e);
        }
        
        // ⭐ 时间轨道系统：关闭窗口时清理时间控制器状态
        try {
            com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance().clear();
            LOGGER.info("[ChronoBlocks] 已清理 TimelineWorldController 状态");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理 TimelineWorldController 状态时出错", e);
        }
        
        // ⭐ 季节轨道系统：关闭窗口时清理季节控制器状态
        try {
            com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance().clear();
            LOGGER.info("[ChronoBlocks] 已清理 TimelineSeasonController 状态");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理 TimelineSeasonController 状态时出错", e);
        }
        
        // ⭐ 天气轨道系统：关闭窗口时清理天气控制器状态
        try {
            com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance().clear();
            LOGGER.info("[ChronoBlocks] 已清理 TimelineWeatherController 状态");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理 TimelineWeatherController 状态时出错", e);
        }
        
        // 季节效果恢复已由 forceDeactivateChronoBlocksMode 统一负责，避免重复执行
        
        cleanupRenderSystems(true);

        if (client != null && client.mouse != null) {
            client.mouse.lockCursor();
            LOGGER.info("[ChronoBlocks] 鼠标光标已锁定，恢复游戏状态");
        }

        // 在关闭前保存当前世界的布局
        try {
            com.chronoblocks.ui.layout.LayoutPersistenceManager layoutManager = 
                com.chronoblocks.ui.layout.LayoutPersistenceManager.getInstance();
            layoutManager.saveLayout();
            layoutManager.saveImGuiIni();
            LOGGER.info("[ChronoBlocks] 已保存当前世界的布局配置");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 保存布局配置失败: {}", e.getMessage(), e);
        }

        // ⭐ 关闭前兜底保存：确保“关 UI / 关游戏”都能恢复到上次工作区
        try {
            com.chronoblocks.core.container.persistence.ContainerPersistence.saveForCurrentWorld(
                com.chronoblocks.core.container.ContainerManager.getInstance()
            );
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 保存容器数据失败: {}", e.getMessage(), e);
        }
        try {
            if (uiManager != null) {
                com.chronoblocks.ui.panels.TimelinePanel timelinePanel =
                    uiManager.getPanelRegistry().getPanel(com.chronoblocks.ui.panels.TimelinePanel.class);
                if (timelinePanel != null) {
                    timelinePanel.saveNow();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 保存时间线数据失败: {}", e.getMessage(), e);
        }
        
        if (uiManager != null) {
            try {
                uiManager.dispose();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[ChronoBlocks] UI 管理器资源已释放");
                }
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 释放UI管理器资源时发生错误", e);
            } finally {
                uiManager = null; // 确保引用被清空
            }
        }

        try {
            com.chronoblocks.ui.imgui.ImGuiStateChecker.forceCleanupStack("ChronoBlocksScreen.close 清理");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理 ImGui 状态时发生错误", e);
        }

        try {
            com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ChronoBlocks] DockSpace 布局状态已重置");
            }
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 重置DockSpace布局状态时发生错误", e);
        }

        initializationFailed = false;
        isInitialized = false;

        super.close();
    }
    
    /**
     * 渲染错误屏幕
     * 当ImGui初始化失败或UI管理器初始化失败时显示
     */
    private void renderErrorScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染半透明背景
        context.fill(0, 0, width, height, 0x80000000);
        
        // 渲染错误信息
        String errorText = "ChronoBlocks UI 初始化失败";
        String detailText = "请检查日志获取详细信息";
        String retryText = "按ESC键关闭此屏幕";
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // 绘制错误信息
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            Text.literal(errorText),
            centerX,
            centerY - 20,
            0xFF0000
        );
        
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            Text.literal(detailText),
            centerX,
            centerY,
            0xFFFFFF
        );
        
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            Text.literal(retryText),
            centerX,
            centerY + 20,
            0xAAAAAA
        );
    }
    
    /**
     * 🎬 进入导演模式
     * 直接集成到ChronoBlocksScreen中，无需额外的启动按钮
     */
    private void enterDirectorMode() {
        if (directorModeActive) {
            LOGGER.warn("[ChronoBlocks] 导演模式已经激活");
            return;
        }

        if (client == null || client.world == null) {
            LOGGER.error("[ChronoBlocks] 无法进入导演模式：世界未加载");
            return;
        }

        LOGGER.info("[ChronoBlocks] 🎬 进入导演模式...");
        directorModeActive = true;

        try {
            // 1️⃣ 冻结世界时间与实体
            freezeWorld();

            // 2️⃣ 创建世界快照
            snapshotManager = new WorldSnapshotManager(client.world);
            snapshotManager.captureSnapshot();
            LOGGER.info("[ChronoBlocks] 世界快照已创建");

            LOGGER.info("[ChronoBlocks] ✅ 导演模式设置完成！");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 进入导演模式失败", e);
            directorModeActive = false;
        }
    }

    /**
     * 🧭 退出导演模式 v2.0
     */
    private void exitDirectorMode() {
        if (!directorModeActive) {
            return;
        }

        LOGGER.info("[ChronoBlocks] 🧭 退出导演模式...");

        try {
            if (client.world != null) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[ChronoBlocks] 实体保持自由移动");
                }

                // 真正退出导演模式：强制停用（恢复输入/清季节效果）
                ChronoBlocksClientController.getInstance().forceDeactivateChronoBlocksMode();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[ChronoBlocks] 时间流动恢复");
                }

                // 3️⃣ 恢复世界快照（方块状态）
                if (snapshotManager != null) {
                    snapshotManager.restoreSnapshot();
                    LOGGER.info("[ChronoBlocks] 世界快照恢复完成");
                }
            }

            directorModeActive = false;
            LOGGER.info("[ChronoBlocks] ✅ 导演模式退出完成");

        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 退出导演模式失败", e);
        }
    }

    /**
     * 🌍 冻结世界 v2.2
     * 暂停世界时间流逝，让植物停止生长
     * 注意：不冻结实体，让所有实体（包括动物）自由移动，避免颤抖和闪烁问题
     */
    private void freezeWorld() {
        try {
            if (client.world != null) {
                // 🔹 1️⃣ 固定当前时间（通过World直接设置）
                long fixedTime = client.world.getTimeOfDay();
                client.world.setTime(fixedTime, fixedTime, false);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[ChronoBlocks] 世界时间已冻结 (tick={})", fixedTime);
                    LOGGER.trace("[ChronoBlocks] 实体保持自由移动，避免颤抖和闪烁");
                }

                // 🔹 3️⃣ 启用 mixin 拦截（tickTime / doRandomBlockDisplayTicks）
                // 通过ClientWorldMixin拦截世界时间更新，实现时间冻结
                ChronoBlocksClientController.getInstance().activateChronoBlocksMode();

                LOGGER.info("[ChronoBlocks] 🌍 世界冻结完成 - 时间已冻结，实体自由移动");
            }
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 冻结世界失败", e);
        }
    }

    /**
     * 生命周期安全检查（failsafe）
     * 防止模式状态不同步（UI开着但controller未激活）
     */
    @Override
    public void tick() {
        super.tick();

        com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
            .forceDisableVanillaWeather();

        // failsafe：节流，避免异常状态下每 tick 打日志/反复激活
        if (directorModeActive && !ChronoBlocksClientController.getInstance().isChronoBlocksModeActive()) {
            long now = System.currentTimeMillis();
            if (now - lastModeResyncMs > 2000L) {
                lastModeResyncMs = now;
                LOGGER.warn("[ChronoBlocks] 检测到模式状态不同步，重新激活 ChronoBlocks 模式");
                ChronoBlocksClientController.getInstance().activateChronoBlocksMode();
            }
        }

        ChronoBlocksRendererRegistry registry = ChronoBlocksRendererRegistry.get();
        if (!registry.isInitialized()) {
            LOGGER.warn("[ChronoBlocks] 渲染注册表失效，尝试补挂载");
            registry.ensureInitialized();
        }

        // ⭐ Actor 选择系统：每帧更新点击检测
        com.chronoblocks.ui.actor.pick.ActorPickController.getInstance().update();

        // ⭐ Actor 放置系统：每帧更新（鼠标附着预览和右键放置）
        com.chronoblocks.ui.actor.placement.ActorPlacementController.getInstance().update();

        // ⭐ Actor 路径工具：每帧更新（路径点采集与预览）
        com.chronoblocks.ui.actor.path.ActorPathController.getInstance().update();

        // ⭐ Actor LookAt Gizmo 系统：每帧更新（LOOK_AT 模式下处理世界点击）
        com.chronoblocks.ui.actor.lookat.ActorLookAtGizmoController.getInstance().update();

        // ⭐ Marker 拖拽系统：每帧更新（LOOK_AT 模式下拖拽 Marker）
        com.chronoblocks.ui.actor.lookat.MarkerDragController.getInstance().update();
        
        // ⚠️ 播放系统 tick 已迁移到 PlaybackOrchestrator（在 UIManager.updateAnimationSystem() 中调用）
        // 这确保了播放系统在任何情况下（面板隐藏/失焦）都保持一致
        // 播放驱动现在由 UIManager 统一管理，不再依赖 ChronoBlocksScreen.tick()
    }

    private void cleanupRenderSystems(boolean full) {
        try {
            SelectionRenderer.getInstance().clearAll();
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理 SelectionRenderer 状态失败", e);
        }
        try {
            ToolPreviewManager.get().clearPreview();
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理工具预览失败", e);
        }
        // ContainerRenderer 已删除
        try {
            GizmoManager.get().deactivate();
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 停用 Gizmo 失败", e);
        }

        if (full) {
            try {
                com.chronoblocks.core.selection.SelectionManager.get().clearSelection();
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 清除选区失败", e);
            }
        }
    }
}
