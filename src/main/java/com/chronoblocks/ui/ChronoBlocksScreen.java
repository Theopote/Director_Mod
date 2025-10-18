package com.chronoblocks.ui;

import com.chronoblocks.ui.imgui.ImGuiRenderer;
import com.chronoblocks.ui.input.InputFocusManager;
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
 * ChronoBlocksScreen - 参考TreeFactory的简洁实现
 * <p>
 * 采用TreeFactory项目的优秀架构设计
 * 简洁、稳定、易维护的ImGui实现
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

    public ChronoBlocksScreen() {
        super(Text.literal("ChronoBlocks"));
        this.client = MinecraftClient.getInstance();
        LOGGER.info("[ChronoBlocks] 创建新的ChronoBlocksScreen");
        
        // 通知InputFocusManager窗口已打开
        InputFocusManager.getInstance().setChronoBlocksScreenActive(true);
    }

    @Override
    protected void init() {
        super.init();
        
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
        
        // 启用容器渲染 v4.5
        try {
            com.chronoblocks.client.renderer.ContainerRenderEvents.enable();
            LOGGER.info("[ChronoBlocks] 容器渲染已启用");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 启用容器渲染失败", e);
        }
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
    
    @Override
    public void removed() {
        super.removed();
        
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
    
    // 处理输入事件 - 参考TreeFactory的输入处理方式
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ImGui.getIO().getWantCaptureKeyboard()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    // 窗口大小改变时更新ImGui
    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        // 窗口大小改变时的处理逻辑
        LOGGER.debug("[ChronoBlocks] 窗口大小改变: {}x{}", width, height);
    }

    @Override
    public boolean shouldPause() {
        return false;  // 不暂停游戏
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

    @Override
    public void close() {
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
}