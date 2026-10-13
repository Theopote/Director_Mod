package com.director.client;

import com.director.client.camera.CameraRenderOverride;
import com.director.client.entity.EntityRenderOverride;
import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.TimelinePlaybackSampler;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 渲染覆盖快照的统一清理/重同步入口。
 */
public final class RenderOverrideSnapshots {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderOverrideSnapshots.class);

    private RenderOverrideSnapshots() {
    }

    public static void clearAll() {
        com.director.lighting.PointLightRuntime.clear();
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
            // best-effort：导出收尾阶段世界/服务可能正在 teardown，失败不阻断 finally
            try {
                TimelinePlaybackSampler.sampleWorldAt(time);
            } catch (RuntimeException e) {
                LOGGER.debug("[RenderOverrideSnapshots] resync sampleWorldAt failed (best-effort)", e);
            }
            try {
                com.director.timeline.playback.camera.TimelineCameraController.getInstance().update();
            } catch (RuntimeException e) {
                LOGGER.debug("[RenderOverrideSnapshots] resync camera update failed (best-effort)", e);
            }
        });
    }

    private static float resolveTimelineTime() {
        var model = TimelinePlaybackSampler.resolveModel();
        return model != null ? model.getCurrentTime() : 0f;
    }
}
