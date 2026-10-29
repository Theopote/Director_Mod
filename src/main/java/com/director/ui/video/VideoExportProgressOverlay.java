package com.director.ui.video;

import com.director.ui.DirectorUIManager;
import com.director.ui.theme.Icons;
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
    private enum Status { RUNNING, CANCELING, SUCCEEDED, FAILED, CANCELED }
    private volatile Status status = Status.RUNNING;
    private volatile long startedAt;
    private volatile long elapsedAtFinish;

    public boolean isVisible() {
        return visible;
    }

    public synchronized void show() {
        visible = true;
        progress = 0f;
        message = "准备中...";
        startedAt = System.nanoTime();
        elapsedAtFinish = 0;
        status = Status.RUNNING;
    }

    public void hide() {
        visible = false;
    }

    public synchronized void setProgress(float p, String msg) {
        if (status != Status.RUNNING) return;
        progress = Math.max(0f, Math.min(1f, p));
        message = (msg != null) ? msg : "";
    }

    public synchronized void setFinished(boolean success, String msg) {
        elapsedAtFinish = System.nanoTime() - startedAt;
        if (msg != null) {
            this.message = msg;
        }
        if (success) this.progress = 1f;
        status = success ? Status.SUCCEEDED : Status.FAILED;
    }

    public synchronized void setCanceled(String message) {
        this.message = message == null ? "导出已取消。" : message;
        elapsedAtFinish = System.nanoTime() - startedAt;
        status = Status.CANCELED;
    }

    private synchronized boolean beginCancel() {
        if (status != Status.RUNNING) return false;
        message = "正在取消，清理临时输出...";
        status = Status.CANCELING;
        return true;
    }

    public void render(DirectorUIManager uiManager) {
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

        Status current = status;
        boolean finished = current == Status.SUCCEEDED || current == Status.FAILED || current == Status.CANCELED;
        if (ImGui.begin(Icons.VIDEO + " 视频导出进度##VideoExportProgressOverlay", flags)) {
            if (!finished) {
                ImGui.text("渲染进度：");
                ImGui.progressBar(progress, -1, 0, String.format("%.0f%%", progress * 100f));
            } else {
                if (current == Status.SUCCEEDED) {
                    ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f, Icons.CHECK + " 导出完成");
                } else if (current == Status.CANCELED) {
                    ImGui.textColored(1.0f, 0.8f, 0.4f, 1.0f, "导出已取消");
                } else {
                    ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, Icons.ERROR + " 导出失败");
                }
            }
            if (message != null && !message.isBlank()) {
                ImGui.textWrapped(message);
            }
            long elapsed = finished ? elapsedAtFinish : System.nanoTime() - startedAt;
            ImGui.textDisabled("已用时：" + java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(Math.max(0, elapsed)) + " 秒");
            ImGui.textDisabled("省资源模式：复用屏幕世界渲染（镜头会跟随导出）；已锁定玩家移动/转向/场景点击。");

            if (!finished) {
                ImGui.beginDisabled(current == Status.CANCELING);
                if (ImGui.button(current == Status.CANCELING ? "正在取消..." : Icons.CLOSE + " 取消导出")) {
                    if (uiManager != null && beginCancel()) uiManager.requestCancelVideoExport();
                }
                ImGui.endDisabled();
            } else {
                if (ImGui.button(Icons.CLOSE + " 关闭")) {
                    hide();
                }
            }
        }
        ImGui.end();
    }
}


