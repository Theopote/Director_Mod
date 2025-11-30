package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CameraRuntime v2.0 - 摄像机运行时状态管理器（统一状态中枢）
 * 
 * <p>统一管理：
 * <ul>
 *   <li>当前摄像机模式（GAME / PREVIEW / PLAYING）</li>
 *   <li>当前电影摄像机 sample</li>
 *   <li>玩家原始摄像机 snapshot（用于从电影模式回到玩家模式）</li>
 * </ul>
 * 
 * <p>⭐ 关键变更：
 * <ul>
 *   <li>不再使用 CinematicCamera，直接使用 CameraSample</li>
 *   <li>Mixin 和 TimelineCameraController 都只跟它打交道</li>
 *   <li>统一的状态切换入口，避免状态不一致</li>
 * </ul>
 */
public final class CameraRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRuntime.class);
    
    private static final CameraRuntime INSTANCE = new CameraRuntime();

    private CameraMode mode = CameraMode.GAME;

    /** 当前正在使用的电影摄像机 sample（PREVIEW / PLAYING 时不为 null） */
    private CameraSample activeSample = null;

    /** 记录进入电影模式前的玩家视角 snapshot，用于恢复 */
    private CameraSample gameSnapshot = null;

    /** 是否已经捕捉过玩家 snapshot */
    private boolean hasGameSnapshot = false;

    private CameraRuntime() {
    }

    public static CameraRuntime getInstance() {
        return INSTANCE;
    }

    // ========= 对外状态查询 =========

    public CameraMode getMode() {
        return mode;
    }

    public boolean isCinematicActive() {
        return mode.isCinematic() && activeSample != null;
    }

    public CameraSample getActiveSample() {
        return activeSample;
    }

    public CameraSample getGameSnapshot() {
        return gameSnapshot;
    }

    public boolean hasGameSnapshot() {
        return hasGameSnapshot;
    }

    // ========= 模式切换 =========

    /**
     * 统一的"请求回到游戏模式"入口（推荐使用）
     * 
     * <p>所有"回到玩家视角"的地方都应该调用这个方法，包括：
     * <ul>
     *   <li>时间线播放结束</li>
     *   <li>用户点击"停止播放"按钮</li>
     *   <li>用户停止 scrubbing</li>
     *   <li>用户点击"退出摄像机预览"按钮</li>
     * </ul>
     */
    public void requestGameMode() {
        enterGameMode(null);
    }

    /**
     * 进入 Game 模式：恢复玩家摄像机，停止所有时间线摄像机接管
     */
    public void enterGameMode() {
        enterGameMode(null);
    }

    /**
     * 进入 Game 模式：恢复玩家摄像机，停止所有时间线摄像机接管
     */
    public void enterGameMode(MinecraftClient client) {
        if (mode == CameraMode.GAME) {
            return;
        }

        if (client == null) {
            client = MinecraftClient.getInstance();
        }

        // ⭐ 空场景/玩家缺失保护
        if (client == null || client.player == null || client.options == null) {
            LOGGER.warn("[CameraRuntime] 无法恢复玩家视角：客户端或玩家为空");
            mode = CameraMode.GAME;
            activeSample = null;
            return;
        }

        if (hasGameSnapshot && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            // 实际恢复交给 Mixin/FOV 覆盖逻辑，
            // 这里只需要清除 activeSample，并标记模式。
            LOGGER.info("[CameraRuntime] 恢复到玩家摄像机视角");
        } else {
            LOGGER.info("[CameraRuntime] 切回 GAME 模式，但没有可用玩家 snapshot，直接关闭电影摄像机");
        }

        mode = CameraMode.GAME;
        activeSample = null;
        // 不清除 gameSnapshot，保留以便下次使用
    }

    /**
     * 进入电影模式（统一入口，避免重复缓存）
     * 
     * <p>⭐ 关键：同帧生效，强制刷新 Camera 使 Mixin 当帧读取 sample
     * 
     * @param targetMode 目标模式（PREVIEW 或 PLAYING）
     * @param sample 摄像机采样数据
     */
    private void enterCinematicMode(CameraMode targetMode, CameraSample sample) {
        if (targetMode == CameraMode.GAME) {
            throw new IllegalArgumentException("使用 enterGameMode() 而不是 enterCinematicMode(GAME)");
        }

        MinecraftClient client = MinecraftClient.getInstance();
        
        // ⭐ 空场景/玩家缺失保护
        if (client == null || client.player == null || client.options == null) {
            LOGGER.warn("[CameraRuntime] 无法进入电影模式：客户端或玩家为空");
            return;
        }

        // ⭐ 只在从 GAME → CINEMATIC 的那一刻缓存（避免 PREVIEW↔PLAYING 重复缓存）
        if (!mode.isCinematic() && !hasGameSnapshot) {
            gameSnapshot = CameraAccessor.sampleGameCamera(client);
            hasGameSnapshot = true;
            LOGGER.debug("[CameraRuntime] 捕捉玩家摄像机 snapshot (mode: {} → {})", mode, targetMode);
        }

        this.activeSample = sample != null ? sample : CameraSample.EMPTY;
        this.mode = targetMode;

        // ⭐ 关键：同帧生效 - 强制刷新 Camera，使 Mixin 当帧读取 sample 而不是 vanilla FOV
        if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            try {
                // 强制更新 Camera，确保 Mixin 在同一帧应用 sample
                client.gameRenderer.getCamera().update(
                    client.world,
                    client.player,
                    false,
                    false,
                    0f
                );
            } catch (Exception e) {
                // 如果 update 参数不匹配，至少确保 mode 和 sample 已设置
                LOGGER.debug("[CameraRuntime] Camera.update() 调用失败，但模式已切换: {}", e.getMessage());
            }
        }
    }

    /**
     * 进入预览模式（播放头拖动、选中关键帧预览）
     */
    public void enterPreviewMode(boolean hasTrack) {
        enterPreviewMode(null, null, true);
    }

    /**
     * 进入预览模式（播放头拖动、选中关键帧预览）
     */
    public void enterPreviewMode(MinecraftClient client, CameraSample previewSample, boolean captureGameSnapshotIfNeeded) {
        enterCinematicMode(CameraMode.PREVIEW, previewSample);
    }

    /**
     * 进入播放模式（时间线播放）
     */
    public void enterPlayingMode(boolean hasTrack) {
        enterPlayingMode(null, null, true);
    }

    /**
     * 进入播放模式（时间线播放）
     */
    public void enterPlayingMode(MinecraftClient client, CameraSample playingSample, boolean captureGameSnapshotIfNeeded) {
        enterCinematicMode(CameraMode.PLAYING, playingSample);
    }

    /**
     * 更新当前 sample（由 TimelineCameraController 每帧调用）
     */
    public void updateSample(CameraSample sample) {
        if (!mode.isCinematic()) {
            return;
        }
        this.activeSample = sample != null ? sample : CameraSample.EMPTY;
    }

    /**
     * 在世界/游戏结束时清理（可选调用）
     */
    public void reset() {
        mode = CameraMode.GAME;
        activeSample = null;
        gameSnapshot = null;
        hasGameSnapshot = false;
    }

    /**
     * 当 Timeline 完全停止时调用（比如停止按钮 / 播放到末尾）
     */
    public void onTimelineStopped() {
        LOGGER.debug("[CameraRuntime] Timeline stopped, back to GAME mode");
        enterGameMode();
    }
}
