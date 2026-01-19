package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.client.renderer.CameraPathDebugRenderer;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * TimelineCameraController v5.0 (Multi-Camera Tracks)
 *
 * 职责：
 *  - 管理多条 CAMERA 轨道，但任意时刻只有一条为 active
 *  - 从激活轨道收集摄像机关键帧，交给 TimelineCameraPathManager 生成轨迹
 *  - 根据播放 / 拖动 / 预览状态采样 CameraSample，写入 CameraRuntime
 *  - 在需要时接管/释放相机控制权（CameraRuntime.Owner）
 *  - 提供「从当前视角捕捉关键帧」等高级操作
 */
public final class TimelineCameraController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
    private static final TimelineCameraController INSTANCE = new TimelineCameraController();

    /** 时间线数据模型（由 TimelinePanel 注入） */
    private TimelineModel timelineModel;

    /** ⭐ v5.0：每帧采样用的核心工具（仅对 activeCameraTrack 的关键帧生效） */
    private final CameraTrackSampler sampler = new CameraTrackSampler();

    /** ⭐ v5.0：用于 UI / 世界渲染的路径采样管理（同样基于 activeCameraTrack） */
    private final TimelineCameraPathManager pathManager = new TimelineCameraPathManager();

    /** ⭐ v5.0：当前激活的摄像机轨道（可能为 null） */
    private TimelineTrack activeCameraTrack;

    /**
     * 性能优化：摄像机轨道数据是否需要重建（keyframes/path）。
     *
     * <p>之前在 updateOwnerAndSample() 中每帧 rebuildFromActiveTrack()，会频繁分配/排序，导致播放掉帧。</p>
     */
    private boolean cameraDataDirty = true;

    /** 是否处于「单帧预览关键帧」的状态 */
    private boolean previewing = false;
    private float previewTimeSeconds = 0f;

    // ⭐ scrubbing 状态完全由 TimelineModel.isScrubbing() 管理，不再使用内部变量

    /** 监听模型变更，自动刷新摄像机关键帧列表 */
    private TimelineModel.ModelChangeListener modelChangeListener;

    private TimelineCameraController() {
        // 把 pathManager 交给调试渲染器，用同一套轨迹数据
        CameraPathDebugRenderer.getInstance().setPathManager(pathManager);
    }

    public static TimelineCameraController getInstance() {
        return INSTANCE;
    }

    // ================== 生命周期 / 绑定模型 ==================

    /**
     * 绑定 TimelineModel。
     * 会自动注册 ModelChangeListener，在模型变更时刷新摄像机关键帧。
     * ⭐ v5.0：会自动初始化 activeCameraTrack（选择第一条 CAMERA 轨道）。
     */
    public synchronized void setTimelineModel(TimelineModel model) {
        // 解除旧监听
        if (this.timelineModel != null && modelChangeListener != null) {
            this.timelineModel.removeModelChangeListener(modelChangeListener);
        }

        this.timelineModel = model;
        cameraDataDirty = true;

        if (model != null) {
            if (modelChangeListener == null) {
                modelChangeListener = changeType -> onModelStructureChanged();
            }
            model.addModelChangeListener(modelChangeListener);
            
            // ⭐ v5.0：初始选中第一条 CAMERA 轨道（如果还没有 active）
            // ⭐ 如果只有一个摄像机轨道，自动激活它
            if (!isValidCameraTrack(activeCameraTrack)) {
                TimelineTrack firstCameraTrack = findFirstCameraTrack();
                if (firstCameraTrack != null) {
                    // 检查是否只有一个摄像机轨道
                    int cameraTrackCount = countCameraTracks();
                    activeCameraTrack = firstCameraTrack;
                    if (cameraTrackCount == 1) {
                        LOGGER.info("[TimelineCamera] 检测到只有一个摄像机轨道，自动激活: {}", firstCameraTrack.getName());
                    }
                }
            }
            
            rebuildFromActiveTrack();
        } else {
            activeCameraTrack = null;
            sampler.setKeyframes(new ArrayList<>());
            pathManager.setKeyframes(new ArrayList<>());
            cameraDataDirty = false;
        }
    }

    /**
     * ⭐ v5.0：当前激活的摄像机轨道
     */
    @Nullable
    public synchronized TimelineTrack getActiveCameraTrack() {
        return activeCameraTrack;
    }

    /**
     * ⭐ v5.0：设置当前激活的摄像机轨道
     * - 仅允许 TrackType.CAMERA
     * - 会立即重建采样器和路径数据
     * - UI 只需要在轨道头"选择按钮"被点击时调用此方法即可
     */
    public synchronized void setActiveCameraTrack(@Nullable TimelineTrack track) {
        if (track != null && track.getType() != TrackType.CAMERA) {
            LOGGER.warn("[TimelineCamera] setActiveCameraTrack: 非 CAMERA 轨道被选中，忽略: {}", track.getName());
            return;
        }
        if (track == activeCameraTrack) {
            return;
        }

        this.activeCameraTrack = track;
        cameraDataDirty = true;
        LOGGER.info("[TimelineCamera] 激活摄像机轨道: {}",
                track != null ? track.getName() : "<none>");

        rebuildFromActiveTrack();
    }

    /**
     * 当关键帧发生变化时可以显式调用（例如某些命令直接操作 keyframe 列表）。
     * ⭐ v5.0：只刷新激活轨道的关键帧
     */
    public synchronized void onKeyframeChanged() {
        cameraDataDirty = true;
        rebuildFromActiveTrack();
    }

    /**
     * ⭐ v5.0：当 TimelineModel 的结构发生变化（轨道增删、关键帧变动）时调用
     * 尝试保持 activeCameraTrack 不变，若失效则自动选取新的摄像机轨道
     */
    private synchronized void onModelStructureChanged() {
        if (timelineModel == null) {
            return;
        }

        // 统计当前摄像机轨道数量
        int cameraTrackCount = countCameraTracks();
        TimelineTrack firstCameraTrack = findFirstCameraTrack();
        
        // ⭐ 如果只有一个摄像机轨道，无论当前状态如何，都确保它是激活的
        // 这包括：从0个变成1个、从多个变成1个、新建唯一轨道等情况
        if (cameraTrackCount == 1 && firstCameraTrack != null) {
            if (activeCameraTrack != firstCameraTrack) {
                activeCameraTrack = firstCameraTrack;
                LOGGER.info("[TimelineCamera] 模型变更：检测到只有一个摄像机轨道，自动激活: {}", firstCameraTrack.getName());
            }
        } else if (cameraTrackCount == 0) {
            // 没有摄像机轨道时，清空激活轨道
            activeCameraTrack = null;
        } else {
            // 有多个摄像机轨道时，检查当前激活轨道是否有效
            if (!isValidCameraTrack(activeCameraTrack)) {
                // 当前激活轨道无效，选择第一个作为激活轨道（但不强制，因为可能有多个）
                activeCameraTrack = firstCameraTrack;
                if (firstCameraTrack != null) {
                    LOGGER.info("[TimelineCamera] 模型变更：当前激活轨道无效，切换到: {}", firstCameraTrack.getName());
                }
            }
        }

        cameraDataDirty = true;
        rebuildFromActiveTrack();
        LOGGER.debug("[TimelineCameraController] 模型变更，刷新摄像机关键帧");
    }

    /**
     * ⭐ v5.0：从当前激活轨道重建采样器和路径
     * 在轨道切换、关键帧变化时调用
     * 
     * <p>重要：只收集 activeCameraTrack 的关键帧，其他摄像机轨道不参与当前播放路径。
     * 每条摄像机轨道都有自己独立的轨迹，但只有激活轨道会被采样。
     * 
     * <p>基于当前 activeCameraTrack 重新构建：
     *  - CameraTrackSampler 的关键帧列表
     *  - TimelineCameraPathManager 的路径采样点
     */
    private synchronized void rebuildFromActiveTrack() {
        if (!hasValidActiveTrack()) {
            sampler.setKeyframes(new ArrayList<>());
            pathManager.setKeyframes(new ArrayList<>());
            cameraDataDirty = false;
            return;
        }

        List<CameraKeyframe> kfs = collectCameraKeyframes(activeCameraTrack);
        sampler.setKeyframes(kfs);
        sampler.setCameraTrack(activeCameraTrack);  // ⭐ 设置轨道引用，用于访问 Path Clips
        pathManager.setKeyframes(kfs);
        pathManager.markPathDirty();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineCamera] Rebuild from track '{}', keyframes = {}",
                    activeCameraTrack.getName(), kfs.size());
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
     * <p>注意：scrubbing 状态由 TimelineModel.setScrubbing(true) 设置，这里只负责摄像机采样
     * <p>⭐ 关键点：即使没有关键帧，也会在 updateOwnerAndSample 中正确切 owner
     */
    public void beginScrubbing(float timeSeconds) {
        this.previewing = false;
        ensureCameraDataBuilt();
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

        ensureCameraDataBuilt();
        sampler.sample(timeSeconds).ifPresent(sample -> {
            // ⭐ 关键修复：只更新 sample，不重复设置 owner
            CameraRuntime.getInstance().applyTimelineSample(sample);
        });
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
     * <p>⭐ 关键修复：在事件发生时更新轨迹，而不是在 update() 中每帧 setKeyframes
     */
    public void startPlayback() {
        this.previewing = false;
        ensureCameraDataBuilt();
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

    /**
     * 调试用：返回当前时间线时间（秒）。若模型为空则返回 0。
     */
    public float getCurrentTimeSeconds() {
        if (timelineModel == null) {
            return 0f;
        }
        return timelineModel.getCurrentTime();
    }

    // ================== 每帧更新（由 TimelinePanel 调用） ==================

    /**
     * TimelinePanel 每帧调用：根据播放/拖动/预览状态切换 owner 并采样。
     */
    public void updateOwnerAndSample(float deltaSeconds) {
        if (timelineModel == null) {
            return;
        }

        // 先判断当前应该由谁控制相机：玩家 or 时间线
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();
        
        // ⭐ 修复：如果不在播放/scrubbing，自动清除 previewing 状态
        // 这样当播放头静止时，玩家可以立即操作摄像机，无需先拖动播放头
        if (!playing && !scrubbing && previewing) {
            previewing = false;
            LOGGER.debug("[CameraController] 自动清除预览状态（播放头静止）");
        }
        
        boolean previewActive = previewing && !playing && !scrubbing;
        CameraRuntime runtime = CameraRuntime.getInstance();
        boolean wantsTimeline = playing || scrubbing || previewActive;
        if (wantsTimeline) {
            ensureCameraDataBuilt();
        }
        boolean hasCameraData = hasValidActiveTrack() && !sampler.getKeyframes().isEmpty();
        boolean shouldTimeline = wantsTimeline && hasCameraData;

        if (shouldTimeline && !runtime.isTimelineOwner()) {
            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
            ChronoBlocksClientController.getInstance().setHideCrosshair(true);
            LOGGER.debug("[CameraController] 接管相机");
        } else if (!shouldTimeline && runtime.isTimelineOwner()) {
            // 退出时轻微 lerp 回玩家相机，避免瞬移
            CameraSample lastSample = runtime.getCurrentSample();
            runtime.applyLerpToPlayer(lastSample, 0.1f);
            runtime.setOwner(CameraRuntime.Owner.PLAYER);
            ChronoBlocksClientController.getInstance().setHideCrosshair(false);
            LOGGER.debug("[CameraController] 恢复玩家控制");
        }

        // 时间线模式：根据当前时间采样相机
        if (runtime.isTimelineOwner()) {
            // ✅ 性能关键：播放时不要每帧 rebuild（会导致 FPS 大幅下降）
            ensureCameraDataBuilt();
            float time = playing || scrubbing ? timelineModel.getCurrentTime() : previewTimeSeconds;
            
            // 获取最后关键帧时间
            List<CameraKeyframe> kfs = sampler.getKeyframes();
            float lastKey = kfs.isEmpty() ? -1f : kfs.getLast().getTime();

            // 超出最后关键帧：保持最后帧的镜头，但继续播放其他轨道，owner 不切回玩家
            if (lastKey >= 0f && time > lastKey + 1e-4f) {
                sampler.sample(lastKey).ifPresent(runtime::applyTimelineSample);
            } else {
                sampler.sample(time).ifPresent(runtime::applyTimelineSample);
            }
        } else {
            // ⭐ 玩家模式：只负责处理平滑过渡
            // 玩家真实相机位置由 CameraMixin → CameraRuntime.updateFromGameCamera() 维护
            // Controller 这里只负责 lerp（如果正在进行平滑过渡）
            runtime.tickPlayerLerp(deltaSeconds);
        }
    }

    /**
     * 兼容旧接口：沿用 update 名称，但委托给 updateOwnerAndSample。
     */
    public void update() {
        MinecraftClient client = MinecraftClient.getInstance();
        float delta = getRenderTickDeltaSafe(client);
        updateOwnerAndSample(delta);
    }

    // ================== 预览相关 ==================

    /**
     * 清除预览状态（供外部调用，例如检测到玩家输入时）
     */
    public synchronized void clearPreviewState() {
        if (previewing) {
            previewing = false;
            LOGGER.debug("[TimelineCameraController] 清除预览状态");
        }
    }

    /**
     * 检查时间线是否真正在活动（播放或 scrubbing）
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
        if (!hasValidActiveTrack()) {
            LOGGER.warn("[TimelineCamera] previewAt({}) 但当前没有有效摄像机轨道", timeSeconds);
            return;
        }
        
        this.previewing = true;
        this.previewTimeSeconds = timeSeconds;

        ensureCameraDataBuilt();
        applySampleAtTime(timeSeconds);
        LOGGER.debug("[TimelineCameraController] 预览 @ {}s", String.format("%.2f", timeSeconds));
    }
    
    /**
     * ⭐ v5.0：对指定时间采样，并写入 CameraRuntime
     */
    private void applySampleAtTime(float timeSeconds) {
        Optional<CameraSample> opt = sampler.sample(timeSeconds);
        CameraRuntime runtime = CameraRuntime.getInstance();

        if (opt.isPresent()) {
            runtime.applyTimelineSample(opt.get());
        }
    }

    /**
     * 取当前渲染 tickDelta（partialTicks），用反射避免 IDE/映射漂移导致的解析报错。
     */
    private static float getRenderTickDeltaSafe(MinecraftClient client) {
        try {
            if (client == null) return 0.0f;
            Object rtc = client.getRenderTickCounter();
            if (rtc == null) return 0.0f;

            try {
                var m = rtc.getClass().getMethod("getTickProgress", boolean.class);
                Object v = m.invoke(rtc, false);
                if (v instanceof Float f) return f;
            } catch (ReflectiveOperationException ignored) {}

            try {
                var m = rtc.getClass().getMethod("getDynamicDeltaTicks");
                Object v = m.invoke(rtc);
                if (v instanceof Float f) return f;
            } catch (ReflectiveOperationException ignored) {}
        } catch (Throwable ignored) {
            // ignore
        }
        return 0.0f;
    }

    /**
     * 点击时间标尺时：只采样并把镜头落到玩家态，允许立即调整。
     */
    public synchronized void jumpToTimeAsPlayer(float timeSeconds) {
        if (!hasValidActiveTrack()) {
            return;
        }
        
        this.previewing = true;
        this.previewTimeSeconds = timeSeconds;

        ensureCameraDataBuilt();
        applySampleAtTime(timeSeconds);
        // 将镜头位置直接写到玩家实体 / 相机
        sampler.sample(timeSeconds).ifPresent(sample -> CameraAccessor.applySampleToPlayerEntity(MinecraftClient.getInstance(), sample));
        // 保持玩家 owner，允许继续移动、录制下一帧
        CameraRuntime.getInstance().setOwner(CameraRuntime.Owner.PLAYER);
        ChronoBlocksClientController.getInstance().setHideCrosshair(false);
        LOGGER.debug("[TimelineCamera] jumpToTimeAsPlayer @ {}s", timeSeconds);
    }

    /**
     * 🎥 导出专用：将指定时间点的 CameraSample 应用到 {@link CameraRuntime}，并强制 owner=TIMELINE。
     *
     * <p>为什么要这样做？</p>
     * <ul>
     *   <li>如果把采样写到 player 实体上（{@link #jumpToTimeAsPlayer(float)}），渲染会使用 tickDelta 对实体做插值，
     *   只要 prev/lastRender 没同步，就会产生“每帧跳一下”的抖动。</li>
     *   <li>导出时我们需要每一帧完全确定：由 {@code CameraMixin} 在 {@code Camera.update(TAIL)} 直接覆盖 Camera 的 pos/rot，
     *   不走实体插值路径，从而避免 jitter。</li>
     * </ul>
     */
    public synchronized void applySampleForExport(float timeSeconds) {
        if (!hasValidActiveTrack()) {
            return;
        }
        ensureCameraDataBuilt();
        CameraRuntime runtime = CameraRuntime.getInstance();
        sampler.sample(timeSeconds).ifPresent(runtime::applyTimelineSample);
        if (!runtime.isTimelineOwner()) {
            runtime.setOwner(CameraRuntime.Owner.TIMELINE);
        }
        // 导出期间保持隐藏十字星（避免 UI/输入干扰），可按需调整
        ChronoBlocksClientController.getInstance().setHideCrosshair(true);
    }

    // ================== 关键帧捕捉 ==================

    /**
     * 捕捉关键帧：从「当前相机状态」采样。
     *
     * ⭐ 关键修复：确保设置关键帧时：
     * 1. owner 是 PLAYER，玩家可以继续移动摄像机设置下一个关键帧
     * 2. 立即从游戏摄像机读取，而不是等待下一帧更新 CameraRuntime
     * 3. 捕获的参数与播放时一致（使用同一套插值算法）
     *
     * 工作流程（参考专业动画软件如 Blender、After Effects）：
     *  - 如果当前 owner 是 TIMELINE（预览/播放状态），先切回 PLAYER
     *  - 立即从游戏摄像机直接采样（不等待 CameraMixin 更新）
     *  - 如果 owner 已经是 PLAYER，也从游戏摄像机直接采样（确保是最新状态）
     *  - 将采样结果同步到 CameraRuntime，然后设置关键帧属性
     *
     * 关键原则：
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
            // ⭐ 将采样结果同步到 CameraRuntime，保持状态一致
            // 使用 updateCurrentSample 而不是 applyTimelineSample，因为这是玩家摄像机采样
            // updateCurrentSample 只更新状态，不改变 owner
            runtime.updateCurrentSample(sample);
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

    /**
     * ⭐ v5.0：从指定 CAMERA 轨道中提取 CameraKeyframe，并按时间排序
     */
    /**
     * ⭐ v5.0：从指定轨道收集摄像机关键帧
     * 
     * <p>注意：此方法只收集传入 track 的关键帧，不会遍历所有摄像机轨道。
     * 用于确保每条摄像机轨道有独立的路径（多机位系统）。
     * 
     * @param track 目标摄像机轨道（通常为 activeCameraTrack）
     * @return 排序后的关键帧列表
     */
    private List<CameraKeyframe> collectCameraKeyframes(TimelineTrack track) {
        List<CameraKeyframe> result = new ArrayList<>();
        if (track == null || track.getType() != TrackType.CAMERA) {
            return result;
        }
        
        // 只收集该轨道上的关键帧，不混入其他轨道的关键帧
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof CameraKeyframe ck) {
                result.add(ck);
            }
        }
        result.sort(Comparator.comparing(CameraKeyframe::getTime));
        return result;
    }
    
    /**
     * ⭐ v5.0：检查是否有有效的激活轨道
     */
    private boolean hasValidActiveTrack() {
        return isValidCameraTrack(activeCameraTrack);
    }

    /**
     * ⭐ v5.0：验证轨道是否是有效的摄像机轨道
     */
    private boolean isValidCameraTrack(@Nullable TimelineTrack track) {
        return track != null && track.getType() == TrackType.CAMERA &&
               timelineModel != null &&
               timelineModel.getTracks().contains(track);
    }

    /**
     * ⭐ v5.0：查找第一条 CAMERA 轨道
     */
    @Nullable
    private TimelineTrack findFirstCameraTrack() {
        if (timelineModel == null) return null;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == TrackType.CAMERA) {
                return track;
            }
        }
        return null;
    }

    /**
     * 统计当前模型中摄像机轨道的数量
     */
    private int countCameraTracks() {
        if (timelineModel == null) return 0;
        int count = 0;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == TrackType.CAMERA) {
                count++;
            }
        }
        return count;
    }
}
