package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;

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

    /** 内部使用的 PathManager（用于实际采样） */
    private final TimelineCameraPathManager pathManager = new TimelineCameraPathManager();

    public CameraTrackSampler() {
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
     * 在指定时间采样摄像机状态
     * 
     * @param timeSeconds 时间（秒）
     * @return 如果成功采样则返回 CameraSample，否则返回 empty
     */
    public Optional<CameraSample> sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }

        // 委托给 PathManager 进行采样（复用现有实现）
        return pathManager.sample(timeSeconds);
    }
}
