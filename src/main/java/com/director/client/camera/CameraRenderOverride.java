package com.director.client.camera;

import com.director.timeline.playback.camera.CameraRuntime;
import com.director.timeline.playback.camera.CameraSample;
import net.minecraft.util.math.Vec3d;

/**
 * 摄像机渲染覆盖的只读快照（Mixin 层唯一依赖入口）。
 * <p>
 * 由 {@link CameraRuntime} / {@link com.director.timeline.playback.camera.TimelineCameraController}
 * 写入；Mixin 只读，不依赖 Controller 单例。
 */
public final class CameraRenderOverride {

    private static final CameraRenderOverride INSTANCE = new CameraRenderOverride();

    private volatile boolean timelineOwner;
    private volatile CameraSample sample = CameraSample.EMPTY;
    /** 播放或 scrubbing 中（用于输入 Mixin 判断是否应释放时间线接管） */
    private volatile boolean timelineActive;

    private CameraRenderOverride() {
    }

    public static CameraRenderOverride get() {
        return INSTANCE;
    }

    public boolean isTimelineOwner() {
        return timelineOwner;
    }

    public boolean isPlayerOwner() {
        return !timelineOwner;
    }

    public CameraSample sample() {
        CameraSample s = sample;
        return s != null ? s : CameraSample.EMPTY;
    }

    public boolean isTimelineActive() {
        return timelineActive;
    }

    // ==================== Publish (playback layer) ====================

    public static void publish(boolean timelineOwner, CameraSample sample) {
        CameraRenderOverride state = INSTANCE;
        state.timelineOwner = timelineOwner;
        state.sample = sample != null ? sample : CameraSample.EMPTY;
    }

    public static void publishTimelineActive(boolean active) {
        INSTANCE.timelineActive = active;
    }

    /**
     * Mixin 在 {@code Camera.update} 之后上报玩家视角（仅 owner=PLAYER 时写入 runtime）。
     */
    public static void reportPlayerView(Vec3d pos, float yaw, float pitch, float fov) {
        CameraRuntime.getInstance().updateFromGameCamera(pos, yaw, pitch, fov);
    }

    public static void clear() {
        publish(false, CameraSample.EMPTY);
        publishTimelineActive(false);
    }
}
