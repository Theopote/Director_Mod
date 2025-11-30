package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 摄像机运行时状态管理器 - 全局唯一入口。
 * <p>
 * 🔹 负责管理当前 CameraMode（GAME / PREVIEW / PLAYING）
 * 🔹 提供全局的 CinematicCamera 实例给 Mixin、时间线等使用
 * 🔹 处理玩家视角缓存与恢复
 */
public final class CameraRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRuntime.class);
    
    private static final CameraRuntime INSTANCE = new CameraRuntime();
    private static final CinematicCamera CINEMATIC_CAMERA = new CinematicCamera();

    private CameraMode currentMode = CameraMode.GAME;
    
    // 可以用于在 UI 显示当前是否有轨道接管摄像机
    private boolean hasActiveCameraTrack = false;

    // 玩家视角缓存
    private Vec3d cachedPlayerPos;
    private float cachedYaw = Float.NaN;
    private float cachedPitch = Float.NaN;
    private float cachedFov = Float.NaN;
    private Perspective cachedPerspective = null;

    private CameraRuntime() {
    }

    public static CameraRuntime getInstance() {
        return INSTANCE;
    }

    public CameraMode getMode() {
        return currentMode;
    }

    public CinematicCamera getCinematicCamera() {
        return CINEMATIC_CAMERA;
    }

    public boolean isCinematicActive() {
        return currentMode.isCinematic() && hasActiveCameraTrack;
    }

    public boolean isPlaying() {
        return currentMode.isPlaying() && hasActiveCameraTrack;
    }

    /**
     * 切换到游戏模式，释放摄像机控制权。
     */
    public void enterGameMode() {
        if (currentMode != CameraMode.GAME) {
            LOGGER.debug("[CameraRuntime] Switch mode → GAME");
            restorePlayerView();
        }
        currentMode = CameraMode.GAME;
        hasActiveCameraTrack = false;
        CINEMATIC_CAMERA.clear();
    }

    /**
     * 预览某个关键帧或时间点时调用。
     */
    public void enterPreviewMode(boolean hasTrack) {
        if (currentMode == CameraMode.GAME) {
            cachePlayerView();
        }
        currentMode = CameraMode.PREVIEW;
        hasActiveCameraTrack = hasTrack;
        LOGGER.debug("[CameraRuntime] Switch mode → PREVIEW (hasTrack={})", hasTrack);
    }

    /**
     * 时间线播放时调用。
     */
    public void enterPlayingMode(boolean hasTrack) {
        if (currentMode == CameraMode.GAME) {
            cachePlayerView();
        }
        currentMode = CameraMode.PLAYING;
        hasActiveCameraTrack = hasTrack;
        LOGGER.debug("[CameraRuntime] Switch mode → PLAYING (hasTrack={})", hasTrack);
    }

    /**
     * 从采样结果更新 CinematicCamera。
     */
    public void applySample(CameraSample sample) {
        if (sample == null) {
            CINEMATIC_CAMERA.clear();
            return;
        }
        CINEMATIC_CAMERA.applySample(sample);
    }

    /**
     * 兼容旧 API
     */
    public void exitToGame() {
        enterGameMode();
    }

    /**
     * 当 Timeline 完全停止时调用（比如停止按钮 / 播放到末尾）。
     */
    public void onTimelineStopped() {
        LOGGER.debug("[CameraRuntime] Timeline stopped, back to GAME mode");
        enterGameMode();
    }

    private void cachePlayerView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            cachedPlayerPos = client.player.getPos();
            cachedYaw = client.player.getYaw();
            cachedPitch = client.player.getPitch();
        }
        if (client.options != null && client.options.getFov() != null) {
            cachedFov = client.options.getFov().getValue().floatValue();
            cachedPerspective = client.options.getPerspective();
        }
    }

    private void restorePlayerView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && cachedPlayerPos != null) {
            client.player.setPosition(cachedPlayerPos);
            client.player.setYaw(cachedYaw);
            client.player.setPitch(cachedPitch);
        }
        if (!Float.isNaN(cachedFov) && client.options != null && client.options.getFov() != null) {
            client.options.getFov().setValue((int) cachedFov);
        }
        if (cachedPerspective != null && client.options != null) {
            client.options.setPerspective(cachedPerspective);
        }
        cachedPlayerPos = null;
        cachedYaw = Float.NaN;
        cachedPitch = Float.NaN;
        cachedFov = Float.NaN;
        cachedPerspective = null;
    }
}
