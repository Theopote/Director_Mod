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

    /**
     * ⭐ 属性面板实时预览：选中关键帧并编辑属性时，直接将关键帧转换为 CameraSample 应用到相机，
     * 无需重建 PathManager，低延迟。
     * <p>
     * keyframeEditFramesRemaining 倒计时：每次 previewKeyframeDirect() 被调用时重置为 N，
     * 每帧 updateOwnerAndSample() 递减。当 renderProperties 不再被调用（关键帧取消选中）时，
     * 倒计时归零，预览自动结束。
     */
    private CameraSample keyframeEditSample = null;
    private int keyframeEditFramesRemaining = 0;

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
        cameraDataDirty = true;
        super.activateTrack(track);
    }

    // ================== 生命周期 / 绑定模型 ==================

    /**
     * 绑定 TimelineModel。
     */
    public synchronized void setTimelineModel(TimelineModel model) {
        cameraDataDirty = true;
        init(model);
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
            sampler.setKeyframes(new ArrayList<>());
            pathManager.setKeyframes(new ArrayList<>());
            cameraDataDirty = false;
            return;
        }

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
        sampler.tick(timelineModel, 0f).ifPresent(CameraRuntime.getInstance()::applyTimelineSample);
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
        sampler.tick(timelineModel, 0f).ifPresent(sample -> CameraRuntime.getInstance().applyTimelineSample(sample));
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
        this.previewing = false;
        this.previewSample = null;
        ensureCameraDataBuilt();

        // ⭐ 修复：检查是否有摄像机数据（关键帧或 PathClip）
        boolean hasKeyframes = isValidTrack(activeTrack) && !sampler.getKeyframes().isEmpty();
        boolean hasPathClips = isValidTrack(activeTrack) && activeTrack != null
                && activeTrack.hasCameraPathClips();
        boolean hasCameraData = hasKeyframes || hasPathClips;

        // ⭐ 修复：如果有摄像机数据，使用 tick() 立即应用采样并设置 owner
        if (hasCameraData && timelineModel != null) {
            CameraRuntime runtime = CameraRuntime.getInstance();
            // ⭐ 使用 tick() 统一入口（deltaSec=0，因为只是初始化，不推进时间）
            sampler.tick(timelineModel, 0f).ifPresent(runtime::applyTimelineSample);

            // 确保 owner 被设置为 TIMELINE
            if (!runtime.isTimelineOwner()) {
                runtime.setOwner(CameraRuntime.Owner.TIMELINE);
                DirectorClientController.getInstance().setHideCrosshair(true);
                LOGGER.debug("[CameraController] 开始播放：接管相机");
            }
        }

        LOGGER.debug("[TimelineCameraController] 开始播放");
    }

    /**
     * ⭐ UI 事件驱动：停止播放
     * 由 TimelineModel.setPlaying(false) 通过 stopPlayback() 调用
     * 
     * <p>
     * ⭐ 修复：清空 PathClip 状态，确保下次播放时 onEnter() 能重新触发
     */
    public void stopPlayback() {
        this.keyframeEditFramesRemaining = 0;
        this.keyframeEditSample = null;
        if (timelineModel != null) {
            setStaticPreviewAt(timelineModel.getCurrentTime());
        } else {
            releaseCameraToPlayer();
        }
        LOGGER.debug("[TimelineCameraController] 停止播放");
    }

    /**
     * @deprecated 使用 beginScrubbing/updateScrubbing/endScrubbing 代替
     *             scrubbing 状态现在完全由 TimelineModel 管理
     */
    @Deprecated
    public void setScrubbing(boolean scrubbing) {
        // 不再使用内部变量，状态由 TimelineModel 管理
        if (timelineModel != null) {
            timelineModel.setScrubbing(scrubbing);
        }
        if (!scrubbing) {
            this.previewing = false;
            this.previewSample = null;
        }
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
        CameraRuntime.getInstance().reset();
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
     * ⭐ 修复：添加 synchronized，与 setActiveCameraTrack / setTimelineModel 等保持一致，
     *   避免并发读写 activeCameraTrack / previewing / timelineModel 导致状态撕裂。
     */
    public synchronized void updateOwnerAndSample(float deltaSeconds) {
        if (timelineModel == null) {
            return;
        }

        // 先判断当前应该由谁控制相机：玩家 or 时间线
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();

        if (!playing && !scrubbing && keyframeEditFramesRemaining > 0) {
            keyframeEditFramesRemaining = 0;
            keyframeEditSample = null;
        }

        // ⭐ 属性面板实时预览：倒计时管理
        // 当 renderProperties() 每帧调用 previewKeyframeDirect() 时，计数器被重置。
        // 当关键帧取消选中（renderProperties 不再被调用），计数器自然递减归零。
        boolean keyframeEditActive = false;
        if (playing || scrubbing) {
            // 播放/Scrubbing 时强制停止关键帧编辑预览
            keyframeEditFramesRemaining = 0;
            keyframeEditSample = null;
        }

        boolean previewActive = previewing && !playing && !scrubbing;
        CameraRuntime runtime = CameraRuntime.getInstance();
        boolean wantsTimeline = playing || scrubbing || previewActive;
        if (wantsTimeline) {
            ensureCameraDataBuilt();
        }
        // ⭐ 修复：检查是否有摄像机数据（关键帧或 PathClip）
        // PathClip 不需要关键帧就能工作，所以需要单独检查
        boolean hasKeyframes = isValidTrack(activeTrack) && !sampler.getKeyframes().isEmpty();
        boolean hasPathClips = isValidTrack(activeTrack) && activeTrack != null
                && activeTrack.hasCameraPathClips();
        boolean hasCameraData = hasKeyframes || hasPathClips;
        boolean isExporting = com.director.export.video.VideoExportManager.getInstance().isExporting();

        // 🔒 导出期间：强制禁用预览状态，避免 UI 循环采样“游标时间”与导出采样“导出时间”冲突。
        if (isExporting && previewing) {
            this.previewing = false;
            this.previewSample = null;
        }

        boolean shouldTimeline = (wantsTimeline || isExporting) && hasCameraData;

        // ⭐ 调试日志：检查 owner 切换逻辑
        if (LOGGER.isDebugEnabled() && (wantsTimeline || isExporting)) {
            LOGGER.debug(
                    "[CameraController] wantsTimeline={}, isExporting={}, hasKeyframes={}, hasPathClips={}, hasCameraData={}, shouldTimeline={}, keyframeEditActive={}, isTimelineOwner={}",
                    wantsTimeline, isExporting, hasKeyframes, hasPathClips, hasCameraData, shouldTimeline,
                    keyframeEditActive, runtime.isTimelineOwner());
        }

        if (shouldTimeline && !runtime.isTimelineOwner()) {
            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
            DirectorClientController.getInstance().setHideCrosshair(true);
            LOGGER.debug("[CameraController] 接管相机");
        } else if (!shouldTimeline && runtime.isTimelineOwner()) {
            // 退出时轻微 lerp 回玩家相机，避免瞬移
            CameraSample lastSample = runtime.getCurrentSample();
            runtime.applyLerpToPlayer(lastSample, 0.1f);
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
            DirectorClientController.getInstance().setHideCrosshair(false);
            LOGGER.debug("[CameraController] 恢复玩家控制");
        }

        // ⭐ 时间线模式：根据当前时间采样相机（播放/scrubbing/预览时）
        // ⭐ 关键修复：使用 tick() 统一入口，确保播放时每帧都采样
        if (shouldTimeline) {
            // ✅ 性能关键：播放时不要每帧 rebuild（会导致 FPS 大幅下降）
            ensureCameraDataBuilt();

            if (playing || scrubbing) {
                // ⭐ 播放/Scrubbing：使用 tick() 统一驱动
                // tick() 负责采样当前时间（时间推进由 TimelinePanel.updatePlayback() 负责）
                sampler.tick(timelineModel, 0f).ifPresent(runtime::applyTimelineSample);
            } else if (previewActive) {
                if (previewSample == null) {
                    previewSample = sampler.sample(previewTimeSeconds).orElse(null);
                }
                if (previewSample != null) {
                    runtime.applyTimelineSample(previewSample);
                }
            }
        } else {
            // ⭐ 玩家模式：只负责处理平滑过渡
            // 玩家真实相机位置由 CameraMixin → CameraRenderOverride.reportPlayerView() 维护
            // Controller 这里只负责 lerp（如果正在进行平滑过渡）
            runtime.tickPlayerLerp(deltaSeconds);
        }

        if (timelineModel != null) {
            CameraRenderOverride.publishTimelineActive(
                    timelineModel.isPlaying() || timelineModel.isScrubbing());
        } else {
            CameraRenderOverride.publishTimelineActive(false);
        }
        commitState();
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
        keyframeEditFramesRemaining = 0;
        keyframeEditSample = null;
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
        this.keyframeEditFramesRemaining = 0;
        this.keyframeEditSample = null;
        DirectorClientController.getInstance().setHideCrosshair(false);
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
        DirectorClientController.getInstance().setHideCrosshair(true);
    }

    // ================== 属性面板实时预览 ==================

    /**
     * 属性面板实时预览：将关键帧直接转换为 CameraSample 并应用到 CameraRuntime。
     * <p>
     * 无需重建 PathManager，适合拖拽编辑时的低延迟实时预览。
     * 每次调用重置存活帧计数器，保证在 renderProperties 不再被调用后自动停止。
     *
     * @param kf 当前正在编辑的关键帧
     */
    public synchronized void previewKeyframeDirect(CameraKeyframe kf) {
        keyframeEditFramesRemaining = 0;
        keyframeEditSample = null;
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

    /**
     * 🎥 导出专用：将指定时间点的 CameraSample 应用到 {@link CameraRuntime}，并强制 owner=TIMELINE。
     *
     * <p>
     * 为什么要这样做？
     * </p>
     * <ul>
     * <li>如果把采样写到 player 实体上（{@link #jumpToTimeAsPlayer(float)}），渲染会使用 tickDelta
     * 对实体做插值，
     * 只要 prev/lastRender 没同步，就会产生“每帧跳一下”的抖动。</li>
     * <li>导出时我们需要每一帧完全确定：由 {@code CameraMixin} 在 {@code Camera.update(TAIL)} 直接覆盖
     * Camera 的 pos/rot，
     * 不走实体插值路径，从而避免 jitter。</li>
     * </ul>
     */
    public synchronized void applySampleForExport(float timeSeconds) {
        if (!isValidTrack(activeTrack)) {
            return;
        }
        ensureCameraDataBuilt();
        CameraRuntime runtime = CameraRuntime.getInstance();
        sampler.sample(timeSeconds).ifPresent(runtime::applyTimelineSample);
        if (!runtime.isTimelineOwner()) {
            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
        }
        // 导出期间保持隐藏十字星（避免 UI/输入干扰），可按需调整
        DirectorClientController.getInstance().setHideCrosshair(true);
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
