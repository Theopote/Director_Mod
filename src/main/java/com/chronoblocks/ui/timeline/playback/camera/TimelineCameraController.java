package com.chronoblocks.ui.timeline.playback.camera;

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
     */
    public void beginScrubbing(float timeSeconds) {
        this.previewing = false;
        
        // 立即采样并应用
        sampler.setKeyframes(collectCameraKeyframes());
        if (!sampler.isEmpty()) {
            CameraSample sample = sampler.sample(timeSeconds).orElse(null);
            if (sample != null) {
                CameraRuntime runtime = CameraRuntime.getInstance();
                runtime.applyTimelineSample(sample);
                runtime.setOwner(CameraRuntime.Owner.TIMELINE);
                LOGGER.debug("[TimelineCameraController] 开始 scrubbing @ {:.2f}s", timeSeconds);
            }
        }
    }

    /**
     * ⭐ UI 事件驱动：更新拖动播放头位置
     * 由 TimelineInteractionHandler 在鼠标拖动时调用
     * 
     * <p>注意：scrubbing 状态由 TimelineModel.isScrubbing() 检查
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
                CameraRuntime runtime = CameraRuntime.getInstance();
                runtime.applyTimelineSample(sample);
                runtime.setOwner(CameraRuntime.Owner.TIMELINE);
            }
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
        
        // 将当前状态同步到玩家实体，然后释放控制权
        bakeCurrentSampleIntoGameAndExit();
        
        LOGGER.debug("[TimelineCameraController] 结束 scrubbing @ {:.2f}s", timeSeconds);
    }

    /**
     * ⭐ UI 事件驱动：开始播放
     * 由 TimelineModel.setPlaying(true) 通过 startPlayback() 调用
     */
    public void startPlayback() {
        this.previewing = false;
        // scrubbing 状态由 TimelineModel 管理，这里不需要设置
        // 播放状态由 TimelineModel.isPlaying() 控制，这里只负责摄像机
        LOGGER.debug("[TimelineCameraController] 开始播放");
    }

    /**
     * ⭐ UI 事件驱动：停止播放
     * 由 TimelineModel.setPlaying(false) 通过 stopPlayback() 调用
     */
    public void stopPlayback() {
        this.previewing = false;
        // scrubbing 状态由 TimelineModel 管理，这里不需要设置
        CameraRuntime.getInstance().setOwner(CameraRuntime.Owner.PLAYER);
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
     * 主更新函数（TimelinePanel 每帧调用）。
     * ⭐ UI 状态驱动：完全根据 TimelineModel 的状态来决定摄像机控制权
     * 
     * <p>更新逻辑：
     * <ul>
     *   <li>如果正在播放：采样当前时间点，时间线接管</li>
     *   <li>如果正在 scrubbing：由 beginScrubbing/updateScrubbing 处理，这里不重复采样</li>
     *   <li>如果只是预览：采样预览时间点</li>
     *   <li>否则：玩家控制</li>
     * </ul>
     */
    public void update() {
        if (timelineModel == null) return;

        CameraRuntime runtime = CameraRuntime.getInstance();
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing(); // ⭐ 使用 model 的状态作为单一真相源
        boolean active = playing || scrubbing || previewing;

        sampler.setKeyframes(collectCameraKeyframes());

        // 没有摄像机关键帧 → 永远玩家控制
        if (sampler.isEmpty()) {
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
            return;
        }

        if (!active) {
            // 不在播放、不在拖动、也没有预览 → 玩家控制
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
            return;
        }

        // ⭐ 关键修复：scrubbing 状态由 beginScrubbing/updateScrubbing 处理，这里只处理播放和预览
        if (scrubbing) {
            // scrubbing 状态下的采样由 updateScrubbing() 处理，这里不需要重复采样
            // 但需要确保 owner 是 TIMELINE（由 beginScrubbing/updateScrubbing 设置）
            return;
        }

        float time;
        if (playing) {
            time = timelineModel.getCurrentTime();
        } else {
            // 纯预览模式（点击关键帧预览）
            time = previewTimeSeconds;
        }

        CameraSample sample = sampler.sample(time).orElse(null);
        if (sample == null) {
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
            return;
        }

        // 时间线接管：写入 sample + 切 owner
        runtime.applyTimelineSample(sample);
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
    }

    // ================== 预览相关 ==================

    /**
     * 当用户点击摄像机关键帧进行预览时调用。
     */
    public void previewAt(float timeSeconds) {
        this.previewing = true;
        this.previewTimeSeconds = timeSeconds;

        sampler.setKeyframes(collectCameraKeyframes());
        if (sampler.isEmpty()) {
            CameraRuntime.getInstance().setOwner(CameraRuntime.Owner.PLAYER);
            return;
        }

        CameraSample sample = sampler.sample(timeSeconds).orElse(null);
        if (sample == null) {
            CameraRuntime.getInstance().setOwner(CameraRuntime.Owner.PLAYER);
            return;
        }

        CameraRuntime runtime = CameraRuntime.getInstance();
        runtime.applyTimelineSample(sample);
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
    }

    public void clearPreview() {
        this.previewing = false;
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
     * 捕捉关键帧：从“当前相机状态”采样。
     * 无论此刻是玩家视角还是时间线播放中的视角，都会得到和之后播放一致的坐标系。
     */
    public void captureFromCurrentCamera(CameraKeyframe keyframe) {
        if (keyframe == null) return;

        CameraSample sample = CameraRuntime.getInstance().getCurrentSample();
        if (sample == null) return;

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
