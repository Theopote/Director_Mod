package com.chronoblocks.ui.timeline.playback.camera;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * CameraDebugOverlay - 开发调试用的摄像机状态显示
 * 
 * <p>显示内容：
 * <ul>
 *   <li>当前摄像机模式（GAME / PREVIEW / PLAYING）</li>
 *   <li>当前 Sample 的位置、角度、FOV 等</li>
 *   <li>是否有 Snapshot 缓存</li>
 * </ul>
 * 
 * <p>使用方式：在调试时调用 {@link #render()}，发布时可以移除或用配置项控制
 */
public final class CameraDebugOverlay {
    private static boolean enabled = false; // 改为 true 启用调试显示

    private CameraDebugOverlay() {
    }

    /**
     * 设置是否启用调试显示
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    /**
     * 渲染调试信息（在主循环中调用）
     */
    public static void render() {
        if (!enabled) {
            return;
        }

        CameraRuntime runtime = CameraRuntime.getInstance();
        CameraSample sample = runtime.getActiveSample();

        // 创建半透明窗口，固定在屏幕右上角
        ImGui.setNextWindowPos(ImGui.getMainViewport().getPosX() + ImGui.getMainViewport().getSizeX() - 420, 
                                ImGui.getMainViewport().getPosY() + 10);
        ImGui.setNextWindowSize(400, 0);
        
        int flags = ImGuiWindowFlags.NoResize | 
                   ImGuiWindowFlags.NoMove | 
                   ImGuiWindowFlags.NoCollapse |
                   ImGuiWindowFlags.AlwaysAutoResize;

        if (ImGui.begin("摄像机调试信息", flags)) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "=== Camera Runtime Debug ===");
            ImGui.separator();

            // 模式信息
            CameraMode mode = runtime.getMode();
            ImGui.text("模式: ");
            ImGui.sameLine();
            switch (mode) {
                case GAME -> ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, "GAME (玩家控制)");
                case PREVIEW -> ImGui.textColored(0.5f, 0.8f, 1.0f, 1.0f, "PREVIEW (预览模式)");
                case PLAYING -> ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f, "PLAYING (播放中)");
            }

            ImGui.text("电影模式激活: " + (runtime.isCinematicActive() ? "是" : "否"));
            ImGui.text("有玩家快照: " + (runtime.hasGameSnapshot() ? "是" : "否"));

            ImGui.separator();

            // Sample 信息
            if (sample != null && runtime.isCinematicActive()) {
                ImGui.textColored(0.8f, 1.0f, 0.8f, 1.0f, "当前 Sample:");
                ImGui.text(String.format("  位置: (%.2f, %.2f, %.2f)", 
                    sample.position().x, sample.position().y, sample.position().z));
                ImGui.text(String.format("  旋转: yaw=%.1f°, pitch=%.1f°, roll=%.1f°", 
                    sample.yaw(), sample.pitch(), sample.roll()));
                ImGui.text(String.format("  FOV: %.1f", sample.fov()));
                ImGui.text(String.format("  裁剪面: near=%.2f, far=%.1f", 
                    sample.nearPlane(), sample.farPlane()));
                
                if (sample.depthOfFieldEnabled()) {
                    ImGui.textColored(0.8f, 0.8f, 1.0f, 1.0f, 
                        String.format("  景深: focus=%.1f, aperture=f/%.1f", 
                        sample.focusDistance(), sample.aperture()));
                }
            } else {
                ImGui.textDisabled("  (当前无 Sample)");
            }

            ImGui.separator();
            
            // 操作按钮
            if (runtime.getMode().isCinematic()) {
                if (ImGui.button("强制切回 GAME 模式")) {
                    runtime.requestGameMode();
                }
            }
        }
        ImGui.end();
    }
}

