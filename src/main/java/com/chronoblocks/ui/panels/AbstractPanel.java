package com.chronoblocks.ui.panels;

import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AbstractPanel - 面板抽象基类
 * <p>
 * 提供面板的通用实现，包括日志记录、状态管理和基础功能。
 * 所有具体面板都应该继承此类以获得一致的行为。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public abstract class AbstractPanel implements IPanel {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private boolean visible = true;
    private boolean initialized = false;

    // 面板隐藏状态管理
    private boolean hidden = false;

    @Override
    public final void render() {
        boolean shouldRenderWindow = (visible && !hidden) || shouldReserveDockSpaceWhenHidden();
        if (!shouldRenderWindow) {
            return;
        }

        final boolean renderContent = visible && !hidden;
        final boolean renderPlaceholder = !renderContent && shouldReserveDockSpaceWhenHidden();

        try {
            // 确保面板已初始化
            if (!initialized) {
                initialize();
                initialized = true;
            }

            if (shouldUseStandardWindow()) {
                // 将 begin/end 的逻辑集中在这里
                // 为DockSpace中的面板设置合适的窗口标志
                int windowFlags = getWindowFlags();
                
                // 如果面板被隐藏，使用特殊的窗口标志（移除可能阻止渲染的标志）
                if (hidden || renderPlaceholder) {
                    // 只保留最基本的标志，避免阻止窗口创建
                    windowFlags = windowFlags | imgui.flag.ImGuiWindowFlags.NoScrollbar;
                }
                
                // 记录状态检查，防止嵌套调用问题
                com.chronoblocks.ui.imgui.ImGuiStateChecker.recordBegin("Panel", getWindowName());
                
                boolean windowStarted;
                try {
                    // 添加ImGui状态检查，防止嵌套调用问题
                    windowStarted = ImGui.begin(getWindowName(), windowFlags);
                    if (windowStarted) {
                        try {
                            // 检查是否点击了关闭按钮
                            if (ImGui.isWindowAppearing()) {
                                // 设置窗口默认大小（高度为80%）
                                setDefaultWindowSize();
                            }
                            
                            if (renderPlaceholder || hidden) {
                                // 渲染隐藏占位符
                                renderHiddenPlaceholder();
                            } else {
                                // 正常渲染内容
                                doRender();
                            }
                        } catch (Exception e) {
                            logger.error("[{}] 面板内容渲染错误: {}", getPanelName(), e.getMessage(), e);
                            com.chronoblocks.ui.imgui.ImGuiStateChecker.recordError("Panel", getWindowName(), e.getMessage());
                        }
                    } else {
                        // 如果窗口未能开始，记录警告但不抛出异常
                        logger.warn("[{}] 窗口未能开始渲染: {} (可能被其他窗口遮挡或DockSpace未就绪)", getPanelName(), getWindowName());
                        com.chronoblocks.ui.imgui.ImGuiStateChecker.recordError("Panel", getWindowName(), "窗口未能开始");
                    }
                } finally {
                    // 关键修复：无论begin返回什么，都必须调用end()来配对
                    try {
                        ImGui.end();
                    } catch (Exception e) {
                        logger.error("[{}] 结束窗口时发生错误: {}", getPanelName(), e.getMessage(), e);
                        com.chronoblocks.ui.imgui.ImGuiStateChecker.recordError("Panel", getWindowName(), "结束窗口失败: " + e.getMessage());
                    }
                    
                    // 记录End调用以保持栈平衡
                    com.chronoblocks.ui.imgui.ImGuiStateChecker.recordEnd("Panel", getWindowName());
                }
            } else {
                // 对于像 MainMenuBar 这样的特殊面板，直接调用 doRender()
                // 这些面板自己管理 ImGui.begin/end 调用
                try {
                    doRender();
                } catch (Exception e) {
                    logger.error("[{}] 特殊面板渲染错误: {}", getPanelName(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            logger.error("[{}] 渲染错误: {}", getPanelName(), e.getMessage(), e);
            // 在渲染异常时，尝试清理可能的ImGui状态
            cleanupImGuiState();
        }
    }
    
    /**
     * 清理ImGui状态，防止状态泄漏
     */
    private void cleanupImGuiState() {
        try {
            // 记录状态清理信息，帮助调试
            logger.warn("[{}] 检测到渲染异常，请检查ImGui Begin/End调用是否匹配", getPanelName());
            // 注意：这里不能直接调用ImGui.end()，因为不知道具体状态
            // 只能记录警告，让开发者手动检查Begin/End调用
        } catch (Exception e) {
            logger.error("[{}] 清理ImGui状态时发生错误: {}", getPanelName(), e.getMessage());
        }
    }
    
    /**
     * 执行实际的渲染逻辑
     * 子类必须实现此方法
     */
    protected abstract void doRender();
    
    @Override
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public void setVisible(boolean visible) {
        boolean newVisible = visible;
        boolean newHidden = !visible && shouldReserveDockSpaceWhenHidden();
        if (this.visible != newVisible || this.hidden != newHidden) {
            this.visible = newVisible;
            this.hidden = newHidden;
            logger.debug("[{}] 可见性设置为: {}, Dock占位: {}", getPanelName(), newVisible, newHidden);
        }
    }
    
    /**
     * 检查面板是否被隐藏
     * 隐藏状态与可见性不同：隐藏的面板仍会占据 DockSpace 位置，但不显示内容
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * 设置面板的隐藏状态。
     * 当隐藏时，仍会保持DockSpace占位，但不会渲染实际内容。
     * @param hidden 是否隐藏
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
        if (hidden && visible) {
            visible = false;
        }
    }

    /**
     * 渲染隐藏占位符
     * 当面板被隐藏时，显示透明占位符以保持DockSpace布局
     */
    private void renderHiddenPlaceholder() {
        if (!shouldShowHiddenPlaceholderIndicator()) {
            // 保持最小占位
            ImGui.dummy(0.0f, 0.0f);
            return;
        }

        // 使用透明背景
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, 0x00000000);

        // 显示隐藏提示
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "[" + getPanelName() + " 已隐藏]");

        ImGui.popStyleColor();
    }
    
    @Override
    public void initialize() {
        logger.debug("[{}] 初始化面板", getPanelName());
    }
    
    @Override
    public void dispose() {
        logger.debug("[{}] 清理面板资源", getPanelName());
        initialized = false;
    }
    
    @Override
    public boolean needsUpdate() {
        // 默认实现：总是需要更新
        // 子类可以重写此方法以实现更智能的更新策略
        return true;
    }
    
    @Override
    public abstract String getWindowName();

    /**
     * 子类可以重写此方法来决定是否使用标准的 ImGui.begin/end 窗口。
     * @return true 如果使用标准窗口，false 如果使用特殊窗口（如MainMenuBar）
     */
    protected boolean shouldUseStandardWindow() {
        return true;
    }
    
    /**
     * 子类可以重写此方法来设置窗口标志
     * @return 窗口标志，默认为0（使用ImGui默认标志）
     */
    protected int getWindowFlags() {
        // 默认添加关闭按钮，但允许子类重写
        return imgui.flag.ImGuiWindowFlags.None;
    }

    /**
     * 当面板被设置为不可见时，是否仍需保留DockSpace占位。
     * 默认情况下返回false。
     * @return true表示即使隐藏也保留占位
     */
    public boolean shouldReserveDockSpaceWhenHidden() {
        return false;
    }

    /**
     * 当面板隐藏时，是否显示占位提示文本。
     * 默认显示提示，子类可重写返回false以保持完全空白。
     * @return true表示显示提示信息
     */
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return true;
    }

    /**
     * 设置窗口的默认大小
     * 子类可以重写此方法来设置自定义的默认大小
     */
    protected void setDefaultWindowSize() {
        // 获取视口大小
        imgui.ImGuiViewport viewport = imgui.ImGui.getMainViewport();
        float viewportWidth = viewport.getWorkSizeX();
        float viewportHeight = viewport.getWorkSizeY();
        
        // 设置默认窗口大小（宽度保持默认，高度为视口高度的80%）
        float defaultWidth = Math.min(400.0f, viewportWidth * 0.3f); // 宽度为视口宽度的30%，最大400px
        float defaultHeight = viewportHeight * 0.8f; // 高度为视口高度的80%
        
        imgui.ImGui.setNextWindowSize(defaultWidth, defaultHeight, imgui.flag.ImGuiCond.FirstUseEver);
    }

}
