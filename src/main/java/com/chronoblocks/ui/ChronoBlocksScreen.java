package com.chronoblocks.ui;

import com.chronoblocks.ui.imgui.ImGuiRenderer;
import com.chronoblocks.ui.input.InputFocusManager;
import com.chronoblocks.client.renderer.MouseHoverRenderEvents;
import com.chronoblocks.client.renderer.MouseHoverRenderer;
import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.world.snapshot.WorldSnapshotManager;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 导入客户端管理器
import com.chronoblocks.ui.di.UIConfiguration;
import com.chronoblocks.ui.di.UIConfigurationBuilder;

/**
 * 🎥 ChronoBlocksScreen v5.0
 * -------------------------------------------
 * ChronoBlocks 导演模式主界面（ImGui DockSpace + 面板系统）
 * <p>
 * ✅ 延迟初始化 UIManager（防止 ImGui 初始化竞态）
 * ✅ 独立渲染阶段：beginFrame / renderPanels / endFrame
 * ✅ DockSpace 全屏可拖拽布局
 * ✅ 生命周期钩子：onEnter / onExit
 * ✅ 自动恢复窗口尺寸更新
 * ✅ 调试信息栏（FPS / 面板数量 / 帧号）
 */
public class ChronoBlocksScreen extends Screen {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksScreen.class);
    
    // 布局常量 - 参考TreeFactory的设计（保留用于未来扩展）
    // private static final float UI_WIDTH_RATIO = 0.3f;  // UI宽度为窗口宽度的30%

    private final MinecraftClient client;
    
    // UI管理器
    private ChronoBlocksUIManager uiManager;
    
    // 初始化状态标志
    private boolean initializationFailed = false;
    private boolean isInitialized = false;
    
    // 导演模式状态
    private boolean directorModeActive = false;
    private WorldSnapshotManager snapshotManager;
    

    public ChronoBlocksScreen() {
        super(Text.literal("ChronoBlocks"));
        this.client = MinecraftClient.getInstance();
        LOGGER.info("[ChronoBlocks] 创建新的ChronoBlocksScreen");
        
        // 通知InputFocusManager窗口已打开
        InputFocusManager.getInstance().setChronoBlocksScreenActive(true);
        
        // 激活ChronoBlocks模式
        ChronoBlocksClientController.getInstance().activateChronoBlocksMode();
        
        // 启用鼠标悬停高亮渲染
        MouseHoverRenderEvents.enable();
        MouseHoverRenderer.getInstance().enable();
        
        // 确保鼠标光标可见
        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
            LOGGER.info("[ChronoBlocks] 鼠标光标已解锁");
        }
    }

    @Override
    protected void init() {
        super.init();
        
        // 解锁鼠标光标 - 关键实现
        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
            LOGGER.info("[ChronoBlocks] 🖱️ 鼠标光标已解锁");
        }
        
        // 防止重复初始化
        if (isInitialized) {
            LOGGER.debug("[ChronoBlocks] UI管理器已经初始化，跳过重复初始化");
            return;
        }
        
        // ImGui is now initialized centrally by ChronoBlocksClientInitializer.
        // We just need to check if it was successful.
        if (!ImGuiRenderer.getInstance().isInitialized()) {
            LOGGER.error("[ChronoBlocks] ImGui is not initialized. The UI cannot be displayed.");
            initializationFailed = true;
            return;
        }
        
        // 清理之前的ImGui状态，确保干净的起始状态
        try {
            LOGGER.debug("[ChronoBlocks] 清理之前的ImGui状态");
            com.chronoblocks.ui.imgui.ImGuiStateChecker.forceCleanupStack("ChronoBlocksScreen初始化清理");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理ImGui状态时发生错误", e);
        }
        
        // 初始化UI管理器
        try {
            UIConfiguration config = UIConfigurationBuilder.builder()
                .withMinecraftClient(client)
                .withDefaultPanels()
                .build();
            this.uiManager = config.createUIManager();
            this.isInitialized = true;
            LOGGER.info("[ChronoBlocks] UI管理器初始化成功");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] UI管理器初始化失败: {}", e.getMessage(), e);
            this.uiManager = null;
            initializationFailed = true;
        }
        
        // 初始化选择工具系统
        try {
            initializeSelectionSystem();
            LOGGER.info("[ChronoBlocks] 选择工具系统初始化完成");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 选择工具系统初始化失败", e);
        }
        
        // 启用容器渲染 v4.5
        try {
            com.chronoblocks.client.renderer.ContainerRenderEvents.enable();
            LOGGER.info("[ChronoBlocks] 容器渲染已启用");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 启用容器渲染失败", e);
        }
    }

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        LOGGER.info("[ChronoBlocks] 🎬 进入导演模式界面");
        enterDirectorMode();
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 如果初始化失败，显示错误信息
        if (initializationFailed) {
            renderErrorScreen(context, mouseX, mouseY, delta);
            return;
        }

        try {
            // 获取ImGui渲染器实例
            ImGuiRenderer imGuiRenderer = ImGuiRenderer.getInstance();

            // 更新显示尺寸（处理全屏切换等）
            imGuiRenderer.updateDisplaySize();
            LOGGER.debug("[ChronoBlocks] Display size updated");

            // 开始新的ImGui帧
            imGuiRenderer.beginFrame();
            LOGGER.debug("[ChronoBlocks] ImGui frame started");

            // 渲染UI管理器（包含DockSpace）
            if (uiManager != null) {
                LOGGER.debug("[ChronoBlocks] Rendering UI manager...");
                uiManager.render();
                LOGGER.debug("[ChronoBlocks] UI manager rendered successfully");
            } else {
                LOGGER.warn("[ChronoBlocks] UIManager为空，跳过UI渲染");
            }

            // 更新输入焦点管理器状态
            InputFocusManager.getInstance().update();

            // 移除调试状态栏显示（FPS、Panels、Frame#已在工具栏和状态栏显示）
            // renderDebugStatusBar();

            // 结束ImGui帧
            imGuiRenderer.endFrame();
            LOGGER.debug("[ChronoBlocks] ImGui frame ended");

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
     * 仅处理UI相关按键，其余交回游戏
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 检查ImGui是否想要捕获键盘事件
        if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return false; // 不拦截游戏按键，让游戏继续处理
    }
    
    /**
     * 处理鼠标点击事件
     * 根据鼠标位置决定是否处理UI事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查ImGui是否想要捕获鼠标事件
        if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse()) {
            return super.mouseClicked(mouseX, mouseY, button);
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
        
        // 禁用容器渲染 v4.5
        try {
            com.chronoblocks.client.renderer.ContainerRenderEvents.disable();
            LOGGER.info("[ChronoBlocks] 容器渲染已禁用");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 禁用容器渲染失败", e);
        }
        
        // 通知InputFocusManager窗口已关闭
        InputFocusManager.getInstance().setChronoBlocksScreenActive(false);
        LOGGER.info("[ChronoBlocks] ChronoBlocksScreen已关闭，恢复游戏输入模式");
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        LOGGER.debug("[ChronoBlocks] 窗口大小变更: {}x{}", width, height);
        ImGuiRenderer.getInstance().updateDisplaySize();
    }

    /**
     * 初始化选择工具系统
     */
    private void initializeSelectionSystem() {
        LOGGER.info("[ChronoBlocks] 初始化选择工具系统...");
        
        try {
            // 获取选择管理器
            com.chronoblocks.core.selection.SelectionManager selectionManager = 
                com.chronoblocks.core.selection.SelectionManager.get();
            
            if (selectionManager == null) {
                LOGGER.error("[ChronoBlocks] SelectionManager为空，无法初始化");
                return;
            }
            
            // 设置默认工具为"点击选择"
            if (!selectionManager.hasCurrentTool() && client.world != null) {
                LOGGER.info("[ChronoBlocks] 设置默认选择工具: 点击选择");
                selectionManager.setCurrentTool("click", client.world);
                
                // 验证工具是否设置成功
                if (selectionManager.hasCurrentTool()) {
                    LOGGER.info("[ChronoBlocks] ✅ 当前工具: {}", 
                        selectionManager.getCurrentToolDisplayName());
                } else {
                    LOGGER.warn("[ChronoBlocks] ⚠️ 设置默认工具失败");
                }
            } else {
                LOGGER.info("[ChronoBlocks] 当前工具: {}", 
                    selectionManager.getCurrentToolDisplayName());
            }
            
            // 启用选择渲染器
            com.chronoblocks.client.renderer.SelectionRenderer renderer = 
                com.chronoblocks.client.renderer.SelectionRenderer.getInstance();
            renderer.setEnabled(true);
            LOGGER.info("[ChronoBlocks] ✅ 选择渲染器已启用");
            
            // 确保选择渲染事件已注册
            if (!com.chronoblocks.client.renderer.SelectionRenderEvents.isRegistered()) {
                LOGGER.warn("[ChronoBlocks] ⚠️ 选择渲染事件未注册，现在注册...");
                com.chronoblocks.client.renderer.SelectionRenderEvents.register();
            }
            
            LOGGER.info("[ChronoBlocks] ✅ 选择工具系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 初始化选择工具系统失败", e);
        }
    }
    
    @Override
    public void close() {
        // 停用ChronoBlocks模式
        ChronoBlocksClientController.getInstance().deactivateChronoBlocksMode();
        
        // 禁用鼠标悬停高亮渲染
        MouseHoverRenderEvents.disable();
        MouseHoverRenderer.getInstance().disable();
        
        // 恢复鼠标锁定状态
        if (client != null && client.mouse != null) {
            client.mouse.lockCursor();
            LOGGER.info("[ChronoBlocks] 鼠标光标已锁定，恢复游戏状态");
        }
        
        
        // ImGui 会自动保存布局到 imgui.ini 文件
        
        // 释放UI Manager资源
        if (uiManager != null) {
            try {
                uiManager.dispose();
                LOGGER.debug("[ChronoBlocks] UI管理器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 释放UI管理器资源时发生错误", e);
            } finally {
                uiManager = null; // 确保引用被清空
            }
        }
        
        // 强制清理ImGui状态，防止第二次打开时的冲突
        try {
            LOGGER.debug("[ChronoBlocks] 强制清理ImGui状态");
            com.chronoblocks.ui.imgui.ImGuiStateChecker.forceCleanupStack("ChronoBlocksScreen关闭清理");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 清理ImGui状态时发生错误", e);
        }
        
        // 重置DockSpace布局状态，下次打开时重新应用默认布局
        try {
            com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
            LOGGER.debug("[ChronoBlocks] DockSpace布局状态已重置");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 重置DockSpace布局状态时发生错误", e);
        }
        
        // 重置状态标志
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
                // 1️⃣ 解冻非玩家实体
                client.world.getEntities().forEach(entity -> {
                    // 只解冻非玩家实体，玩家本来就没有被冻结
                    if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                        entity.setFrozenTicks(0);
                    }
                });
                LOGGER.debug("[ChronoBlocks] 非玩家实体解冻");

                // 2️⃣ 停用 mixin 拦截（时间会自动恢复流逝）
                ChronoBlocksClientController.getInstance().deactivateChronoBlocksMode();
                LOGGER.debug("[ChronoBlocks] 时间流动恢复");

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
     * 🌍 冻结世界 v2.1
     * 暂停世界时间流逝，让植物停止生长、动物停止活动
     * 注意：不冻结玩家，让玩家可以自由移动摄像机进行动画制作
     */
    private void freezeWorld() {
        try {
            if (client.world != null) {
                // 🔹 1️⃣ 固定当前时间（通过World直接设置）
                long fixedTime = client.world.getTimeOfDay();
                client.world.setTime(fixedTime, fixedTime, false);
                LOGGER.debug("[ChronoBlocks] 世界时间已冻结 (固定于 tick={})", fixedTime);

                // 🔹 2️⃣ 冻结非玩家实体（动物、怪物等，但保留玩家移动）
                client.world.getEntities().forEach(entity -> {
                    try {
                        // 不冻结玩家，让玩家可以移动摄像机
                        if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                            entity.setFrozenTicks(Integer.MAX_VALUE);
                        }
                    } catch (Exception ignored) {}
                });
                LOGGER.debug("[ChronoBlocks] 非玩家实体已冻结，玩家可自由移动");

                // 🔹 3️⃣ 启用 mixin 拦截（tickTime / tickEntities）
                ChronoBlocksClientController.getInstance().activateChronoBlocksMode();

                LOGGER.info("[ChronoBlocks] 🌍 世界冻结完成 - 非玩家实体、植物全静止，玩家可自由移动");
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
        
        // 安全检查：如果UI开着但controller未激活，则激活
        if (directorModeActive && !ChronoBlocksClientController.getInstance().isChronoBlocksModeActive()) {
            LOGGER.warn("[ChronoBlocks] 检测到模式状态不同步，重新激活ChronoBlocks模式");
            ChronoBlocksClientController.getInstance().activateChronoBlocksMode();
        }
    }

    /**
     * 检查导演模式是否激活
     */
    public boolean isDirectorModeActive() {
        return directorModeActive;
    }
    
}