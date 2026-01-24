package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import com.chronoblocks.ui.commands.UICommandFacade;
import com.chronoblocks.ui.di.IPanelRegistry;
import com.chronoblocks.ui.aware.ICommandsAware;
import com.chronoblocks.ui.aware.IPanelRegistryAware;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.core.container.ContainerManager;

/**
 * StatusBarPanel
 *
 * Facade read-only status display panel.
 * No commands, no side effects.
 */
public class StatusBarPanel extends AbstractPanel implements IPanelRegistryAware, ICommandsAware {

    private UICommandFacade commands;
    private IPanelRegistry panelRegistry;

    // FPS smoothing
    private long lastFrameTime = System.nanoTime();
    private float fps = 60.0f;
    private final float fpsSmoothing = 0.9f;

    // ==================== Meta ====================

    @Override
    public String getPanelName() {
        return "StatusBar";
    }

    @Override
    public String getWindowName() {
        return "##StatusBar";
    }

    @Override
    protected boolean shouldUseStandardWindow() {
        // 状态栏需要自己管理窗口（像 MenuBar 一样），以便在 begin() 之前设置位置和大小
        return false;
    }

    @Override
    public int getPriority() {
        return -10; // 永远在最底部
    }

    // ==================== Injection ====================

    @Override
    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
    }

    @Override
    public boolean isHideableInView() {
        return false;
    }

    @Override
    public void setPanelRegistry(IPanelRegistry registry) {
        this.panelRegistry = registry;
    }

    // ==================== Render ====================

    @Override
    protected void doRender() {
        renderStatusBar();
    }

    private void renderStatusBar() {
        float barHeight = 22.0f;
        float displayHeight = ImGui.getIO().getDisplaySizeY();
        float displayWidth = ImGui.getIO().getDisplaySizeX();

        // ⭐ 必须在 begin() 之前设置窗口位置和大小
        ImGui.setNextWindowPos(0, displayHeight - barHeight);
        ImGui.setNextWindowSize(displayWidth, barHeight);

        int flags =
                ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoScrollbar |
                ImGuiWindowFlags.NoScrollWithMouse |
                ImGuiWindowFlags.NoCollapse;

        // 应用扁平样式
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 0.0f);

        try {
            if (ImGui.begin(getWindowName(), flags)) {
                try {
                    renderContent(barHeight);
                } catch (Exception e) {
                    logger.error("[StatusBar] Render error", e);
                } finally {
                    ImGui.end();
                }
            }
        } finally {
            ImGui.popStyleVar(2);
        }
    }

    // ==================== Content ====================

    private void renderContent(float barHeight) {
        updateFPS();

        float textHeight = ImGui.getTextLineHeight();
        ImGui.setCursorPosY((barHeight - textHeight) * 0.5f);
        ImGui.setCursorPosX(ImGui.getStyle().getFramePadding().x);

        // 1. FPS
        int fpsColor = fps >= 60
                ? rgba(51, 204, 51)
                : fps >= 30
                    ? rgba(255, 204, 51)
                    : rgba(255, 102, 102);

        UITheme.label(String.format("FPS: %.0f", fps), fpsColor);
        separator();

        // 2. Playback state
        if (commands != null && commands.playback() != null) {
            boolean playing = commands.playback().isPlaying();
            boolean looping = commands.playback().isLooping();
            float speed = commands.playback().getPlaybackSpeed();

            String playText = playing
                    ? Icons.PLAY + " Playing"
                    : Icons.PAUSE + " Paused";

            UITheme.label(playText,
                    playing ? rgba(51, 204, 51) : UITheme.DarkTheme.TEXT);
            separator();

            // 3. Timeline time
            float current = commands.playback().getCurrentTime();
            float total = commands.playback().getTotalDuration();

            String timeFmt = total < 60.0f ? "%.2fs / %.2fs" : "%.0fs / %.0fs";
            UITheme.label(
                    String.format("Time: " + timeFmt, current, total),
                    UITheme.DarkTheme.TEXT
            );
            separator();

            // 4. Speed
            if (Math.abs(speed - 1.0f) > 0.01f) {
                UITheme.label(String.format("Speed: %.2fx", speed),
                        UITheme.DarkTheme.TEXT);
                separator();
            }

            // 5. Loop
            if (looping) {
                UITheme.label(Icons.LOOP + " Loop", rgba(61, 133, 224));
                separator();
            }
        }

        // 6. Panels
        if (panelRegistry != null) {
            int total = 0;
            int visible = 0;
            for (IPanel p : panelRegistry.getAllPanels()) {
                total++;
                if (p.isVisible()) visible++;
            }
            UITheme.label(
                    String.format("Panels: %d/%d", visible, total),
                    UITheme.DarkTheme.TEXT_DISABLED
            );
            separator();
        }

        // 7. Containers
        try {
            ContainerManager cm = ContainerManager.getInstance();
            if (cm != null && cm.getContainerCount() > 0) {
                UITheme.label(
                        "Containers: " + cm.getContainerCount(),
                        UITheme.DarkTheme.TEXT_DISABLED
                );
            }
        } catch (Exception ignored) {}
    }

    // ==================== Helpers ====================

    private void updateFPS() {
        long now = System.nanoTime();
        float delta = (now - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = now;

        if (delta > 0 && delta < 1.0f) {
            float current = 1.0f / delta;
            fps = fps * fpsSmoothing + current * (1.0f - fpsSmoothing);
        }
    }

    private void separator() {
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();
    }

    private static int rgba(int r, int g, int b) {
        return (r << 24) | (g << 16) | (b << 8) | 255;
    }
}
