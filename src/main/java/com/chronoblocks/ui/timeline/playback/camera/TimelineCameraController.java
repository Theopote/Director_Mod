package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TimelineCameraController v3.0
 *
 * <p>负责：
 * <ul>
 *   <li>从时间线的摄像机轨道采样 CameraSample</li>
 *   <li>根据播放/拖拽状态切换 CameraRuntime 模式</li>
 *   <li>锁定玩家输入（播放时）</li>
 * </ul>
 *
 * <p>⭐ 重要变更：
 * <ul>
 *   <li>不再直接持有 CameraMode / CinematicCamera 状态</li>
 *   <li>所有运行时状态都交给 CameraRuntime</li>
 *   <li>只负责采样和状态切换逻辑</li>
 * </ul>
 */
public final class TimelineCameraController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
    private static final TimelineCameraController INSTANCE = new TimelineCameraController();

    private final CameraTrackSampler sampler = new CameraTrackSampler();
    private TimelineModel timelineModel;
    private boolean scrubbing = false; // 播放头是否正在被拖动
    private boolean keyframesDirty = true;
    private TimelineModel.ModelChangeListener modelChangeListener;

    private TimelineCameraController() {
    }

    public static TimelineCameraController getInstance() {
        return INSTANCE;
    }

    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
        // ⭐ 初始化时设置一次 keyframes，之后只在 keyframe 变化时更新
        if (model != null) {
            sampler.setKeyframes(collectCameraKeyframes());
        }
    }

    /**
     * 当关键帧发生变化时调用（添加/删除/移动关键帧时）
     * 
     * <p>⭐ 关键：不在每帧 update() 中调用 setKeyframes，避免每帧重新排序导致 prev/next 错误
     */
    public void onKeyframeChanged() {
        if (timelineModel != null) {
            sampler.setKeyframes(collectCameraKeyframes());
            LOGGER.debug("[TimelineCameraController] 关键帧变化，更新采样器");
        }
    }

    /**
     * 设置播放头拖拽状态
     * 
     * <p>⭐ 关键：不在这里立即采样，让 update() 统一处理
     * 这样可以确保时序正确，避免每帧 setKeyframes 导致的问题
     */
    public void setScrubbing(boolean scrubbing) {
        this.scrubbing = scrubbing;
        LOGGER.debug("[TimelineCameraController] scrubbing = {}", scrubbing);
    }

    /**
     * 当播放开始时调用（由 TimelineModel 调用）
     */
    public void onPlaybackStarted() {
        LOGGER.debug("[TimelineCameraController] 播放开始");
        // 播放状态会在 update() 中处理，这里可以添加额外的初始化逻辑
    }

    /**
     * 当播放停止时调用（由 TimelineModel 调用）
     */
    public void onPlaybackStopped() {
        LOGGER.debug("[TimelineCameraController] 播放停止");
        // 停止播放时，如果不在拖拽，切回游戏模式
        if (!scrubbing) {
            CameraRuntime.getInstance().enterGameMode();
        }
    }

    /**
     * TimelinePanel 每帧调用。
     * 
     * <p>⭐ 摄像机模式切换规则（完整版）：
     * <ul>
     *   <li>1. 没有相机关键帧 → 永远回 GAME</li>
     *   <li>2. 播放中 + 有摄像机轨道 → PLAYING 模式</li>
     *   <li>3. 非播放 + 拖拽播放头 + 有摄像机轨道 → PREVIEW 模式</li>
     *   <li>4. 既不播放也不拖动 → GAME 模式</li>
     * </ul>
     * 
     * <p>⭐ 关键修复：不在每帧 setKeyframes，避免每帧重新排序导致 prev/next 错误
     */
    public void update() {
        if (timelineModel == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        CameraRuntime runtime = CameraRuntime.getInstance();

        // ⭐ 规则 1：没有相机关键帧 → 永远回 GAME
        // ⭐ 关键：不在这里调用 setKeyframes，只在 keyframe 变化时调用
        if (sampler.isEmpty()) {
            if (runtime.getMode().isCinematic()) {
                runtime.requestGameMode();
                LOGGER.debug("[TimelineCameraController] 没有摄像机关键帧，切回 GAME 模式");
            }
            return;
        }

        // ⭐ 获取播放和拖拽状态（统一来源）
        boolean isPlaying = timelineModel.isPlaying();
        boolean isScrubbing = this.scrubbing;
        float currentTime = timelineModel.getCurrentTime();

        // ⭐ 规则 2：播放优先级最高
        if (isPlaying) {
            CameraSample sample = sampler.sample(currentTime).orElse(null);
            if (sample == null) {
                LOGGER.warn("[TimelineCameraController] 播放时无法采样摄像机，切回 GAME");
                runtime.requestGameMode();
                return;
            }

            if (runtime.getMode() != CameraMode.PLAYING) {
                runtime.enterPlayingMode(client, sample, true);
                LOGGER.info("[TimelineCameraController] 进入 PLAYING 模式 (时间: {}s)", currentTime);
            } else {
                runtime.updateSample(sample);
            }

            // 播放时锁定玩家输入
            if (runtime.isCinematicActive()) {
                lockPlayerInput();
            }
            return;
        }

        // ⭐ 规则 3：停止播放，但正在拖动 → 预览
        if (isScrubbing) {
            CameraSample sample = sampler.sample(currentTime).orElse(null);
            if (sample == null) {
                LOGGER.warn("[TimelineCameraController] 拖拽时无法采样摄像机，切回 GAME");
                runtime.requestGameMode();
                return;
            }

            if (runtime.getMode() != CameraMode.PREVIEW) {
                runtime.enterPreviewMode(client, sample, true);
                LOGGER.debug("[TimelineCameraController] 进入 PREVIEW 模式 (时间: {}s)", currentTime);
            } else {
                runtime.updateSample(sample);
            }
            return;
        }

        // ⭐ 规则 4：既不播放也不拖动 → 回玩家视角
        if (runtime.getMode().isCinematic()) {
            runtime.requestGameMode();
            LOGGER.debug("[TimelineCameraController] 既不播放也不拖动，切回 GAME 模式");
        }
    }

    /**
     * 当用户点击某个摄像机关键帧，希望预览画面时，可以调用这个方法。
     *
     * @param timeSeconds 该关键帧对应的时间
     */
    public void previewAt(float timeSeconds) {
        List<CameraKeyframe> keyframes = collectCameraKeyframes();
        sampler.setKeyframes(keyframes);

        MinecraftClient client = MinecraftClient.getInstance();
        CameraRuntime runtime = CameraRuntime.getInstance();
        boolean hasTrack = !keyframes.isEmpty();
        
        if (!hasTrack) {
            runtime.enterPreviewMode(client, null, false);
            return;
        }

        CameraSample sample = sampler.sample(timeSeconds).orElse(null);
        if (sample == null) {
            runtime.enterPreviewMode(client, null, false);
            return;
        }

        runtime.enterPreviewMode(client, sample, true);
    }

    /**
     * 供 TimelinePanel.onAddKeyframeToTrack 使用：
     * 从当前游戏摄像机填充一个 CameraKeyframe。
     */
    public void captureFromGameCamera(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        CameraSample sample = CameraAccessor.sampleGameCamera(client);

        keyframe.setPosition(new Vector3f(
            (float) sample.position().x,
            (float) sample.position().y,
            (float) sample.position().z
        ));
        // ⭐ 正确 yaw/pitch 顺序（rotation.x=pitch, rotation.y=yaw, rotation.z=roll）
        keyframe.setRotation(new Vector3f(
            sample.pitch(),  // x = pitch
            sample.yaw(),    // y = yaw
            sample.roll()     // z = roll
        ));
        keyframe.setFov(sample.fov());
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());

        LOGGER.info("[TimelineCameraController] 捕捉玩家视角到 CameraKeyframe@{}s", keyframe.getTime());
    }

    private void lockPlayerInput() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.input != null) {
            var input = client.player.input;
            input.movementForward = 0.0f;
            input.movementSideways = 0.0f;
            // Note: jumping and sneaking are handled through key bindings
        }

        if (client.options != null) {
            clearKey(client.options.forwardKey);
            clearKey(client.options.backKey);
            clearKey(client.options.leftKey);
            clearKey(client.options.rightKey);
            clearKey(client.options.jumpKey);
            clearKey(client.options.sneakKey);
            clearKey(client.options.sprintKey);
        }
    }

    private void clearKey(KeyBinding key) {
        if (key != null) {
            key.setPressed(false);
        }
    }

    private List<CameraKeyframe> collectCameraKeyframes() {
        List<CameraKeyframe> result = new ArrayList<>();
        if (timelineModel == null) {
            return result;
        }
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type == null || !type.supportsCameraPath() || track.isLocked() || !track.isVisible()) {
                continue;
            }
            for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
                if (selectable instanceof CameraKeyframe ck) {
                    result.add(ck);
                }
            }
        }
        return result;
    }
}
