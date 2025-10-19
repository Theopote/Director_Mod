package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.chronoblocks.core.animation.AnimationState;
import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;

/**
 * ChronoBlocks Toolbar v4.0
 * 
 * 简洁专业版工具栏：
 * - 工具选择（Select / Move / Rotate / Scale）
 * - 播放控制（Play / Pause / Stop / Loop）
 * - 状态显示（时间 / 工具模式 / FPS）
 * - 窗口控制（关闭）
 */
public class ToolbarPanel extends AbstractPanel {

    public enum ToolMode {
        SELECT("选择", "S"),
        MOVE("移动", "M"),
        ROTATE("旋转", "R"),
        SCALE("缩放", "C");

        private final String displayName;
        private final String shortcut;

        ToolMode(String name, String shortcut) {
            this.displayName = name;
            this.shortcut = shortcut;
        }

        public String getDisplayName() { return displayName; }
        public String getShortcut() { return shortcut; }
    }

    private ToolMode currentTool = ToolMode.SELECT;
    private boolean isLoopEnabled = false;
    
    // RGBA颜色打包辅助方法
    private static int rgba(int r, int g, int b, int a) {
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    @Override
    public String getPanelName() {
        return "Toolbar";
    }

    @Override
    public String getWindowName() {
        return "Toolbar###ToolbarPanel";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    protected int getWindowFlags() {
        return imgui.flag.ImGuiWindowFlags.NoCollapse;
    }

    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return false;
    }

    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }

    @Override
    protected void doRender() {
        // 使用统一的元素间距（4px）和按钮内边距（12px, 3px）
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        
        renderToolButtons();
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderPlaybackControls();
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderStatusIndicators();
        ImGui.sameLine();
        renderCloseButton();
        
        ImGui.popStyleVar(2);
    }

    /* ---------- 工具选择 ---------- */
    private void renderToolButtons() {
        // 工具图标
        UITheme.label(Icons.TOOLS, UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.sameLine();

        for (ToolMode mode : ToolMode.values()) {
            boolean active = (currentTool == mode);
            if (active) {
                // 使用主题高亮色
                float[] highlightColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }
            // 使用自动宽度：按钮宽度 = 文字宽度 + 2×边距(12px)
            if (ImGui.button(mode.getDisplayName() + "##" + mode.name(), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                setCurrentTool(mode);
            }
            if (active) {
                ImGui.popStyleColor();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(mode.getDisplayName() + " (" + mode.getShortcut() + ")");
            }
            ImGui.sameLine();
        }
    }

    /* ---------- 播放控制 ---------- */
    private void renderPlaybackControls() {
        PlaybackController pc = PlaybackController.get();
        UITheme.label(Icons.PLAY, UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.sameLine();

        // Play / Pause - 使用自动宽度
        if (pc.isPlaying()) {
            if (ImGui.button(Icons.PAUSE + " 暂停##play", 0, UITheme.Spacing.BUTTON_HEIGHT)) pc.pause();
        } else {
            if (ImGui.button(Icons.PLAY + " 播放##play", 0, UITheme.Spacing.BUTTON_HEIGHT)) pc.play();
        }
        ImGui.sameLine();

        // Stop - 使用自动宽度
        if (ImGui.button(Icons.STOP + " 停止##play", 0, UITheme.Spacing.BUTTON_HEIGHT)) pc.stop();
        ImGui.sameLine();

        // Loop toggle
        if (ImGui.checkbox(Icons.LOOP + " 循环##loop", isLoopEnabled)) {
            isLoopEnabled = !isLoopEnabled;
            pc.toggleLoop();
        }
    }

    /* ---------- 状态显示 ---------- */
    private void renderStatusIndicators() {
        AnimationState state = AnimationState.get();
        float time = state.getCurrentTime();
        float duration = state.getClip() != null ? state.getClip().computeDuration() : 10.0f;
        float fps = ImGui.getIO().getFramerate();

        // 使用主题文本颜色
        UITheme.label(String.format(Icons.TIME + " %.2f / %.2fs", time, duration), UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        UITheme.label(Icons.TOOLBOX + " " + currentTool.getDisplayName(), UITheme.DarkTheme.TEXT);
        ImGui.sameLine();
        UITheme.label(String.format("FPS: %.0f", fps), UITheme.DarkTheme.TEXT);
        
        // 渲染开关 v4.5
        ImGui.sameLine();
        renderRenderingToggle();
    }
    
    /* ---------- 渲染开关 v4.5 ---------- */
    private void renderRenderingToggle() {
        var renderer = com.chronoblocks.client.renderer.ContainerRenderer.get();
        boolean enabled = renderer.isEnabled();
        
        // 使用主题颜色的可视化开关
        if (enabled) {
            float[] activeColor = UITheme.toFloatArrayPublic(rgba(51, 204, 51, 255)); // 绿色
            float[] hoverColor = UITheme.toFloatArrayPublic(rgba(77, 255, 77, 255));
            ImGui.pushStyleColor(ImGuiCol.Button, activeColor[0], activeColor[1], activeColor[2], activeColor[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]);
        } else {
            float[] inactiveColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.BUTTON);
            float[] hoverColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.BUTTON_HOVERED);
            ImGui.pushStyleColor(ImGuiCol.Button, inactiveColor[0], inactiveColor[1], inactiveColor[2], inactiveColor[3]);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]);
        }
        
        String label = enabled ? (Icons.VISIBLE + " 可见") : (Icons.INVISIBLE + " 隐藏");
        // 使用自动宽度
        if (ImGui.button(label + "##renderToggle", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            renderer.setEnabled(!enabled);
            logger.info("[ChronoBlocks] 容器渲染已{}", enabled ? "禁用" : "启用");
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(enabled ? "点击隐藏容器边界框" : "点击显示容器边界框");
        }
        
        ImGui.popStyleColor(2);
        
        // 渲染选项弹出菜单（右键点击按钮打开）
        if (enabled && ImGui.beginPopupContextItem("renderOptions")) {
            UITheme.title(Icons.RULER + " 渲染选项");
            
            // ImGui checkbox 需要传递 boolean[] 数组
            boolean showBBox = renderer.isShowBoundingBox();
            if (ImGui.checkbox("边界框", showBBox)) {
                renderer.setShowBoundingBox(!showBBox);
            }
            
            boolean showCenter = renderer.isShowCenterPoint();
            if (ImGui.checkbox("中心点", showCenter)) {
                renderer.setShowCenterPoint(!showCenter);
            }
            
            boolean showAxes = renderer.isShowAxes();
            if (ImGui.checkbox("坐标轴", showAxes)) {
                renderer.setShowAxes(!showAxes);
            }
            
            ImGui.endPopup();
        }
    }

    /* ---------- 关闭按钮 ---------- */
    private void renderCloseButton() {
        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1.0f, 0.3f, 0.3f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.6f, 0.1f, 0.1f, 1.0f);

        // 使用自动宽度
        if (ImGui.button("关闭##close", 0, UITheme.Spacing.BUTTON_HEIGHT)) handleCloseWindow();
        if (ImGui.isItemHovered()) ImGui.setTooltip("关闭 ChronoBlocks 窗口");

        ImGui.popStyleColor(3);
    }

    private void setCurrentTool(ToolMode tool) {
        if (currentTool != tool) {
            currentTool = tool;
            logger.info("[ChronoBlocks] Tool switched to {}", tool.getDisplayName());
        }
    }

    public ToolMode getCurrentTool() {
        return currentTool;
    }

    private void handleCloseWindow() {
        logger.info("[ChronoBlocks] Closing ChronoBlocks window...");
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.currentScreen instanceof com.chronoblocks.ui.ChronoBlocksScreen screen) {
                screen.close();
                client.setScreen(null);
            }
        } catch (Exception e) {
            logger.error("[ChronoBlocks] Failed to close ChronoBlocks window", e);
        }
    }
}
