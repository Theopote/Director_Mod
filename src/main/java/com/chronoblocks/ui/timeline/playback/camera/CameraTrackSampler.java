package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathSample;
import net.minecraft.util.math.Vec3d;

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

    /** 排序后的关键帧列表 */
    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    
    /** 摄像机轨道引用（用于访问 Path Clips） */
    private TimelineTrack cameraTrack;

    /** 内部使用的 PathManager（用于实际采样） */
    private final TimelineCameraPathManager pathManager = new TimelineCameraPathManager();

    public CameraTrackSampler() {
    }
    
    /**
     * 设置摄像机轨道引用（用于访问 Path Clips）
     */
    public void setCameraTrack(TimelineTrack track) {
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
     * ⭐ 在指定时间采样摄像机状态（优先使用 Path Clip）
     * 
     * <p>采样优先级：
     * <ol>
     *   <li>如果存在激活的 CameraPathClip，使用 Path Clip 计算位置和朝向</li>
     *   <li>否则，使用关键帧插值计算位置和朝向</li>
     *   <li>FOV、Roll、DOF 等参数从关键帧获取（如果没有关键帧，使用默认值）</li>
     * </ol>
     * 
     * @param timeSeconds 时间（秒）
     * @return 如果成功采样则返回 CameraSample，否则返回 empty
     */
    public Optional<CameraSample> sample(float timeSeconds) {
        // ⭐ 优先检查 Path Clip
        CameraPathClip activePathClip = null;
        if (cameraTrack != null && cameraTrack.hasCameraPathClips()) {
            activePathClip = cameraTrack.findActivePathClip(timeSeconds);
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
        } else {
            // 使用关键帧插值计算位置和朝向
            if (keyframes.isEmpty()) {
                return Optional.empty();
            }
            Optional<CameraSample> keyframeSample = pathManager.sample(timeSeconds);
            if (keyframeSample.isEmpty()) {
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
        return Optional.of(new CameraSample(
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
        ));
    }
}
