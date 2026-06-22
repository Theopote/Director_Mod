package com.director.client;

import com.director.client.camera.CameraRenderOverride;
import com.director.client.entity.EntityRenderOverride;
import com.director.client.world.WorldRenderOverride;
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
            try {
                com.director.timeline.playback.camera.TimelineCameraController.getInstance().update();
            } catch (RuntimeException ignored) {
            }
            try {
                com.director.timeline.playback.time.TimelineWorldController.getInstance().update();
            } catch (RuntimeException ignored) {
            }
            try {
                com.director.timeline.playback.season.TimelineSeasonController.getInstance().update();
            } catch (RuntimeException ignored) {
            }
            try {
                com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
                        .updateForPlayback(resolveTimelineTime());
            } catch (RuntimeException ignored) {
            }
            try {
                com.director.timeline.playback.entity.TimelineActorController.getInstance()
                        .updateForPlayback(resolveTimelineTime());
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
