package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathEnterContext;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathSample;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CameraTrackSampler v1.0
 *
 * <p>负责对单个摄像机轨道进行采样。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>维护一组 CameraKeyframe（通常来自某条 CAMERA 轨道）</li>
 *   <li>根据关键帧的 SegmentPathType / RotationInterpolation 等进行插值</li>
 *   <li>提供 sample(time) 方法，返回 CameraSample</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>只负责采样逻辑，不关心轨道管理</li>
 *   <li>采样策略与 TimelineCameraPathManager 保持一致</li>
 *   <li>为了代码复用，内部使用 TimelineCameraPathManager 进行实际采样</li>
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

    public CameraTrackSampler() {
    }
    
    /**
     * 设置摄像机轨道引用（用于访问 Path Clips）
     * 
     * <p>⭐ 重要：当轨道切换时，重置状态跟踪，确保新的 PathClip 能正确触发 onEnter()
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
     * <p>这是解决"播放停止后再次播放时 PathClip 不响应"的关键方法
     */
    public void onStop() {
        // ⭐ 清空 lastActivePathClip，确保下次进入 PathClip 时能触发 onEnter()
        lastActivePathClip = null;
        // 注意：保留 lastSample，因为可能立即进入另一个 PathClip
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[CameraTrackSampler] 停止播放，清空 PathClip 状态");
        }
    }

    /**
     * ⭐ 在指定时间采样摄像机状态（优先使用 Path Clip）
     * 
     * <p>采样优先级：
     * <ol>
     *   <li>如果存在激活的 CameraPathClip，使用 Path Clip 计算位置和朝向</li>
     *   <li>否则，使用关键帧插值计算位置和朝向</li>
     *   <li>FOV、Roll、DOF 等参数从关键帧获取（如果没有关键帧，使用默认值）</li>
     * </ol>
     * 
     * <p>⭐ 新增功能：检测"刚进入"PathClip 事件，触发 onEnter() 生命周期钩子
     * 
     * @param timeSeconds 时间（秒）
     * @return 如果成功采样则返回 CameraSample，否则返回 empty
     */
    public Optional<CameraSample> sample(float timeSeconds) {
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
                // 注意：CameraSample 只有 position，没有 lookAt，需要从 yaw/pitch 计算
                Vec3d lastPos = lastSample.position();

                // 从 yaw/pitch 计算 lookAt（近似值）
                // 这是一个简化实现，如果需要更精确的 lookAt，可以从 CameraRuntime 获取
                double yawRad = Math.toRadians(lastSample.yaw());
                double pitchRad = Math.toRadians(lastSample.pitch());
                double cosPitch = Math.cos(pitchRad);
                Vec3d lastLookAt = lastPos.add(
                    -Math.sin(yawRad) * cosPitch * 10.0,  // 向前看 10 个单位
                    -Math.sin(pitchRad) * 10.0,
                    Math.cos(yawRad) * cosPitch * 10.0
                );

                CameraPathEnterContext ctx = new CameraPathEnterContext(lastPos, lastLookAt);
                activePathClip.onEnter(ctx);
            } else {
                // 如果没有上一帧状态，尝试从 CameraRuntime 获取当前状态
                try {
                    CameraRuntime runtime = CameraRuntime.getInstance();
                    CameraSample currentSample = runtime.getCurrentSample();
                    if (currentSample != null) {
                        Vec3d currentPos = currentSample.position();
                        // 从 yaw/pitch 计算 lookAt（近似值）
                        double yawRad = Math.toRadians(currentSample.yaw());
                        double pitchRad = Math.toRadians(currentSample.pitch());
                        double cosPitch = Math.cos(pitchRad);
                        Vec3d currentLookAt = currentPos.add(
                            -Math.sin(yawRad) * cosPitch * 10.0,
                            -Math.sin(pitchRad) * 10.0,
                            Math.cos(yawRad) * cosPitch * 10.0
                        );
                        CameraPathEnterContext ctx = new CameraPathEnterContext(currentPos, currentLookAt);
                        activePathClip.onEnter(ctx);
                    }
                } catch (Exception e) {
                    // 如果获取失败，跳过 onEnter（不影响正常采样）
                }
            }
        }
        
        Vec3d position;
        float yaw, pitch;
        
        if (activePathClip != null) {
            // 使用 Path Clip 计算位置和朝向
            CameraPathSample pathSample = activePathClip.sample(timeSeconds);
            position = pathSample.getPosition();
            
            // 从 lookAt 计算 yaw/pitch
            Vec3d direction = pathSample.getDirection();
            yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            pitch = (float) Math.toDegrees(Math.asin(-direction.y));
            
            // ⭐ 调试日志：确认 PathClip 采样成功
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 使用 PathClip 采样: {} @ {}s, pos=({:.2f}, {:.2f}, {:.2f}), lookAt=({:.2f}, {:.2f}, {:.2f})", 
                    activePathClip.getClass().getSimpleName(), timeSeconds,
                    position.x, position.y, position.z,
                    pathSample.getLookAt().x, pathSample.getLookAt().y, pathSample.getLookAt().z);
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
            Optional<CameraSample> keyframeSample = pathManager.sample(timeSeconds);
            if (keyframeSample.isPresent()) {
                CameraSample kfSample = keyframeSample.get();
                roll = kfSample.roll();
                fov = kfSample.fov();
                nearPlane = kfSample.nearPlane();
                farPlane = kfSample.farPlane();
                depthOfFieldEnabled = kfSample.depthOfFieldEnabled();
                focusDistance = kfSample.focusDistance();
                aperture = kfSample.aperture();
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
                aperture
        );
        
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
