package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TimelineCameraController v3.0（统一相机系统版）
 *
 * 职责：
 * - 从 TimelineModel 收集摄像机关键帧，并交给 CameraTrackSampler 插值
 * - 每帧根据 currentTime 采样 CameraSample，写入 CameraRuntime
 * - 根据播放 / 拖动 / 预览状态，切换 CameraRuntime 的 owner（PLAYER / TIMELINE）
 * - 捕捉关键帧时，从 CameraRuntime.currentSample 读取当前视角
 */
public final class TimelineCameraController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
    private static final TimelineCameraController INSTANCE = new TimelineCameraController();

    /** 时间线数据模型（由 TimelinePanel 注入） */
    private TimelineModel timelineModel;

    /** 负责摄像机关键帧插值的采样器 */
    private final CameraTrackSampler sampler = new CameraTrackSampler();

    /** 是否处于"单帧预览关键帧"的状态 */
    private boolean previewing = false;
    private float previewTimeSeconds = 0f;
    
    // ⭐ 修复：scrubbing 状态完全由 TimelineModel.isScrubbing() 管理，不再使用内部变量

    /** 监听模型变更，自动刷新摄像机关键帧列表 */
    private TimelineModel.ModelChangeListener modelChangeListener;

    private TimelineCameraController() {}

    public static TimelineCameraController getInstance() {
        return INSTANCE;
    }

    // ================== 生命周期 / 绑定模型 ==================

    /**
     * 绑定 TimelineModel。
     * 会自动注册 ModelChangeListener，在模型变更时刷新摄像机关键帧。
     */
    public void setTimelineModel(TimelineModel model) {
        // 解除旧监听
        if (this.timelineModel != null && modelChangeListener != null) {
            this.timelineModel.removeModelChangeListener(modelChangeListener);
        }

        this.timelineModel = model;

        if (model != null) {
            if (modelChangeListener == null) {
                modelChangeListener = changeType -> {
                    sampler.setKeyframes(collectCameraKeyframes());
                    LOGGER.debug("[TimelineCameraController] 模型变更({})，刷新摄像机关键帧", changeType);
                };
            }
            model.addModelChangeListener(modelChangeListener);
            sampler.setKeyframes(collectCameraKeyframes());
        } else {
            sampler.setKeyframes(new ArrayList<>());
        }
    }

    /**
     * 当关键帧发生变化时可以显式调用（例如某些命令直接操作 keyframe 列表）。
     */
    public void onKeyframeChanged() {
        if (timelineModel != null) {
            sampler.setKeyframes(collectCameraKeyframes());
            LOGGER.debug("[TimelineCameraController] onKeyframeChanged()，刷新采样器");
        }
    }

    // ================== 播放 / 拖动 / 预览 状态控制 ==================

    /**
     * ⭐ UI 事件驱动：开始拖动播放头（scrubbing）
     * 由 TimelineInteractionHandler 在鼠标按下播放头时调用
     * 
     * <p>注意：scrubbing 状态由 TimelineModel.setScrubbing(true) 设置，这里只负责摄像机采样
     * <p>⭐ 关键修复：即使没有关键帧，也要设置 owner = TIMELINE，确保 scrubbing 状态正确
     */
    public void beginScrubbing(float timeSeconds) {
        this.previewing = false;
        
        sampler.setKeyframes(collectCameraKeyframes());
        sampler.sample(timeSeconds).ifPresent(CameraRuntime.getInstance()::applyTimelineSample);
        LOGGER.debug("[TimelineCameraController] 开始 scrubbing @ {}s", String.format("%.2f", timeSeconds));
    }

    /**
     * ⭐ UI 事件驱动：更新拖动播放头位置
     * 由 TimelineInteractionHandler 在鼠标拖动时调用
     * 
     * <p>注意：scrubbing 状态由 TimelineModel.isScrubbing() 检查
     * <p>⭐ 关键修复：只更新 sample，不重复设置 owner（owner 已在 beginScrubbing 时设置）
     */
    public void updateScrubbing(float timeSeconds) {
        // 检查 model 的状态（单一真相源）
        if (timelineModel == null || !timelineModel.isScrubbing()) {
            // 如果不在 scrubbing 状态，忽略更新
            return;
        }
        
        sampler.setKeyframes(collectCameraKeyframes());
        if (!sampler.isEmpty()) {
            CameraSample sample = sampler.sample(timeSeconds).orElse(null);
            if (sample != null) {
                // ⭐ 关键修复：只更新 sample，不重复设置 owner
                // owner 已在 beginScrubbing() 时设置为 TIMELINE，这里不需要重复设置
                CameraRuntime.getInstance().applyTimelineSample(sample);
            } else {
                LOGGER.debug("[TimelineCameraController] updateScrubbing @ {}s: 采样失败", String.format("%.2f", timeSeconds));
            }
        } else {
            LOGGER.debug("[TimelineCameraController] updateScrubbing @ {}s: 没有关键帧", String.format("%.2f", timeSeconds));
        }
    }

    /**
     * ⭐ UI 事件驱动：结束拖动播放头
     * 由 TimelineInteractionHandler 在鼠标释放时调用
     * 
     * <p>注意：scrubbing 状态由 TimelineModel.setScrubbing(false) 设置，这里只负责摄像机状态同步
     */
    public void endScrubbing(float timeSeconds) {
        // 检查 model 的状态（单一真相源）
        if (timelineModel == null || !timelineModel.isScrubbing()) {
            return;
        }
        
        this.previewing = false;
        
        LOGGER.debug("[TimelineCameraController] 结束 scrubbing @ {}s", String.format("%.2f", timeSeconds));
    }

    /**
     * ⭐ UI 事件驱动：开始播放
     * 由 TimelineModel.setPlaying(true) 通过 startPlayback() 调用
     * 
     * <p>⭐ 关键修复：在事件发生时设置 owner，而不是在 update() 中每帧设置
     */
    public void startPlayback() {
        this.previewing = false;
        sampler.setKeyframes(collectCameraKeyframes());
        LOGGER.debug("[TimelineCameraController] 开始播放");
    }

    /**
     * ⭐ UI 事件驱动：停止播放
     * 由 TimelineModel.setPlaying(false) 通过 stopPlayback() 调用
     */
    public void stopPlayback() {
        this.previewing = false;
        LOGGER.debug("[TimelineCameraController] 停止播放");
    }

    /**
     * @deprecated 使用 beginScrubbing/updateScrubbing/endScrubbing 代替
     * scrubbing 状态现在完全由 TimelineModel 管理
     */
    @Deprecated
    public void setScrubbing(boolean scrubbing) {
        // 不再使用内部变量，状态由 TimelineModel 管理
        if (timelineModel != null) {
            timelineModel.setScrubbing(scrubbing);
        }
        if (!scrubbing) {
            this.previewing = false;
        }
    }

    /** 由 TimelineModel.setPlaying(true) 通知 */
    public void onPlaybackStarted() {
        LOGGER.debug("[TimelineCameraController] 播放开始");
        startPlayback();
    }

    /** 由 TimelineModel.setPlaying(false) 通知 */
    public void onPlaybackStopped() {
        LOGGER.debug("[TimelineCameraController] 播放停止");
        stopPlayback();
    }

    /**
     * UI 关闭时调用，彻底释放时间线对摄像机的控制，恢复玩家控制。
     * 由 ChronoBlocksScreen.close() 统一调用一次。
     */
    public void onTimelineUiClosed() {
        this.previewing = false;
        // scrubbing 状态由 TimelineModel 管理，这里不需要设置
        if (timelineModel != null) {
            timelineModel.setPlaying(false);
            timelineModel.setScrubbing(false);
        }
        CameraRuntime.getInstance().reset();
        LOGGER.debug("[TimelineCameraController] UI 关闭，释放摄像机控制权");
    }

    // ================== 每帧更新（由 TimelinePanel 调用） ==================

    /**
     * TimelinePanel 每帧调用：根据播放/拖动状态切换 owner 并采样。
     */
    public void updateOwnerAndSample(float deltaSeconds) {
        if (timelineModel == null) {
            return;
        }

        sampler.setKeyframes(collectCameraKeyframes());

        CameraRuntime runtime = CameraRuntime.getInstance();
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();
        boolean previewActive = previewing && !playing && !scrubbing;
        boolean shouldTimeline = playing || scrubbing || previewActive;

        if (shouldTimeline && !runtime.isTimelineOwner()) {
            // 接管前先用玩家当前位置作为锚点，弯折轨道避免跳变
            CameraSample playerSample = runtime.getCurrentSample();
            if (playerSample == null) {
                playerSample = CameraAccessor.sampleFromGameCamera(MinecraftClient.getInstance());
            }
            float startTime = timelineModel.getCurrentTime();
            sampler.ensureAnchorAt(startTime, playerSample);

            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
            ChronoBlocksClientController.getInstance().setHideCrosshair(true);
            LOGGER.debug("[CameraController] 接管相机 (时间: {}s)", String.format("%.2f", startTime));
        } else if (!shouldTimeline && runtime.isTimelineOwner()) {
            // 退出时轻微 lerp 回玩家相机，避免瞬移
            CameraSample lastSample = runtime.getCurrentSample();
            runtime.applyLerpToPlayer(lastSample, 0.1f);
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
            ChronoBlocksClientController.getInstance().setHideCrosshair(false);
            LOGGER.debug("[CameraController] 恢复玩家控制");
        }

        if (runtime.isTimelineOwner()) {
            float time = playing || scrubbing ? timelineModel.getCurrentTime() : previewTimeSeconds;
            sampler.sample(time).ifPresent(runtime::applyTimelineSample);
        } else {
            updateFromGameCamera();
            runtime.tickPlayerLerp(deltaSeconds);
        }
    }

    /**
     * 兼容旧接口：沿用 update 名称，但委托给 updateOwnerAndSample。
     */
    public void update() {
        MinecraftClient client = MinecraftClient.getInstance();
        float delta = 0f;
        if (client != null && client.getRenderTickCounter() != null) {
            delta = client.getRenderTickCounter().getTickDelta(false);
        }
        updateOwnerAndSample(delta);
    }

    private void updateFromGameCamera() {
        CameraSample sample = CameraAccessor.sampleFromGameCamera(MinecraftClient.getInstance());
        if (sample != null) {
            CameraRuntime.getInstance().applyTimelineSample(sample);
        }
    }

    // ================== 预览相关 ==================

    /**
     * 当用户点击摄像机关键帧进行预览时调用。
     * ⭐ 注意：预览会设置 owner = TIMELINE，如果用户想添加关键帧，需要先退出预览
     */
    public void previewAt(float timeSeconds) {
        this.previewing = true;
        this.previewTimeSeconds = timeSeconds;

        sampler.setKeyframes(collectCameraKeyframes());
        CameraRuntime runtime = CameraRuntime.getInstance();
        
        if (sampler.isEmpty()) {
            // ⭐ 修复：只在确实需要切换时才切换
            if (runtime.getOwner() == CameraRuntime.Owner.TIMELINE) {
                runtime.setOwner(CameraRuntime.Owner.PLAYER);
            }
            return;
        }

        CameraSample sample = sampler.sample(timeSeconds).orElse(null);
        if (sample == null) {
            // ⭐ 修复：只在确实需要切换时才切换
            if (runtime.getOwner() == CameraRuntime.Owner.TIMELINE) {
                runtime.setOwner(CameraRuntime.Owner.PLAYER);
            }
            return;
        }

        // ⭐ 关键修复：在事件发生时设置 owner，而不是在 update() 中每帧设置
        runtime.applyTimelineSample(sample);
        LOGGER.debug("[TimelineCameraController] 预览 @ {}s，准备接管相机", String.format("%.2f", timeSeconds));
    }

    public void clearPreview() {
        this.previewing = false;
    }

    /**
     * 点击时间标尺时：只采样并把镜头落到玩家态，允许立即调整。
     */
    public void jumpToTimeAsPlayer(float timeSeconds) {
        this.previewing = false;

        sampler.setKeyframes(collectCameraKeyframes());
        CameraSample sample = sampler.sample(timeSeconds).orElse(null);
        if (sample == null) {
            return;
        }

        CameraRuntime runtime = CameraRuntime.getInstance();
        runtime.applyTimelineSample(sample);
        // 将镜头位置直接写到玩家实体 / 相机
        CameraAccessor.applySampleToGameCamera(MinecraftClient.getInstance(), sample);
        // 保持玩家 owner，允许继续移动、录制下一帧
        runtime.setOwner(CameraRuntime.Owner.PLAYER);
    }

    /**
     * 拖动播放头结束时调用。
     * 将当前摄像机状态同步到玩家实体，然后释放控制权给玩家。
     * 确保玩家从当前位置继续移动，不会有跳变。
     */
    public void bakeCurrentSampleIntoGameAndExit() {
        CameraRuntime runtime = CameraRuntime.getInstance();
        CameraSample currentSample = runtime.getCurrentSample();
        
        if (currentSample != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                // 将当前摄像机状态同步到玩家实体
                client.player.setPos(currentSample.position().x, currentSample.position().y, currentSample.position().z);
                client.player.setYaw(currentSample.yaw());
                client.player.setPitch(currentSample.pitch());
                LOGGER.debug("[TimelineCameraController] 将摄像机状态同步到玩家实体");
            }
        }
        
        // 释放控制权给玩家
        runtime.setOwner(CameraRuntime.Owner.PLAYER);
        // scrubbing 状态由 TimelineModel 管理，这里不需要设置
        this.previewing = false;
        LOGGER.debug("[TimelineCameraController] 释放摄像机控制权给玩家");
    }

    // ================== 关键帧捕捉 ==================

    /**
     * 捕捉关键帧：从"当前相机状态"采样。
     * 
     * ⭐ 关键修复：确保设置关键帧时：
     * 1. owner 是 PLAYER，玩家可以继续移动摄像机设置下一个关键帧
     * 2. 立即从游戏摄像机读取，而不是等待下一帧更新 CameraRuntime
     * 3. 捕获的参数与播放时一致（使用同一套插值算法）
     * 
     * <p>工作流程（参考专业动画软件如 Blender、After Effects）：
     * <ul>
     *   <li>如果当前 owner 是 TIMELINE（预览/播放状态），先切回 PLAYER</li>
     *   <li>立即从游戏摄像机直接采样（不等待 CameraMixin 更新）</li>
     *   <li>如果 owner 已经是 PLAYER，也从游戏摄像机直接采样（确保是最新状态）</li>
     *   <li>将采样结果同步到 CameraRuntime，然后设置关键帧属性</li>
     * </ul>
     * 
     * <p>关键原则：
     * - 设置关键帧时：从游戏摄像机直接读取（玩家当前视角）
     * - 播放关键帧时：从关键帧插值得到 sample（时间线视角）
     * - 两者使用同一套数据源（CameraRuntime.currentSample）和插值算法
     */
    public void captureFromCurrentCamera(CameraKeyframe keyframe) {
        if (keyframe == null) return;

        CameraRuntime runtime = CameraRuntime.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();

        // 只允许玩家模式下捕捉，避免时间线接管时错采样
        if (!runtime.isPlayerOwner()) {
            LOGGER.warn("[TimelineCameraController] 当前非玩家控制，忽略关键帧捕捉");
            return;
        }
        
        CameraSample sample = CameraAccessor.sampleFromGameCamera(client);
        if (sample == null) {
            // 如果无法从游戏摄像机采样，尝试从 CameraRuntime 读取（降级方案）
            sample = runtime.getCurrentSample();
            if (sample == null) {
                LOGGER.warn("[TimelineCameraController] 无法获取当前相机状态，无法设置关键帧");
                return;
            }
            LOGGER.warn("[TimelineCameraController] 从游戏摄像机采样失败，使用 CameraRuntime 中的状态（可能不是最新）");
        } else {
            // 将采样结果同步到 CameraRuntime，保持状态一致
            runtime.applyTimelineSample(sample);
            // 注意：这里不设置 owner，因为已经是 PLAYER 了
        }

        keyframe.setPosition(new Vector3f(
                (float) sample.position().x,
                (float) sample.position().y,
                (float) sample.position().z
        ));

        // rotation.x = pitch, rotation.y = yaw, rotation.z = roll
        keyframe.setRotation(new Vector3f(
                sample.pitch(),  // x = pitch
                sample.yaw(),    // y = yaw
                sample.roll()    // z = roll
        ));
        keyframe.setFov(sample.fov());
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());

        LOGGER.info("[TimelineCameraController] 捕捉当前视角到 CameraKeyframe@{}s", keyframe.getTime());
    }

    // ================== 内部工具：收集摄像机关键帧 ==================

    private List<CameraKeyframe> collectCameraKeyframes() {
        List<CameraKeyframe> list = new ArrayList<>();
        if (timelineModel == null) return list;

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type == null || !type.supportsCameraPath() || track.isLocked() || !track.isVisible()) {
                continue;
            }
            for (TimelineSelectable s : track.getAutomationKeyframes()) {
                if (s instanceof CameraKeyframe ck) {
                    list.add(ck);
                }
            }
        }
        return list;
    }
}
