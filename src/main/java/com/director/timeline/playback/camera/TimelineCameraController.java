package com.director.timeline.playback.camera;

import com.director.client.DirectorClientController;
import com.director.client.camera.CameraRenderOverride;
import com.director.client.renderer.CameraPathRenderer;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.AbstractModelTimelinePlaybackController;
import com.director.timeline.playback.TimelinePlaybackThreading;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * TimelineCameraController v5.0 (Multi-Camera Tracks)
 * <p>
 * 职责：
 * - 管理多条 CAMERA 轨道，但任意时刻只有一条为 active
 * - 从激活轨道收集摄像机关键帧，交给 TimelineCameraPathManager 生成轨迹
 * - 根据播放 / 拖动 / 预览状态采样 CameraSample，写入 CameraRuntime
 * - 在需要时接管/释放相机控制权（CameraRuntime.Owner）
 * - 提供「从当前视角捕捉关键帧」等高级操作
 *
 * @see TimelinePlaybackThreading
 */
public final class TimelineCameraController extends AbstractModelTimelinePlaybackController<CameraSample> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
    private static final TimelineCameraController INSTANCE = new TimelineCameraController();

    /** 每帧采样用的核心工具（仅对 activeTrack 的关键帧生效） */
    private final CameraTrackSampler sampler = new CameraTrackSampler();

    /** 用于 UI / 世界渲染的路径采样管理 */
    private final TimelineCameraPathManager pathManager = new TimelineCameraPathManager();

    /**
     * 性能优化：摄像机轨道数据是否需要重建（keyframes/path）。
     *
     * <p>
     * 之前在 updateOwnerAndSample() 中每帧 rebuildFromActiveTrack()，会频繁分配/排序，导致播放掉帧。
     * </p>
     */
    private boolean cameraDataDirty = true;

    /** 是否处于「单帧预览关键帧」的状态 */
    private boolean previewing = false;
    private float previewTimeSeconds = 0f;
    private CameraSample previewSample = null;

    // scrubbing 状态完全由 TimelineModel.isScrubbing() 管理

    /** 帧间隔计时器：用于计算精确的 delta seconds */
    private long lastUpdateNanoTime = 0L;

    private TimelineCameraController() {
        super(LOGGER, true);
        CameraPathRenderer.getInstance().setPathManager(pathManager);
    }

    public static TimelineCameraController getInstance() {
        return INSTANCE;
    }

    @Override
    protected String logTag() {
        return "TimelineCamera";
    }

    @Override
    protected TrackType expectedTrackType() {
        return TrackType.CAMERA;
    }

    @Override
    protected void onModelDetached() {
        sampler.setKeyframes(new ArrayList<>());
        pathManager.setKeyframes(new ArrayList<>());
        cameraDataDirty = false;
        sampler.setCameraTrack(null);
        sampler.resetPathState();
        CameraRuntime.getInstance().reset();
        CameraRenderOverride.publishTimelineActive(false);
        setCameraInputHidden(false);
        previewing = false;
        previewSample = null;
    }

    @Override
    protected void onSingleTrackAutoActivatedOnModelChange(TimelineTrack track) {
        logger.info("[TimelineCamera] 模型变更：检测到只有一个摄像机轨道，自动激活: {}", track.getName());
    }

    @Override
    protected void onActiveTrackRemovedFromModel(TimelineTrack fallback) {
        if (fallback != null) {
            logger.info("[TimelineCamera] 模型变更：当前激活轨道无效，切换到: {}", fallback.getName());
        }
    }

    @Override
    protected void onModelStructureChangedComplete() {
        logger.debug("[TimelineCameraController] 模型变更，刷新摄像机关键帧");
    }

    @Override
    protected synchronized void activateTrack(TimelineTrack track) {
        if (track == activeTrack) return;
        previewSample = null;
        sampler.resetPathState();
        cameraDataDirty = true;
        super.activateTrack(track);
    }

    // ================== 生命周期 / 绑定模型 ==================

    /**
     * 绑定 TimelineModel。
     */
    @Override
    public synchronized void init(TimelineModel model) {
        if (this.timelineModel == model) return;
        if (this.timelineModel != null) onTimelineUiClosed();
        sampler.resetPathState(); previewSample = null;
        cameraDataDirty = true;
        super.init(model);
    }

    @Nullable
    public synchronized TimelineTrack getActiveCameraTrack() {
        return getActiveTrack();
    }

    public synchronized void setActiveCameraTrack(@Nullable TimelineTrack track) {
        activateTrack(track);
    }

    /**
     * 当关键帧发生变化时可以显式调用（例如某些命令直接操作 keyframe 列表）。
     * ⭐ v5.0：只刷新激活轨道的关键帧
     */
    public synchronized void onKeyframeChanged() {
        cameraDataDirty = true;
        rebuildFromActiveTrack();
        if (previewing && timelineModel != null && !timelineModel.isPlaying() && !timelineModel.isScrubbing()) {
            setStaticPreviewAt(previewTimeSeconds);
        }
    }

    /**
     * 拖拽预览用的轻量刷新：
     * 仅让路径缓存失效，不触发关键帧列表重建。
     */
    public synchronized void onKeyframePreviewMoved() {
        markCustomPathClipsDirty(activeTrack);
        pathManager.markPathDirty();
    }

    @Override
    public synchronized void rebuildFromActiveTrack() {
        if (!isValidTrack(activeTrack)) {
            sampler.setCameraTrack(null);
            sampler.setKeyframes(new ArrayList<>());
            previewSample = null;
            pathManager.setKeyframes(new ArrayList<>());
            cameraDataDirty = false;
            return;
        }

        previewSample = null;
        markCustomPathClipsDirty(activeTrack);
        List<CameraKeyframe> trackKeyframes = collectTrackCameraKeyframes(activeTrack);
        List<CameraKeyframe> pathPreviewKeyframes = collectAllCameraKeyframes(activeTrack);
        sampler.setKeyframes(trackKeyframes);
        sampler.setCameraTrack(activeTrack);
        pathManager.setKeyframes(pathPreviewKeyframes);
        pathManager.markPathDirty();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineCamera] Rebuild from track '{}', fallback keyframes = {}, preview keyframes = {}",
                    activeTrack.getName(), trackKeyframes.size(), pathPreviewKeyframes.size());
        }
        cameraDataDirty = false;
    }

    /**
     * 在真正需要时才重建（避免播放时每帧 rebuild 造成 FPS 大幅下降）。
     */
    private synchronized void ensureCameraDataBuilt() {
        if (cameraDataDirty) {
            rebuildFromActiveTrack();
        }
    }

    // ================== 播放 / 拖动 / 预览 状态控制 ==================

    /**
     * ⭐ UI 事件驱动：开始拖动播放头（scrubbing）
     * 由 TimelineInteractionHandler 在鼠标按下播放头时调用
     *
     * <p>
     * 注意：scrubbing 状态由 TimelineModel.setScrubbing(true) 设置，这里只负责摄像机采样
     * <p>
     * ⭐ 关键点：即使没有关键帧，也会在 updateOwnerAndSample 中正确切 owner
     * <p>
     * ⭐ 修复：使用 tick() 统一入口，确保 Scrub 和 Play 逻辑一致
     */
    public void beginScrubbing(float timeSeconds) {
        this.previewing = false;
        this.previewSample = null;
        ensureCameraDataBuilt();
        // ⭐ 使用 tick() 统一入口（deltaSec=0，因为 scrubbing 不推进时间）
        updateOwnerAndSample(0f);
        LOGGER.debug("[TimelineCameraController] 开始 scrubbing @ {}s", String.format("%.2f", timeSeconds));
    }

    /**
     * ⭐ UI 事件驱动：更新拖动播放头位置
     * 由 TimelineInteractionHandler 在鼠标拖动时调用
     *
     * <p>
     * 注意：scrubbing 状态由 TimelineModel.isScrubbing() 检查
     * <p>
     * ⭐ 修复：使用 tick() 统一入口，确保 Scrub 和 Play 逻辑一致
     * <p>
     * 注意：时间已经在 TimelineModel 中更新，这里只需要采样当前时间
     */
    public void updateScrubbing(float timeSeconds) {
        // 检查 model 的状态（单一真相源）
        if (timelineModel == null || !timelineModel.isScrubbing()) {
            // 如果不在 scrubbing 状态，忽略更新
            return;
        }

        ensureCameraDataBuilt();
        // ⭐ 使用 tick() 统一入口（deltaSec=0，因为 scrubbing 不推进时间）
        updateOwnerAndSample(0f);
    }

    /**
     * ⭐ UI 事件驱动：结束拖动播放头
     * 由 TimelineInteractionHandler 在鼠标释放时调用
     * 
     * <p>
     * 注意：scrubbing 状态由 TimelineModel.setScrubbing(false) 设置，这里只负责摄像机状态同步
     */
    public void endScrubbing(float timeSeconds) {
        setStaticPreviewAt(timeSeconds);

        LOGGER.debug("[TimelineCameraController] 结束 scrubbing @ {}s", String.format("%.2f", timeSeconds));
    }

    /**
     * ⭐ UI 事件驱动：开始播放
     * 由 TimelineModel.setPlaying(true) 通过 startPlayback() 调用
     *
     * <p>
     * ⭐ 关键修复：在事件发生时更新轨迹，而不是在 update() 中每帧 setKeyframes
     * <p>
     * ⭐ 修复：使用 tick() 统一入口，立即应用当前时间的采样并设置 owner
     */
    public void startPlayback() {
        clearPreviewState();
        ensureCameraDataBuilt();
        updateOwnerAndSample(0f);
    }

    /**
     * ⭐ UI 事件驱动：停止播放
     * 由 TimelineModel.setPlaying(false) 通过 stopPlayback() 调用
     * 
     * <p>
     * ⭐ 修复：清空 PathClip 状态，确保下次播放时 onEnter() 能重新触发
     */
    public void stopPlayback() {
        if (timelineModel != null) {
            setStaticPreviewAt(timelineModel.getCurrentTime());
        } else {
            releaseCameraToPlayer();
        }
        LOGGER.debug("[TimelineCameraController] 停止播放");
    }

    /** 由 TimelineModel.setPlaying(false) 通知 */
    public void onPlaybackStopped() {
        LOGGER.debug("[TimelineCameraController] 播放停止");
        stopPlayback();
    }

    /**
     * UI 关闭时调用，彻底释放时间线对摄像机的控制，恢复玩家控制。
     * 由 DirectorScreen.close() 统一调用一次。
     */
    public void onTimelineUiClosed() {
        this.previewing = false;
        this.previewSample = null;
        // scrubbing 状态由 TimelineModel 管理，这里不需要设置
        if (timelineModel != null) {
            timelineModel.setPlaying(false);
            timelineModel.setScrubbing(false);
        }
        clearPreviewState();
        sampler.resetPathState();
        CameraRuntime.getInstance().reset();
        CameraRenderOverride.publishTimelineActive(false);
        setCameraInputHidden(false);
        commitState();
        LOGGER.debug("[TimelineCameraController] UI 关闭，释放摄像机控制权");
    }

    /**
     * 调试用：返回当前时间线时间（秒）。若模型为空则返回 0。
     */
    public float getCurrentTimeSeconds() {
        return currentTimelineTime();
    }

    // ================== 每帧更新（由 TimelinePanel 调用） ==================

    /**
     * TimelinePanel 每帧调用：根据播放/拖动/预览状态切换 owner 并采样。
     * ⭐ 修复：添加 synchronized，与 setActiveCameraTrack / init 等保持一致，
     *   避免并发读写 activeCameraTrack / previewing / timelineModel 导致状态撕裂。
     */
    public synchronized void updateOwnerAndSample(float deltaSeconds) {
        if (timelineModel == null) {
            CameraRuntime.getInstance().reset();
            CameraRenderOverride.publishTimelineActive(false);
            commitState(); return;
        }
        // Export publishes its camera explicitly; ordinary preview must not overwrite it.
        if (com.director.export.video.VideoExportManager.getInstance().isExporting()) return;
        boolean playing = timelineModel.isPlaying(), scrubbing = timelineModel.isScrubbing();
        CameraSample sampled = null;
        if (isValidTrack(activeTrack) && (playing || scrubbing || previewing)) {
            ensureCameraDataBuilt();
            if (playing || scrubbing) {
                sampled = sampler.tick(timelineModel, 0f).orElse(null);
            } else {
                if (previewSample == null) previewSample = sampler.sample(previewTimeSeconds).orElse(null);
                sampled = previewSample;
            }
        }
        var runtime = CameraRuntime.getInstance();
        if (sampled != null) {
            runtime.applyTimelineSample(sampled);
            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
            setCameraInputHidden(true);
        } else {
            runtime.reset();
            setCameraInputHidden(false);
        }
        CameraRenderOverride.publishTimelineActive(playing || scrubbing);
        commitState();
    }

    private static void setCameraInputHidden(boolean hidden) {
        if (MinecraftClient.getInstance() != null)
            DirectorClientController.getInstance().setHideCrosshair(hidden);
    }

    @Override
    public synchronized boolean isEnabled() {
        return timelineModel != null;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        if (!enabled) {
            clear();
        }
    }

    @Override
    public boolean hasOverride() {
        return cachedHasOverride;
    }

    /** Historical query without changing camera owner, playhead or playback path state. */
    public synchronized Optional<CameraSample> sampleSnapshot(float time) {
        if(!isValidTrack(activeTrack)) return Optional.empty();
        return sampler.sampleSnapshot(activeTrack,collectTrackCameraKeyframes(activeTrack),time);
    }

    @Override
    public synchronized CameraSample getCurrentSample() {
        return CameraRuntime.getInstance().getCurrentSample();
    }

    @Override
    public synchronized void clear() {
        releaseCameraToPlayer();
    }

    /**
     * 兼容旧接口：沿用 update 名称，但委托给 updateOwnerAndSample。
     * ⭐ 使用 System.nanoTime() 计算真实帧间隔（delta seconds），
     *   避免 getTickProgress 返回的 tick 位置（非增量）被误用为时间差。
     */
    public synchronized void update() {
        long now = System.nanoTime();
        float delta;
        if (lastUpdateNanoTime == 0L) {
            delta = 1f / 60f; // 首帧使用合理默认值
        } else {
            delta = (now - lastUpdateNanoTime) / 1_000_000_000f;
        }
        lastUpdateNanoTime = now;
        // 限制在合理范围内（防止暂停后巨大跳变）
        delta = Math.min(delta, 0.5f);
        updateOwnerAndSample(delta);
    }

    // ================== 预览相关 ==================

    /**
     * 清除预览状态（供外部调用，例如检测到玩家输入时）
     */
    public synchronized void clearPreviewState() {
        if (previewing) {
            previewing = false;
            previewSample = null;
            LOGGER.debug("[TimelineCameraController] 清除预览状态");
        }
        // ⭐ 同时清除关键帧编辑预览，避免残留状态导致下一帧又切回 TIMELINE
    }

    public synchronized void releaseCameraToPlayer() {
        CameraRuntime runtime = CameraRuntime.getInstance();
        if (runtime.isTimelineOwner()) {
            CameraAccessor.applySampleToPlayerEntity(MinecraftClient.getInstance(), runtime.getCurrentSample());
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
        }
        sampler.onStop();
        this.previewing = false;
        this.previewSample = null;
        setCameraInputHidden(false);
        CameraRenderOverride.publishTimelineActive(false);
        commitState();
    }

    /**
     * 检查时间线是否真正在活动（播放、scrubbing 或关键帧属性编辑预览）
     * 用于判断是否应该保持 TIMELINE owner
     */
    public synchronized boolean isTimelineActive() {
        if (timelineModel == null) {
            return false;
        }
        return timelineModel.isPlaying() || timelineModel.isScrubbing();
    }

    /**
     * 当用户点击摄像机关键帧进行预览时调用。
     * ⭐ 注意：预览会设置 owner = TIMELINE，如果用户想添加关键帧，需要先退出预览
     */
    public synchronized void previewAt(float timeSeconds) {
        if (!isValidTrack(activeTrack)) {
            LOGGER.warn("[TimelineCamera] previewAt({}) 但当前没有有效摄像机轨道", timeSeconds);
            return;
        }

        setStaticPreviewAt(timeSeconds);
        LOGGER.debug("[TimelineCameraController] 预览 @ {}s", String.format("%.2f", timeSeconds));
    }

    /**
     * ⭐ v5.0：对指定时间采样，并写入 CameraRuntime
     */
    private Optional<CameraSample> applySampleAtTime(float timeSeconds) {
        Optional<CameraSample> opt = sampler.sample(timeSeconds);
        CameraRuntime runtime = CameraRuntime.getInstance();

        opt.ifPresent(runtime::applyTimelineSample);
        return opt;
    }

    /**
     * 静止预览只在进入静止状态时采样一次。
     * Dolly/Crane 等路径会沿用上一帧朝向；如果静止状态每帧重采样，朝向会受到采样器内部状态影响。
     */
    private void setStaticPreviewAt(float timeSeconds) {
        this.previewing = true;
        this.previewTimeSeconds = timeSeconds;

        ensureCameraDataBuilt();
        this.previewSample = applySampleAtTime(timeSeconds).orElse(null);

        if (previewSample == null) {
            releaseCameraToPlayer();
            return;
        }

        CameraRuntime runtime = CameraRuntime.getInstance();
        if (!runtime.isTimelineOwner()) {
            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
        }
        setCameraInputHidden(true);
    }

    // ================== 属性面板实时预览 ==================

    /**
     * 属性面板实时预览：将关键帧直接转换为 CameraSample 并应用到 CameraRuntime。
     * <p>
     * 无需重建 PathManager，适合拖拽编辑时的低延迟实时预览。
     * 仅在控件修改时调用；保留最终预览，玩家输入可释放接管。
     *
     * @param kf 当前正在编辑的关键帧
     */
    public synchronized void previewKeyframeDirect(CameraKeyframe kf) {
        if (kf == null || timelineModel == null || timelineModel.isPlaying() || timelineModel.isScrubbing()
                || com.director.export.video.VideoExportManager.getInstance().isExporting()) return;
        previewing = true; previewTimeSeconds = kf.getTime();
        previewSample = pathManager.toSample(kf);
        var runtime = CameraRuntime.getInstance();
        runtime.applyTimelineSample(previewSample);
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
        setCameraInputHidden(true);
        commitState();
    }

    /**
     * 点击时间标尺时：用时间线接管相机并停在当前路径位置。
     * 玩家输入会通过 MouseMixin / KeyboardInputMixin 释放预览，恢复自由控制。
     */
    public synchronized void jumpToTimeAsPlayer(float timeSeconds) {
        if (!isValidTrack(activeTrack)) {
            return;
        }

        this.previewTimeSeconds = timeSeconds;

        setStaticPreviewAt(timeSeconds);
        LOGGER.debug("[TimelineCamera] jumpToTimeAsPlayer @ {}s", timeSeconds);
    }

    /** State belongs to the editor session that started the export. */
    public record ExportState(TimelineModel model, float time, boolean playing,
            TimelineTrack track, boolean previewing, float previewTime, CameraSample previewSample) {}

    public synchronized ExportState saveStateForExport() {
        var state = new ExportState(timelineModel,
                timelineModel == null ? 0 : timelineModel.getCurrentTime(),
                timelineModel != null && timelineModel.isPlaying(),
                activeTrack, previewing, previewTimeSeconds, previewSample);
        if (timelineModel != null) timelineModel.setPlaying(false);
        return state;
    }

    /** Caller clears the exporting flag before restoring, on the client thread. */
    public synchronized void restoreStateAfterExport(ExportState state) {
        if (state == null || state.model() != timelineModel) return;
        setActiveCameraTrack(state.track());
        previewing = state.previewing(); previewTimeSeconds = state.previewTime();
        previewSample = state.previewSample();
        if (timelineModel != null) {
            timelineModel.setCurrentTime(state.time());
            timelineModel.setPlaying(state.playing());
        }
        updateOwnerAndSample(0f);
    }

    public synchronized void preparePlayerCameraForExport() {
        clearPreviewState();
        CameraRuntime.getInstance().reset();
        CameraRenderOverride.publishTimelineActive(false);
        setCameraInputHidden(false);
        commitState();
    }

    /** Export owns the camera only when this frame has a valid shot. */
    public synchronized void applySampleForExport(float timeSeconds) {
        clearPreviewState();
        ensureCameraDataBuilt();
        CameraSample sample = isValidTrack(activeTrack) ? sampler.sample(timeSeconds).orElse(null) : null;
        var runtime = CameraRuntime.getInstance();
        if (sample == null) {
            preparePlayerCameraForExport();
            return;
        }
        runtime.applyTimelineSample(sample);
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
        CameraRenderOverride.publishTimelineActive(true);
        setCameraInputHidden(true);
        commitState();
    }


    /**
     * 从当前游戏摄像机捕捉状态并写入关键帧。
     * @return true 表示捕捉成功，false 表示由于非玩家模式或其他原因失败
     */
    public synchronized boolean captureFromCurrentCamera(CameraKeyframe keyframe) {
        if (keyframe == null)
            return false;

        CameraRuntime runtime = CameraRuntime.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();

        // 只允许玩家模式下捕捉，避免时间线接管时错采样
        if (!runtime.isPlayerOwner()) {
            LOGGER.warn("[TimelineCameraController] 当前非玩家控制，忽略关键帧捕捉");
            return false;
        }

        CameraSample sample = CameraAccessor.sampleFromGameCamera(client);
        if (sample == null) {
            // 如果无法从游戏摄像机采样，尝试从 CameraRuntime 读取（降级方案）
            sample = runtime.getCurrentSample();
            if (sample == null) {
                LOGGER.warn("[TimelineCameraController] 无法获取当前相机状态，无法设置关键帧");
                return false;
            }
            LOGGER.warn("[TimelineCameraController] 从游戏摄像机采样失败，使用 CameraRuntime 中的状态（可能不是最新）");
        } else {
            // ⭐ 将采样结果同步到 CameraRuntime，保持状态一致
            // 使用 updateCurrentSample 而不是 applyTimelineSample，因为这是玩家摄像机采样
            // updateCurrentSample 只更新状态，不改变 owner
            runtime.updateCurrentSample(sample);
        }

        keyframe.setPosition(new Vector3f(
                (float) sample.position().x,
                (float) sample.position().y,
                (float) sample.position().z));

        // rotation.x = pitch, rotation.y = yaw, rotation.z = roll
        keyframe.setRotation(new Vector3f(
                sample.pitch(), // x = pitch
                sample.yaw(), // y = yaw
                sample.roll() // z = roll
        ));
        keyframe.setFov(sample.fov());
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());

        LOGGER.info("[TimelineCameraController] 捕捉当前视角到 CameraKeyframe@{}s", keyframe.getTime());
        return true;
    }

    /**
     * ⭐ v5.0：从指定轨道收集摄像机关键帧
     * 
     * <p>
     * 注意：此方法只收集传入 track 的关键帧，不会遍历所有摄像机轨道。
     * 用于确保每条摄像机轨道有独立的路径（多机位系统）。
     * 
     * @param track 目标摄像机轨道（通常为 activeCameraTrack）
     * @return 排序后的关键帧列表
     */
    private List<CameraKeyframe> collectTrackCameraKeyframes(TimelineTrack track) {
        List<CameraKeyframe> result = new ArrayList<>();
        if (track == null || track.getType() != TrackType.CAMERA) {
            return result;
        }

        // 只收集旧数据/兼容路径中的轨道级关键帧。片段内关键帧不能进入 fallback 采样，
        // 否则自定义路径片段会在片段外继续驱动摄像机。
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof CameraKeyframe ck) {
                result.add(ck);
            }
        }
        result.sort(Comparator.comparing(CameraKeyframe::getTime));
        return result;
    }

    private List<CameraKeyframe> collectAllCameraKeyframes(TimelineTrack track) {
        List<CameraKeyframe> result = collectTrackCameraKeyframes(track);
        if (track == null || track.getType() != TrackType.CAMERA) {
            return result;
        }

        for (CameraPathClip pathClip : track.getCameraPathClips()) {
            if (pathClip instanceof CustomPathClip customPathClip) {
                result.addAll(customPathClip.getKeyframes());
            }
        }
        result.sort(Comparator.comparing(CameraKeyframe::getTime));
        return result;
    }

    private void markCustomPathClipsDirty(TimelineTrack track) {
        if (track == null || track.getType() != TrackType.CAMERA) {
            return;
        }
        for (CameraPathClip pathClip : track.getCameraPathClips()) {
            if (pathClip instanceof CustomPathClip customPathClip) {
                customPathClip.onKeyframeChanged();
            }
        }
    }

    @Override
    protected boolean computeHasOverride() {
        return CameraRuntime.getInstance().isTimelineOwner();
    }

    @Override
    protected void publishRenderSnapshot() {
        // 快照由 CameraRuntime 写入；此处仅刷新热路径缓存
    }

    @Override
    protected void onDisabled() {
    }

    @Override
    protected void onSampleApplied(CameraSample sample) {
    }

    @Override
    protected void onOverrideCleared(boolean fullReset) {
    }
}
