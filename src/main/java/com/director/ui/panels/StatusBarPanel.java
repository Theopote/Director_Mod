package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.core.container.visibility.ContainerVisibilityFeedback;
import com.director.core.container.ContainerStatusFeedback;
import com.director.persistence.PersistenceSaveStatus;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang.Status;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import com.director.ui.commands.UICommandFacade;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.aware.ICommandsAware;
import com.director.ui.aware.IPanelRegistryAware;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.core.container.ContainerManager;

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
            boolean visible = ImGui.begin(getWindowName(), flags);
            try {
                if (visible) {
                    renderContent(barHeight);
                }
            } finally {
                ImGui.end();
            }
        } finally {
            ImGui.popStyleVar(2);
        }
    }

    // ==================== Content ====================

    private void renderContent(float barHeight) {
        updateFPS();

        float y = (barHeight - ImGui.getTextLineHeight()) * 0.5f;
        float padding = ImGui.getStyle().getFramePadding().x;
        float gap = UITheme.Spacing.LARGE;
        String fpsText = DirectorI18n.translate(Status.FPS, fps);
        int fpsColor = fps >= 60 ? UITheme.Semantic.SUCCESS
                : fps >= 30 ? UITheme.Semantic.WARNING : UITheme.Semantic.DANGER;
        String failureText = PersistenceSaveStatus.hasSaveFailure()
                ? Icons.WARNING + " " + DirectorI18n.translate(Status.SAVE_FAILED) : null;

        float available = ImGui.getWindowWidth() - 2f * padding;
        float fpsWidth = ImGui.calcTextSize(fpsText).x;
        boolean compactFailure = failureText != null && ImGui.calcTextSize(failureText).x + gap + fpsWidth > available;
        String failureLabel = compactFailure ? Icons.WARNING : failureText;
        float failureWidth = failureLabel == null ? 0f : ImGui.calcTextSize(failureLabel).x;
        boolean showFps = failureLabel == null || failureWidth + gap + fpsWidth <= available;
        float rightWidth = failureWidth + (showFps ? fpsWidth : 0f)
                + (failureLabel != null && showFps ? gap : 0f);
        float rightX = Math.max(padding, ImGui.getWindowWidth() - padding - rightWidth);
        float leftLimit = rightX - gap;

        ImGui.setCursorPos(padding, y);
        renderLeftStatus(leftLimit);

        ImGui.setCursorPos(rightX, y);
        if (failureLabel != null) {
            renderSaveFailure(failureLabel, compactFailure);
            if (showFps) separator();
        }
        if (showFps) {
            UITheme.label(fpsText, fpsColor);
        }
    }

    private void renderLeftStatus(float limit) {
        if (commands != null && commands.playback() != null) {
            var playback = commands.playback();
            boolean playing = playback.isPlaying();
            String icon = playing ? Icons.PLAY : Icons.PAUSE;
            segment(limit, icon + " " + DirectorI18n.translate(playing ? Status.PLAYING : Status.PAUSED),
                    playing ? UITheme.Semantic.SUCCESS : UITheme.DarkTheme.TEXT);

            float total = playback.getTotalDuration();
            String timeKey = total < 60f ? Status.TIME_PRECISE : Status.TIME_ROUNDED;
            segment(limit, DirectorI18n.translate(timeKey, playback.getCurrentTime(), total), UITheme.DarkTheme.TEXT);

            if (playback.hasInPoint() || playback.hasOutPoint()) {
                segment(limit, DirectorI18n.translate(Status.RANGE, playback.getInPoint(), playback.getOutPoint()),
                        UITheme.Semantic.INFO);
            }
            float speed = playback.getPlaybackSpeed();
            if (Math.abs(speed - 1f) > 0.01f) {
                segment(limit, DirectorI18n.translate(Status.SPEED, speed), UITheme.DarkTheme.TEXT);
            }
            if (playback.isLooping()) {
                segment(limit, Icons.LOOP + " " + DirectorI18n.translate(Status.LOOP), UITheme.Semantic.INFO);
            }
        }

        // 6. Container visibility batch operations
        if (ContainerVisibilityFeedback.hasMessage()) {
            String visibilityMessage = ContainerVisibilityFeedback.getStatusMessage();
            if (visibilityMessage != null && !visibilityMessage.isBlank()) {
                segment(limit, Icons.REFRESH + " " + visibilityMessage, UITheme.Semantic.WARNING);
            }
        }

        // 6.5. Container operational advisories
        if (ContainerStatusFeedback.hasMessage()) {
            String advisory = ContainerStatusFeedback.getMessage();
            if (advisory != null && !advisory.isBlank()) {
                segment(limit, Icons.WARNING + " " + advisory, UITheme.Semantic.WARNING);
            }
        }

        // 7. Panels
        if (panelRegistry != null) {
            int total = 0;
            int visible = 0;
            for (IPanel p : panelRegistry.getAllPanels()) {
                total++;
                if (p.isVisible()) visible++;
            }
            segment(limit,
                    DirectorI18n.translate(Status.PANELS, visible, total),
                    UITheme.DarkTheme.TEXT_DISABLED
            );
        }

        // 7. Containers
        try {
            ContainerManager cm = DirectorServices.get().containers();
            if (cm.getContainerCount() > 0) {
                segment(limit,
                        DirectorI18n.translate(Status.CONTAINERS, cm.getContainerCount()),
                        UITheme.DarkTheme.TEXT_DISABLED
                );
            }
        } catch (Exception ignored) {}
    }

    private void segment(float limit, String text, int color) {
        if (ImGui.getCursorPosX() + ImGui.calcTextSize(text).x <= limit) {
            UITheme.label(text, color);
            separator();
        }
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

    private void renderSaveFailure(String label, boolean compact) {
        String popup = "##saveFailureDetails";
        float[] danger = UITheme.toFloatArrayPublic(UITheme.Semantic.DANGER);
        ImGui.pushStyleColor(ImGuiCol.Text, danger[0], danger[1], danger[2], danger[3]);
        ImGui.pushStyleColor(ImGuiCol.Button, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f);
        boolean clicked;
        try {
            clicked = ImGui.smallButton(label + "###saveFailure");
        } finally {
            ImGui.popStyleVar();
            ImGui.popStyleColor(2);
        }
        if (compact && ImGui.isItemHovered()) {
            ImGui.setTooltip(DirectorI18n.translate(Status.SAVE_FAILED));
        }
        if (clicked) {
            ImGui.openPopup(popup);
        }
        if (ImGui.beginPopup(popup)) {
            try {
                for (String key : PersistenceSaveStatus.getFailureReasonKeys()) {
                    ImGui.textUnformatted(DirectorI18n.translate(key));
                }
                ImGui.textDisabled(DirectorI18n.translate(Status.SAVE_FAILED_HINT));
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void separator() {
        ImGui.sameLine(0, UITheme.Spacing.LARGE);
    }
}
