package com.director.ui.timeline.playback.camera;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
import com.director.ui.timeline.model.camera.path.CameraPathEnterContext;
import com.director.ui.timeline.model.camera.path.CameraPathSample;
import com.director.ui.timeline.model.camera.path.CustomPathClip;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CameraTrackSampler v1.0
 *
 * <p>
 * 负责对单个摄像机轨道进行采样。
 * 
 * <p>
 * 核心功能：
 * <ul>
 * <li>维护一组 CameraKeyframe（通常来自某条 CAMERA 轨道）</li>
 * <li>根据关键帧的 SegmentPathType / RotationInterpolation 等进行插值</li>
 * <li>提供 sample(time) 方法，返回 CameraSample</li>
 * </ul>
 *
 * <p>
 * 注意：
 * <ul>
 * <li>只负责采样逻辑，不关心轨道管理</li>
 * <li>采样策略与 TimelineCameraPathManager 保持一致</li>
 * <li>为了代码复用，内部使用 TimelineCameraPathManager 进行实际采样</li>
 * </ul>
 */
public final class CameraTrackSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraTrackSampler.class);

    /** 排序后的关键帧列表 */
    private final List<CameraKeyframe> keyframes = new ArrayList<>();

    /** 摄像机轨道引用（用于访问 Path Clips） */
    private TimelineTrack cameraTrack;

    /** 内部使用的 PathManager（用于实际采样） */
    private final TimelineCameraPathManager pathManager = new TimelineCameraPathManager();

    /** ⭐ 记录上一帧激活的 PathClip，用于检测"刚进入"事件 */
    private CameraPathClip lastActivePathClip = null;

    /** ⭐ 记录上一帧的摄像机状态，用于传递给 onEnter() */
    private CameraSample lastSample = null;

    /** ⭐ 是否正在接管摄像机控制权（用于 tick() 状态管理） */
    private boolean active = false;

    public CameraTrackSampler() {
    }

    /**
     * 设置摄像机轨道引用（用于访问 Path Clips）
     * 
     * <p>
     * ⭐ 重要：当轨道切换时，重置状态跟踪，确保新的 PathClip 能正确触发 onEnter()
     */
    public void setCameraTrack(TimelineTrack track) {
        // ⭐ 如果轨道切换，重置状态跟踪
        if (this.cameraTrack != track) {
            this.lastActivePathClip = null;
            this.lastSample = null;
        }
        this.cameraTrack = track;
    }

    /**
     * 设置关键帧列表（会自动排序）
     */
    public void setKeyframes(Collection<CameraKeyframe> frames) {
        keyframes.clear();
        if (frames != null && !frames.isEmpty()) {
            keyframes.addAll(frames);
            keyframes.sort(Comparator.comparingDouble(CameraKeyframe::getTime));
        }
        // 同步到 PathManager
        pathManager.setKeyframes(keyframes);
    }

    /**
     * 获取当前关键帧列表（只读）
     */
    public List<CameraKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    /**
     * ⭐ 停止播放时调用，清空 PathClip 状态，确保下次播放时 onEnter() 能重新触发
     * 
     * <p>
     * 这是解决"播放停止后再次播放时 PathClip 不响应"的关键方法
     */
    public void onStop() {
        // ⭐ 清空 lastActivePathClip，确保下次进入 PathClip 时能触发 onEnter()
        lastActivePathClip = null;
        // 注意：保留 lastSample，因为可能立即进入另一个 PathClip
        active = false;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[CameraTrackSampler] 停止播放，清空 PathClip 状态");
        }
    }

    /**
     * ⭐ 在播放模式下，每一帧调用（统一的 tick 驱动入口）
     * 
     * <p>
     * 这是从"UI 驱动"升级为"时间驱动"的核心方法。
     * 所有播放相关的采样都通过此方法统一管理。
     * 
     * <p>
     * 工作流程：
     * <ul>
     * <li>PLAYING：采样当前时间（时间推进由 TimelinePanel.updatePlayback() 负责）</li>
     * <li>SCRUBBING：采样当前时间（不推进时间）</li>
     * <li>STOPPED：清空状态，释放控制权</li>
     * </ul>
     * 
     * <p>
     * ⭐ 注意：虽然建议架构中 tick() 应该推进时间，但当前 TimelinePanel 已经在推进时间。
     * 为了保持兼容性，这里只负责采样，不推进时间。未来可以重构让 tick() 完全负责时间推进。
     * 
     * @param timelineModel 时间轴模型（提供时间与播放状态）
     * @param deltaSec      帧间隔（秒）。当前未使用，保留用于未来扩展（如果 tick() 负责推进时间）
     * @return 如果成功采样则返回 CameraSample，否则返回 empty
     */
    public Optional<CameraSample> tick(TimelineModel timelineModel, float deltaSec) {
        if (timelineModel == null) {
            return Optional.empty();
        }

        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();

        // ⭐ 判断播放状态
        if (playing) {
            // ▶️ 播放：采样当前时间
            float currentTime = timelineModel.getCurrentTime();
            Optional<CameraSample> result = sampleAtTime(currentTime);
            active = true;
            return result;

        } else if (scrubbing) {
            // 🖱️ Scrubbing：采样当前帧
            float currentTime = timelineModel.getCurrentTime();
            Optional<CameraSample> result = sampleAtTime(currentTime);
            active = true;
            return result;

        } else {
            // ⏹️ 停止：释放摄像机控制权
            if (active) {
                onStop();
            }
            return Optional.empty();
        }
    }

    /**
     * ⭐ 公共接口：在指定时间采样摄像机状态（向后兼容）
     * 
     * <p>
     * 注意：建议使用 tick() 方法进行统一的播放驱动。
     * 此方法保留用于 Scrubbing 等需要直接采样的场景。
     * 
     * @param timeSeconds 时间（秒）
     * @return 如果成功采样则返回 CameraSample，否则返回 empty
     */
    public Optional<CameraSample> sample(float timeSeconds) {
        return sampleAtTime(timeSeconds);
    }

    /**
     * ⭐ 内部采样方法：在指定时间采样摄像机状态（优先使用 Path Clip）
     * 
     * <p>
     * 这是所有采样逻辑的统一入口（tick() 和 sample() 都调用此方法）。
     * 
     * <p>
     * 采样优先级：
     * <ol>
     * <li>如果存在激活的 CameraPathClip，使用 Path Clip 计算位置和朝向</li>
     * <li>否则，使用关键帧插值计算位置和朝向</li>
     * <li>FOV、Roll、DOF 等参数从关键帧获取（如果没有关键帧，使用默认值）</li>
     * </ol>
     * 
     * <p>
     * ⭐ 新增功能：检测"刚进入"PathClip 事件，触发 onEnter() 生命周期钩子
     * 
     * @param timeSeconds 时间（秒）
     * @return 如果成功采样则返回 CameraSample，否则返回 empty
     */
    private Optional<CameraSample> sampleAtTime(float timeSeconds) {
        // ⭐ 优先检查 Path Clip
        CameraPathClip activePathClip = null;
        if (cameraTrack != null && cameraTrack.hasCameraPathClips()) {
            activePathClip = cameraTrack.findActivePathClip(timeSeconds);

            // ⭐ 调试日志：检查 PathClip 检测
            if (LOGGER.isTraceEnabled()) {
                if (activePathClip != null) {
                    LOGGER.trace("[CameraTrackSampler] 找到激活的 PathClip: {} @ {}s (轨道: {})",
                            activePathClip.getClass().getSimpleName(), timeSeconds,
                            cameraTrack != null ? cameraTrack.getName() : "null");
                } else {
                    LOGGER.trace("[CameraTrackSampler] 未找到激活的 PathClip @ {}s (轨道: {}, 有PathClips: {})",
                            timeSeconds,
                            cameraTrack != null ? cameraTrack.getName() : "null",
                            cameraTrack != null && cameraTrack.hasCameraPathClips());
                }
            }
        }

        // ⭐ 检测是否"刚进入"PathClip（关键逻辑）
        if (activePathClip != null && activePathClip != lastActivePathClip) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 检测到进入 PathClip: {} @ {}s",
                        activePathClip.getClass().getSimpleName(), timeSeconds);
            }

            // 刚进入 PathClip，触发 onEnter() 生命周期钩子
            // 使用上一帧的摄像机状态作为进入上下文
            if (lastSample != null) {
                // 从上一帧的 CameraSample 构造进入上下文
                // ⭐ 使用 yaw/pitch 构造函数，更精确且支持 CAMERA_FORWARD 模式
                Vec3d lastPos = lastSample.position();
                CameraPathEnterContext ctx = new CameraPathEnterContext(
                        lastPos,
                        lastSample.yaw(),
                        lastSample.pitch());
                activePathClip.onEnter(ctx);
            } else {
                // 如果没有上一帧状态，尝试从 CameraRuntime 获取当前状态
                try {
                    CameraRuntime runtime = CameraRuntime.getInstance();
                    CameraSample currentSample = runtime.getCurrentSample();
                    if (currentSample != null) {
                        Vec3d currentPos = currentSample.position();
                        // ⭐ 使用 yaw/pitch 构造函数
                        CameraPathEnterContext ctx = new CameraPathEnterContext(
                                currentPos,
                                currentSample.yaw(),
                                currentSample.pitch());
                        activePathClip.onEnter(ctx);
                    }
                } catch (Exception e) {
                    // ⭐ 记录异常而非静默吞掉，便于排查 PathClip 初始化问题
                    LOGGER.warn("[CameraTrackSampler] onEnter fallback 获取 CameraRuntime 状态失败", e);
                }
            }
        }

        Vec3d position;
        float yaw, pitch;

        // ⭐ 缓存关键帧采样结果，避免后续 FOV/Roll/DOF 部分重复调用 pathManager.sample()
        CameraSample cachedKeyframeSample = null;

        if (activePathClip != null) {
            if (activePathClip instanceof CustomPathClip customPathClip) {
                CameraSample customSample = customPathClip.sampleCamera(timeSeconds);
                if (customSample != null) {
                    lastActivePathClip = activePathClip;
                    lastSample = customSample;
                    return Optional.of(customSample);
                }
            }
            // 使用 Path Clip 计算位置和朝向
            CameraPathSample pathSample = activePathClip.sample(timeSeconds);

            // ⭐ 防御：如果 pathSample 为 null，fallback 到关键帧
            if (pathSample == null) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("[CameraTrackSampler] PathClip.sample() 返回 null @ {}s，fallback 到关键帧", timeSeconds);
                }
                // Fallback 到关键帧采样
                if (keyframes.isEmpty()) {
                    lastActivePathClip = null;
                    lastSample = null;
                    return Optional.empty();
                }
                Optional<CameraSample> keyframeSample = pathManager.sample(timeSeconds);
                if (keyframeSample.isEmpty()) {
                    lastActivePathClip = null;
                    lastSample = null;
                    return Optional.empty();
                }
                CameraSample kfSample = keyframeSample.get();
                cachedKeyframeSample = kfSample;
                position = kfSample.position();
                yaw = kfSample.yaw();
                pitch = kfSample.pitch();
            } else {
                position = pathSample.getPosition();

                // ⭐ 处理 lookAt 为 null 的情况（Dolly/Crane 保持构图）
                if (pathSample.hasLookAt()) {
                    // 有明确的 lookAt，从 lookAt 计算 yaw/pitch
                    Vec3d direction = pathSample.getDirection();
                    yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    pitch = (float) Math.toDegrees(Math.asin(-direction.y));
                } else {
                    // lookAt 为 null，沿用上一帧或关键帧的朝向
                    if (lastSample != null) {
                        // 使用上一帧的朝向（保持构图）
                        yaw = lastSample.yaw();
                        pitch = lastSample.pitch();
                    } else {
                        // 如果没有上一帧，从关键帧获取朝向
                        if (!keyframes.isEmpty()) {
                            Optional<CameraSample> keyframeSample = pathManager.sample(timeSeconds);
                            if (keyframeSample.isPresent()) {
                                CameraSample kfSample = keyframeSample.get();
                                cachedKeyframeSample = kfSample;
                                yaw = kfSample.yaw();
                                pitch = kfSample.pitch();
                            } else {
                                // 最后的 fallback：使用默认朝向
                                yaw = 0f;
                                pitch = 0f;
                            }
                        } else {
                            yaw = 0f;
                            pitch = 0f;
                        }
                    }
                }

                // ⭐ 调试日志：确认 PathClip 采样成功
                if (LOGGER.isDebugEnabled()) {
                    if (pathSample.hasLookAt()) {
                        Vec3d lookAt = pathSample.getLookAt();
                        if (lookAt != null) {
                            LOGGER.debug(
                                    "[CameraTrackSampler] 使用 PathClip 采样: {} @ {}s, pos=({}, {}, {}), lookAt=({}, {}, {})",
                                    activePathClip.getClass().getSimpleName(), timeSeconds,
                                    String.format("%.2f", position.x), String.format("%.2f", position.y), String.format("%.2f", position.z),
                                    String.format("%.2f", lookAt.x), String.format("%.2f", lookAt.y), String.format("%.2f", lookAt.z));
                        }
                    } else {
                        LOGGER.debug(
                                "[CameraTrackSampler] 使用 PathClip 采样: {} @ {}s, pos=({}, {}, {}), lookAt=null (沿用上一帧)",
                                activePathClip.getClass().getSimpleName(), timeSeconds,
                                String.format("%.2f", position.x), String.format("%.2f", position.y), String.format("%.2f", position.z));
                    }
                }
            }
        } else {
            // 使用关键帧插值计算位置和朝向
            if (keyframes.isEmpty()) {
                lastActivePathClip = null;
                lastSample = null;
                return Optional.empty();
            }
            Optional<CameraSample> keyframeSample = pathManager.sample(timeSeconds);
            if (keyframeSample.isEmpty()) {
                lastActivePathClip = null;
                lastSample = null;
                return Optional.empty();
            }
            CameraSample kfSample = keyframeSample.get();
            cachedKeyframeSample = kfSample;
            position = kfSample.position();
            yaw = kfSample.yaw();
            pitch = kfSample.pitch();
        }

        // FOV、Roll、DOF 等参数从关键帧获取（如果没有关键帧，使用默认值）
        float roll = 0f;
        float fov = 70f;
        float nearPlane = 0.1f;
        float farPlane = 1000f;
        boolean depthOfFieldEnabled = false;
        float focusDistance = 10f;
        float aperture = 2.8f;

        if (!keyframes.isEmpty()) {
            // ⭐ 优化：复用上面已经采样过的关键帧结果（如果有的话），
            //   避免对 pathManager.sample() 进行重复计算。
            //   仅当前面的分支没有调用过 pathManager.sample() 时才重新采样。
            CameraSample kfSampleForParams = cachedKeyframeSample;

            if (kfSampleForParams == null) {
                // 前面的分支没有采样过（例如 PathClip 有 lookAt 且有 lastSample 的情况）
                Optional<CameraSample> freshSample = pathManager.sample(timeSeconds);
                if (freshSample.isPresent()) {
                    kfSampleForParams = freshSample.get();
                }
            }

            if (kfSampleForParams != null) {
                roll = kfSampleForParams.roll();
                fov = kfSampleForParams.fov();
                nearPlane = kfSampleForParams.nearPlane();
                farPlane = kfSampleForParams.farPlane();
                depthOfFieldEnabled = kfSampleForParams.depthOfFieldEnabled();
                focusDistance = kfSampleForParams.focusDistance();
                aperture = kfSampleForParams.aperture();
            }
        }

        // 组合结果：位置/朝向来自 Path Clip（如果有），其他参数来自关键帧（或默认值）
        CameraSample result = new CameraSample(
                position,
                yaw,
                pitch,
                roll,
                fov,
                nearPlane,
                farPlane,
                depthOfFieldEnabled,
                focusDistance,
                aperture);

        // ⭐ 更新状态记录（用于下一帧检测"刚进入"事件）
        // ⭐ 重要：当离开 PathClip 时，需要重置 lastActivePathClip，确保下次进入时能触发 onEnter
        if (activePathClip == null && lastActivePathClip != null) {
            // 离开 PathClip，重置状态
            lastActivePathClip = null;
            // 注意：保留 lastSample，因为可能立即进入另一个 PathClip
        } else {
            lastActivePathClip = activePathClip;
        }
        lastSample = result;

        return Optional.of(result);
    }
}
