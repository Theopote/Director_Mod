package com.chronoblocks.ui.video;

import com.chronoblocks.ui.ChronoBlocksUIManager;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

/**
 * 🎥 VideoExportProgressOverlay - 视频导出进度浮层
 *
 * <p>设计为全局 Overlay：不依赖 DockSpace，随 UIManager 每帧渲染。</p>
 */
public final class VideoExportProgressOverlay {

    private volatile boolean visible = false;
    private volatile float progress = 0f; // 0~1
    private volatile String message = "准备中...";

    public boolean isVisible() {
        return visible;
    }

    public void show() {
        visible = true;
        progress = 0f;
        message = "准备中...";
    }

    public void hide() {
        visible = false;
    }

    public void setProgress(float p, String msg) {
        progress = Math.max(0f, Math.min(1f, p));
        message = (msg != null) ? msg : "";
    }

    public void render(ChronoBlocksUIManager uiManager) {
        if (!visible) {
            return;
        }

        var viewport = ImGui.getMainViewport();
        // 左上角轻量提示窗
        ImGui.setNextWindowPos(viewport.getWorkPosX() + 16, viewport.getWorkPosY() + 48, ImGuiCond.Always);
        ImGui.setNextWindowSize(420, 0, ImGuiCond.Always);

        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoFocusOnAppearing;

        if (ImGui.begin(Icons.VIDEO + " 视频导出进度##VideoExportProgressOverlay", flags)) {
            ImGui.text("渲染进度：");
            ImGui.progressBar(progress, -1, 0, String.format("%.0f%%", progress * 100f));
            if (message != null && !message.isBlank()) {
                ImGui.text(message);
            }
            ImGui.textDisabled("省资源模式：复用屏幕世界渲染（镜头会跟随导出）；已锁定玩家移动/转向/场景点击。");

            if (ImGui.button(Icons.CLOSE + " 取消导出")) {
                if (uiManager != null) {
                    uiManager.requestCancelVideoExport();
                }
            }
        }
        ImGui.end();
    }
}


