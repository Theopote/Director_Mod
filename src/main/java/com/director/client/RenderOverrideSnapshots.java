package com.director.client;

import com.director.client.camera.CameraRenderOverride;
import com.director.client.entity.EntityRenderOverride;
import com.director.client.world.WorldRenderOverride;
import com.director.ui.timeline.playback.PlaybackOrchestrator;
import net.minecraft.client.MinecraftClient;

/**
 * 渲染覆盖快照的统一清理/重同步入口。
 */
public final class RenderOverrideSnapshots {

    private RenderOverrideSnapshots() {
    }

    public static void clearAll() {
        WorldRenderOverride.clearAll();
        CameraRenderOverride.clear();
        EntityRenderOverride.clear();
    }

    /**
     * 视频导出结束后在客户端线程重采样，使 owner/override 与当前播放状态一致。
     */
    public static void resyncAfterVideoExport() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            float time = resolveTimelineTime();
            try {
                PlaybackOrchestrator.get().sampleAt(time);
            } catch (RuntimeException ignored) {
            }
            try {
                com.director.timeline.playback.camera.TimelineCameraController.getInstance().update();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private static float resolveTimelineTime() {
        var service = com.director.core.DirectorServices.get().containerTimeline();
        if (service == null) {
            return 0f;
        }
        var model = service.getTimelineModel();
        return model != null ? model.getCurrentTime() : 0f;
    }
}
