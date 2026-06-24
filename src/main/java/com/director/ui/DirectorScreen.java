package com.director.ui;

import com.director.core.DirectorServices;
import com.director.client.DirectorClientController;
import com.director.client.RenderOverrideSnapshots;
import com.director.client.gizmo.GizmoManager;
import com.director.client.renderer.SelectionRenderer;
import com.director.client.renderer.ToolPreviewManager;
import com.director.client.renderer.pipeline.DirectorRendererRegistry;
import com.director.ui.cursor.ImGuiCursorController;
import com.director.ui.di.UIConfiguration;
import com.director.ui.di.UIConfigurationBuilder;
import com.director.ui.imgui.DirectorStyleScope;
import com.director.ui.imgui.ImGuiRenderer;
import com.director.ui.input.InputFocusManager;
import com.director.world.snapshot.WorldSnapshotManager;
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
 * 🎥 DirectorScreen v5.1
 * - 统一清理：cleanupRenderSystems()
 * - 渲染注册表自检：RendererRegistry 状态检查与补挂载
 * - ContainerRenderer 延迟启用：onDisplayed() 中启用、removed()/close() 中关闭
 * - 降噪日志：常规帧日志降至 trace 级
 * - 初始化与退出职责分层：removed() 与 close() 各司其职
 */
public class DirectorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorScreen.class);

    private final MinecraftClient client;
    private DirectorUIManager uiManager;
    
    /**
     * 获取 UIManager 实例（供 KeyboardMixin 分发快捷键）。
     *
     * @return UIManager 实例，如果未初始化则返回 null
     */
    public DirectorUIManager getUIManager() {
        return uiManager;
    }
    private boolean initializationFailed = false;
    private boolean isInitialized = false;
    private boolean directorModeActive = false;
    private long lastModeResyncMs = 0L;
    private boolean closeCleanupDone = false;
    private boolean detachedWindowClosePending = false;
    private WorldSnapshotManager snapshotManager;
    private UISessionState uiSessionState;
    private static volatile PendingRestoreState pendingRestoreState;

    private static final class UISessionState {
        private final com.director.timeline.playback.camera.CameraSample cameraSample;
        private final long worldTime;
        private final long worldTimeOfDay;
        private final float rainGradient;
        private final float thunderGradient;
        private final boolean raining;
        private final Long serverWorldTimeOfDay;
        private final Boolean serverDoDaylightCycle;

        private UISessionState(
            com.director.timeline.playback.camera.CameraSample cameraSample,
            long worldTime,
            long worldTimeOfDay,
            float rainGradient,
            float thunderGradient,
            boolean raining,
            Long serverWorldTimeOfDay,
            Boolean serverDoDaylightCycle
        ) {
            this.cameraSample = cameraSample;
            this.worldTime = worldTime;
            this.worldTimeOfDay = worldTimeOfDay;
            this.rainGradient = rainGradient;
            this.thunderGradient = thunderGradient;
            this.raining = raining;
            this.serverWorldTimeOfDay = serverWorldTimeOfDay;
            this.serverDoDaylightCycle = serverDoDaylightCycle;
        }
    }

    private static final class PendingRestoreState {
        private final long worldTime;
        private final long worldTimeOfDay;
        private final Long serverWorldTimeOfDay;
        private final Boolean serverDoDaylightCycle;
        private int ticksRemaining;

        private PendingRestoreState(
            long worldTime,
            long worldTimeOfDay,
            Long serverWorldTimeOfDay,
            Boolean serverDoDaylightCycle,
            int ticksRemaining
        ) {
            this.worldTime = worldTime;
            this.worldTimeOfDay = worldTimeOfDay;
            this.serverWorldTimeOfDay = serverWorldTimeOfDay;
            this.serverDoDaylightCycle = serverDoDaylightCycle;
            this.ticksRemaining = ticksRemaining;
        }
    }

    public DirectorScreen() {
        super(Text.literal("Director"));
        this.client = MinecraftClient.getInstance();
        LOGGER.info("[Director] 创建新的 DirectorScreen");
    }

    @Override
    protected void init() {
        super.init();
        
        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] 🖱️ 鼠标光标已解锁");
            }
        }

        if (isInitialized) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] 已初始化，跳过");
            }
            return;
        }

        if (!DirectorServices.get().imgui().isInitialized()) {
            LOGGER.error("[Director] ImGui 未初始化，UI 无法显示");
            initializationFailed = true;
            return;
        }

        try {
            com.director.ui.imgui.ImGuiStateChecker.forceCleanupStack("DirectorScreen.init 清理");
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理 ImGui 状态时发生错误", e);
        }

        try {
            UIConfiguration config = UIConfigurationBuilder.builder()
                .withMinecraftClient(client)
                .withDefaultPanels()
                .build();
            this.uiManager = config.createUIManager();
            this.isInitialized = true;
            LOGGER.info("[Director] UI 管理器初始化成功");
            
            // 确保加载当前世界的布局（在UI初始化后，此时世界已加载）
            try {
                com.director.ui.layout.LayoutPersistenceManager layoutManager = 
                    com.director.ui.layout.LayoutPersistenceManager.getInstance();
                layoutManager.reloadForCurrentWorld("DirectorScreen.init()");
                LOGGER.info("[Director] 已同步当前世界的布局配置");
            } catch (Exception e) {
                LOGGER.warn("[Director] 同步世界布局失败: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            LOGGER.error("[Director] UI 管理器初始化失败: {}", e.getMessage(), e);
            this.uiManager = null;
            initializationFailed = true;
        }

        try {
            initializeSelectionSystem();
            LOGGER.info("[Director] 选择工具系统初始化完成");
        } catch (Exception e) {
            LOGGER.error("[Director] 选择工具系统初始化失败", e);
        }
    }

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        LOGGER.info("[Director] 🎬 进入导演模式界面");
        activateScreenState();
        enterDirectorMode();
        DirectorRendererRegistry rendererRegistry = DirectorRendererRegistry.get();
        if (!rendererRegistry.isInitialized()) {
            LOGGER.warn("[Director] 渲染注册表未激活，尝试重新初始化");
            rendererRegistry.ensureInitialized();
        }
        // ContainerRenderer 已删除
        
        // 激活 UI 相关快捷键上下文
        try {
            com.director.ui.input.ShortcutInitializer.activateUIContexts();
        } catch (Exception e) {
            LOGGER.warn("[Director] 激活 UI 快捷键上下文失败", e);
        }
    }

    private void activateScreenState() {
        InputFocusManager.getInstance().setDirectorScreenActive(true);
        DirectorClientController.getInstance().activateDirectorMode();

        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
            LOGGER.info("[Director] 鼠标光标已解锁");
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
            ImGuiRenderer imGuiRenderer = DirectorServices.get().imgui();
            if (imGuiRenderer.consumeDetachedWindowCloseRequest(this)) {
                // 系统关闭按钮（外置窗口 X）触发时，在主路径安全关闭 detached window，
                // 避免在 detached 渲染上下文内直接销毁原生窗口。
                imGuiRenderer.closeDetachedDirectorWindow(this);
                detachedWindowClosePending = true;
                return;
            }

            // 更新显示尺寸（处理全屏切换等）
            imGuiRenderer.updateDisplaySize();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] Display size updated");
            }

            // 开始新的ImGui帧
            imGuiRenderer.beginFrame();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] ImGui frame started");
            }

            // 渲染UI管理器（包含DockSpace）- 使用 DirectorStyleScope 临时应用样式，渲染后 pop 恢复，避免影响 Treefactory 等模组
            if (uiManager != null) {
            InputFocusManager.getInstance().beginFrame();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] Rendering UI manager...");
            }
            try (DirectorStyleScope scope = DirectorStyleScope.enter()) {
                uiManager.render();
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] UI manager rendered successfully");
            }
            } else {
                LOGGER.warn("[Director] UIManager为空，跳过UI渲染");
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
                LOGGER.trace("[Director] ImGui frame ended");
            }

        } catch (Exception e) {
            LOGGER.error("[Director] UI渲染异常", e);
            
            // 在异常情况下强制清理ImGui状态
            try {
                com.director.ui.imgui.ImGuiStateChecker.forceCleanupStack("DirectorScreen异常恢复");
            } catch (Exception cleanupException) {
                LOGGER.error("[Director] 清理ImGui状态时发生异常", cleanupException);
            }
        }
    }


    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 禁用ESC键关闭，使用工具栏关闭按钮
    }
    
    /**
     * 控制当打开Director界面时游戏是否暂停
     * 返回false确保游戏继续运行，允许"幽灵相机"模式工作
     */
    @Override
    public boolean shouldPause() {
        return false; // 不暂停游戏，让游戏继续tick
    }
    
    /**
     * Screen 层键盘事件：仅将 ImGui 文本输入交给父类，快捷键由 {@link com.director.mixin.KeyboardMixin} 统一分发。
     */
    @Override
    public boolean keyPressed(KeyInput input) {
        if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) {
            return super.keyPressed(input);
        }
        return false;
    }

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
        performCloseCleanup("removed");
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
     * 检查鼠标是否在Director界面上
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @return 如果鼠标在界面上返回true，否则返回false
     */
    public boolean isMouseOverDirectorGui(double mouseX, double mouseY) {
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
            LOGGER.trace("[Director] 窗口大小变更: {}x{}", width, height);
        }
        DirectorServices.get().imgui().updateDisplaySize();
    }

    /**
     * 初始化选择工具系统
     */
    private void initializeSelectionSystem() {
        LOGGER.info("[Director] 初始化选择工具系统...");

        DirectorRendererRegistry registry = DirectorRendererRegistry.get();
        if (!registry.isInitialized()) {
            LOGGER.warn("[Director] ⚠️ 渲染注册表未初始化，正在初始化...");
            registry.ensureInitialized();
        }

        com.director.core.selection.SelectionManager selectionManager =
            DirectorServices.get().selection();
        if (selectionManager == null) {
            LOGGER.error("[Director] SelectionManager 为空，无法初始化");
            return;
        }

        if (!selectionManager.hasCurrentTool() && client.world != null) {
            // 统一入口：通过 ToolRegistry 激活默认工具，避免多处直接 setCurrentTool
            com.director.ui.tools.ToolRegistry.get().activate("box");
            if (selectionManager.hasCurrentTool()) {
                LOGGER.info("[Director] ✅ 当前工具: {}",
                    selectionManager.getCurrentToolDisplayName());
            } else {
                LOGGER.warn("[Director] ⚠️ 设置默认工具失败");
            }
        } else if (selectionManager.hasCurrentTool() && LOGGER.isTraceEnabled()) {
            LOGGER.trace("[Director] 当前工具: {}",
                selectionManager.getCurrentToolDisplayName());
        }

        SelectionRenderer.getInstance().setEnabled(true);
    }
    
    @Override
    public void close() {
        performCloseCleanup("close");
        super.close();
    }

    private void performCloseCleanup(String source) {
        if (closeCleanupDone) {
            return;
        }
        closeCleanupDone = true;
        com.director.client.DirectorClient.markDirectorScreenClosed();

        try {
            DirectorServices.get().imgui().closeDetachedDirectorWindow(this);
        } catch (Exception e) {
            LOGGER.warn("[Director] 关闭外置 Director 窗口失败", e);
        }

        // 样式隔离已改为 DirectorStyleScope（每帧 push/pop），不再需要上下文切换或恢复

        LOGGER.info("[Director] 🧭 退出导演模式界面 (source={})", source);
        exitDirectorMode();

        // 关闭 UI：必须强制停用（否则 controller 会因 UI 打开而忽略 soft-deactivate）
        DirectorClientController.getInstance().forceDeactivateDirectorMode();

        // ⭐ 单一摄像机系统：关闭窗口时通知时间线控制器停止驱动摄像机
        try {
            com.director.timeline.playback.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
            LOGGER.info("[Director] 已通知 TimelineCameraController 关闭窗口");
        } catch (Exception e) {
            LOGGER.warn("[Director] 通知 TimelineCameraController 关闭窗口时出错", e);
        }

        // ⭐ 时间轨道系统：关闭窗口时清理时间控制器状态
        try {
            com.director.timeline.playback.time.TimelineWorldController.getInstance().clear();
            LOGGER.info("[Director] 已清理 TimelineWorldController 状态");
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理 TimelineWorldController 状态时出错", e);
        }

        // ⭐ 季节轨道系统：关闭窗口时清理季节控制器状态
        try {
            com.director.timeline.playback.season.TimelineSeasonController.getInstance().clear();
            LOGGER.info("[Director] 已清理 TimelineSeasonController 状态");
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理 TimelineSeasonController 状态时出错", e);
        }

        // ⭐ 天气轨道系统：关闭窗口时清理天气控制器状态
        try {
            com.director.timeline.playback.weather.TimelineWeatherController.getInstance().clear();
            LOGGER.info("[Director] 已清理 TimelineWeatherController 状态");
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理 TimelineWeatherController 状态时出错", e);
        }

        RenderOverrideSnapshots.clearAll();

        cleanupRenderSystems(true);
        InputFocusManager.getInstance().setDirectorScreenActive(false);

        // 停用 UI 相关快捷键上下文
        try {
            com.director.ui.input.ShortcutInitializer.deactivateUIContexts();
        } catch (Exception e) {
            LOGGER.warn("[Director] 停用 UI 快捷键上下文失败", e);
        }

        if (client != null && client.mouse != null) {
            client.mouse.lockCursor();
            LOGGER.info("[Director] 鼠标光标已锁定，恢复游戏状态");
        }

        // 在关闭前保存当前世界的布局
        try {
            com.director.ui.layout.LayoutPersistenceManager layoutManager =
                com.director.ui.layout.LayoutPersistenceManager.getInstance();
            layoutManager.saveLayout();
            layoutManager.saveImGuiIni();
            LOGGER.info("[Director] 已保存当前世界的布局配置");
        } catch (Exception e) {
            LOGGER.warn("[Director] 保存布局配置失败: {}", e.getMessage(), e);
        }

        // ⭐ 关闭前兜底保存：确保“关 UI / 关游戏”都能恢复到上次工作区
        if (!com.director.core.container.persistence.ContainerPersistence.saveForCurrentWorld(
                DirectorServices.get().containers())) {
            LOGGER.warn("[Director] 关闭前保存容器数据失败");
        }
        try {
            if (uiManager != null) {
                com.director.ui.panels.TimelinePanel timelinePanel =
                    uiManager.getPanelRegistry().getPanel(com.director.ui.panels.TimelinePanel.class);
                if (timelinePanel != null && !timelinePanel.saveNow()) {
                    LOGGER.warn("[Director] 关闭前保存时间线数据失败");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Director] 保存时间线数据失败: {}", e.getMessage(), e);
        }

        if (uiManager != null) {
            try {
                uiManager.dispose();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[Director] UI 管理器资源已释放");
                }
            } catch (Exception e) {
                LOGGER.warn("[Director] 释放UI管理器资源时发生错误", e);
            } finally {
                uiManager = null;
            }
        }

        try {
            com.director.ui.imgui.ImGuiStateChecker.forceCleanupStack("DirectorScreen.close 清理");
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理 ImGui 状态时发生错误", e);
        }

        try {
            com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Director] DockSpace 布局状态已重置");
            }
        } catch (Exception e) {
            LOGGER.warn("[Director] 重置DockSpace布局状态时发生错误", e);
        }

        initializationFailed = false;
        isInitialized = false;

        LOGGER.info("[Director] DirectorScreen 已关闭，恢复游戏输入模式");
    }
    
    /**
     * 渲染错误屏幕
     * 当ImGui初始化失败或UI管理器初始化失败时显示
     */
    private void renderErrorScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染半透明背景
        context.fill(0, 0, width, height, 0x80000000);
        
        // 渲染错误信息
        String errorText = "Director UI 初始化失败";
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
     * 直接集成到DirectorScreen中，无需额外的启动按钮
     */
    private void enterDirectorMode() {
        if (directorModeActive) {
            LOGGER.warn("[Director] 导演模式已经激活");
            return;
        }

        if (client == null || client.world == null) {
            LOGGER.error("[Director] 无法进入导演模式：世界未加载");
            return;
        }

        LOGGER.info("[Director] 🎬 进入导演模式...");
        directorModeActive = true;

        try {
            captureUiSessionState();

            // 1️⃣ 冻结世界时间与实体
            freezeWorld();

            // 2️⃣ 创建世界快照
            snapshotManager = new WorldSnapshotManager(client.world);
            snapshotManager.captureSnapshot();
            LOGGER.info("[Director] 世界快照已创建");

            LOGGER.info("[Director] ✅ 导演模式设置完成！");
        } catch (Exception e) {
            LOGGER.error("[Director] 进入导演模式失败", e);
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

        LOGGER.info("[Director] 🧭 退出导演模式...");

        try {
            if (client.world != null) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[Director] 实体保持自由移动");
                }

                // 真正退出导演模式：强制停用（恢复输入/清季节效果）
                DirectorClientController.getInstance().forceDeactivateDirectorMode();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[Director] 时间流动恢复");
                }

                // 3️⃣ 恢复世界快照（方块状态）
                if (snapshotManager != null) {
                    snapshotManager.restoreSnapshot();
                    LOGGER.info("[Director] 世界快照恢复完成");
                }

                restoreUiSessionState();
            }

            directorModeActive = false;
            LOGGER.info("[Director] ✅ 导演模式退出完成");

        } catch (Exception e) {
            LOGGER.error("[Director] 退出导演模式失败", e);
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
                    LOGGER.trace("[Director] 世界时间已冻结 (tick={})", fixedTime);
                    LOGGER.trace("[Director] 实体保持自由移动，避免颤抖和闪烁");
                }

                // 🔹 3️⃣ 启用 mixin 拦截（tickTime / doRandomBlockDisplayTicks）
                // 通过ClientWorldMixin拦截世界时间更新，实现时间冻结
                DirectorClientController.getInstance().activateDirectorMode();

                LOGGER.info("[Director] 🌍 世界冻结完成 - 时间已冻结，实体自由移动");
            }
        } catch (Exception e) {
            LOGGER.error("[Director] 冻结世界失败", e);
        }
    }

    private void captureUiSessionState() {
        if (client == null || client.world == null) {
            return;
        }
        try {
            var cameraSample = com.director.timeline.playback.camera.CameraAccessor.sampleFromGameCamera(client);
            long worldTime = client.world.getTime();
            long worldTimeOfDay = client.world.getTimeOfDay();
            float rain = client.world.getRainGradient(1.0f);
            float thunder = client.world.getThunderGradient(1.0f);
            boolean raining = client.world.isRaining();

            Long serverTimeOfDay = null;
            Boolean serverDoDaylightCycle = null;
            var server = client.getServer();
            if (server != null) {
                var serverWorld = server.getOverworld();
                if (serverWorld != null) {
                    serverTimeOfDay = serverWorld.getTimeOfDay();
                    serverDoDaylightCycle = serverWorld.getGameRules()
                        .getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE);
                }
            }

            uiSessionState = new UISessionState(
                cameraSample,
                worldTime,
                worldTimeOfDay,
                rain,
                thunder,
                raining,
                serverTimeOfDay,
                serverDoDaylightCycle
            );
            LOGGER.info(
                "[Director] 已捕获 UI 会话前状态: worldTime={}, timeOfDay={}, rain={}, thunder={}, raining={}, serverTimeOfDay={}, serverDoDaylightCycle={}",
                worldTime, worldTimeOfDay, rain, thunder, raining, serverTimeOfDay, serverDoDaylightCycle
            );
        } catch (Exception e) {
            LOGGER.warn("[Director] 捕获 UI 会话前状态失败", e);
            uiSessionState = null;
        }
    }

    private void restoreUiSessionState() {
        if (uiSessionState == null || client == null || client.world == null) {
            return;
        }
        try {
            // 先恢复相机视角，再恢复世界时间与天气，尽量回到打开 UI 前状态。
            if (uiSessionState.cameraSample != null) {
                com.director.timeline.playback.camera.CameraAccessor.applySampleToPlayerEntity(
                    client, uiSessionState.cameraSample
                );
                com.director.timeline.playback.camera.CameraRuntime.getInstance()
                    .updateCurrentSample(uiSessionState.cameraSample);
            }

            // 单人世界优先恢复服务端权威时间与昼夜规则，避免客户端被下一次同步包覆盖。
            var server = client.getServer();
            if (server != null && uiSessionState.serverWorldTimeOfDay != null) {
                var serverWorld = server.getOverworld();
                if (serverWorld != null) {
                    if (uiSessionState.serverDoDaylightCycle != null) {
                        serverWorld.getGameRules()
                            .get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE)
                            .set(uiSessionState.serverDoDaylightCycle, server);
                    }
                    serverWorld.setTimeOfDay(uiSessionState.serverWorldTimeOfDay);
                }
            }

            client.world.setTime(uiSessionState.worldTime, uiSessionState.worldTimeOfDay, false);
            client.world.setRainGradient(uiSessionState.rainGradient);
            client.world.setThunderGradient(uiSessionState.thunderGradient);
            client.world.getLevelProperties().setRaining(uiSessionState.raining);

            LOGGER.info(
                "[Director] 已恢复 UI 会话前状态: worldTime={}, timeOfDay={}, rain={}, thunder={}, raining={}",
                uiSessionState.worldTime,
                uiSessionState.worldTimeOfDay,
                uiSessionState.rainGradient,
                uiSessionState.thunderGradient,
                uiSessionState.raining
            );

            // 关闭 UI 后做短期二次校正，抵消异步同步把时间再次改写的情况。
            scheduleDeferredRestore(uiSessionState);
        } catch (Exception e) {
            LOGGER.warn("[Director] 恢复 UI 会话前状态失败", e);
        } finally {
            uiSessionState = null;
        }
    }

    private static void scheduleDeferredRestore(UISessionState state) {
        if (state == null) {
            return;
        }
        pendingRestoreState = new PendingRestoreState(
            state.worldTime,
            state.worldTimeOfDay,
            state.serverWorldTimeOfDay,
            state.serverDoDaylightCycle,
            40
        );
    }

    public static void tickPendingRestore(MinecraftClient client) {
        PendingRestoreState state = pendingRestoreState;
        if (state == null || client == null || client.world == null) {
            return;
        }

        if (client.currentScreen instanceof DirectorScreen) {
            return;
        }

        try {
            var server = client.getServer();
            if (server != null && state.serverWorldTimeOfDay != null) {
                var serverWorld = server.getOverworld();
                if (serverWorld != null) {
                    if (state.serverDoDaylightCycle != null) {
                        serverWorld.getGameRules()
                            .get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE)
                            .set(state.serverDoDaylightCycle, server);
                    }
                    serverWorld.setTimeOfDay(state.serverWorldTimeOfDay);
                }
            }
            client.world.setTime(state.worldTime, state.worldTimeOfDay, false);
        } catch (Exception e) {
            LOGGER.debug("[Director] 延迟时间校正失败", e);
        }

        state.ticksRemaining--;
        if (state.ticksRemaining <= 0) {
            pendingRestoreState = null;
        }
    }

    /**
     * 生命周期安全检查（failsafe）
     * 防止模式状态不同步（UI开着但controller未激活）
     */
    @Override
    public void tick() {
        super.tick();

        if (detachedWindowClosePending) {
            detachedWindowClosePending = false;
            if (client != null && client.currentScreen == this) {
                client.setScreen(null);
            }
            return;
        }

        com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
            .forceDisableVanillaWeather();

        // failsafe：节流，避免异常状态下每 tick 打日志/反复激活
        if (directorModeActive && !DirectorClientController.getInstance().isDirectorModeActive()) {
            long now = System.currentTimeMillis();
            if (now - lastModeResyncMs > 2000L) {
                lastModeResyncMs = now;
                LOGGER.warn("[Director] 检测到模式状态不同步，重新激活 Director 模式");
                DirectorClientController.getInstance().activateDirectorMode();
            }
        }

        DirectorRendererRegistry registry = DirectorRendererRegistry.get();
        if (!registry.isInitialized()) {
            LOGGER.warn("[Director] 渲染注册表失效，尝试补挂载");
            registry.ensureInitialized();
        }

        // ⭐ Actor 选择系统：每帧更新点击检测
        com.director.ui.actor.pick.ActorPickController.getInstance().update();

        // ⭐ Actor 放置系统：每帧更新（鼠标附着预览和右键放置）
        com.director.ui.actor.placement.ActorPlacementController.getInstance().update();

        // ⭐ Actor 路径工具：每帧更新（路径点采集与预览）
        com.director.ui.actor.path.ActorPathController.getInstance().update();

        // Camera path edit tool: world-space anchor picking and Gizmo movement.
        com.director.ui.camera.path.CameraPathEditController.getInstance().update();

        // ⭐ Actor LookAt Gizmo 系统：每帧更新（LOOK_AT 模式下处理世界点击）
        com.director.ui.actor.lookat.ActorLookAtGizmoController.getInstance().update();

        // ⭐ Marker 拖拽系统：每帧更新（LOOK_AT 模式下拖拽 Marker）
        com.director.ui.actor.lookat.MarkerDragController.getInstance().update();
        
        // ⚠️ 播放系统 tick 已迁移到 PlaybackOrchestrator（在 UIManager.updateAnimationSystem() 中调用）
        // 这确保了播放系统在任何情况下（面板隐藏/失焦）都保持一致
        // 播放驱动现在由 UIManager 统一管理，不再依赖 DirectorScreen.tick()
    }

    private void cleanupRenderSystems(boolean full) {
        try {
            SelectionRenderer.getInstance().clearAll();
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理 SelectionRenderer 状态失败", e);
        }
        try {
            ToolPreviewManager.getInstance().clearPreview();
        } catch (Exception e) {
            LOGGER.warn("[Director] 清理工具预览失败", e);
        }
        // ContainerRenderer 已删除
        try {
            GizmoManager.get().deactivate();
        } catch (Exception e) {
            LOGGER.warn("[Director] 停用 Gizmo 失败", e);
        }

        if (full) {
            try {
                DirectorServices.get().selection().clearSelection();
            } catch (Exception e) {
                LOGGER.warn("[Director] 清除选区失败", e);
            }
        }
    }
}
