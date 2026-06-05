package com.director.ui.timeline.model.camera.path;

import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.playback.camera.CameraSample;
import com.director.ui.timeline.playback.camera.TimelineCameraPathManager;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 用户自定义摄像机路径片段。
 *
 * <p>原先直接挂在摄像机轨道上的 CameraKeyframe 现在由这个片段持有。
 */
public class CustomPathClip extends CameraPathClip {

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    private final TimelineCameraPathManager pathManager = new TimelineCameraPathManager();
    private boolean dirty = true;

    public CustomPathClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public CameraPathType getPathType() {
        return CameraPathType.CUSTOM;
    }

    public List<CameraKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    public boolean hasKeyframes() {
        return !keyframes.isEmpty();
    }

    public boolean containsKeyframe(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return false;
        }
        return keyframes.stream().anyMatch(k -> k != null && keyframe.getId().equals(k.getId()));
    }

    public void addKeyframe(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }
        keyframes.removeIf(k -> k != null && keyframe.getId().equals(k.getId()));
        keyframes.add(keyframe);
        sortAndMarkDirty();
    }

    public boolean removeKeyframe(UUID keyframeId) {
        if (keyframeId == null) {
            return false;
        }
        boolean removed = keyframes.removeIf(k -> k != null && keyframeId.equals(k.getId()));
        if (removed) {
            sortAndMarkDirty();
        }
        return removed;
    }

    public CameraKeyframe findKeyframe(UUID keyframeId) {
        if (keyframeId == null) {
            return null;
        }
        for (CameraKeyframe keyframe : keyframes) {
            if (keyframe != null && keyframeId.equals(keyframe.getId())) {
                return keyframe;
            }
        }
        return null;
    }

    public void onKeyframeChanged() {
        sortAndMarkDirty();
    }

    public CameraSample sampleCamera(float timeSeconds) {
        rebuildIfDirty();
        return pathManager.sample(timeSeconds).orElse(null);
    }

    public void offsetKeyframes(float deltaSeconds) {
        if (Math.abs(deltaSeconds) < 0.0001f) {
            return;
        }
        for (CameraKeyframe keyframe : keyframes) {
            if (keyframe != null) {
                keyframe.setTime(keyframe.getTime() + deltaSeconds);
            }
        }
        sortAndMarkDirty();
    }

    public void remapKeyframes(float oldStart, float oldEnd, float newStart, float newEnd) {
        float oldDuration = oldEnd - oldStart;
        float newDuration = newEnd - newStart;
        for (CameraKeyframe keyframe : keyframes) {
            if (keyframe == null) {
                continue;
            }
            float normalized = oldDuration > 0.0001f
                    ? (keyframe.getTime() - oldStart) / oldDuration
                    : 0f;
            normalized = Math.max(0f, Math.min(1f, normalized));
            keyframe.setTime(newStart + normalized * Math.max(0f, newDuration));
        }
        sortAndMarkDirty();
    }

    @Override
    public CameraPathSample sample(float timeSeconds) {
        CameraSample sample = sampleCamera(timeSeconds);
        if (sample == null) {
            return null;
        }

        Vec3d position = sample.position();
        double yawRad = Math.toRadians(sample.yaw());
        double pitchRad = Math.toRadians(sample.pitch());
        Vec3d forward = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        );
        return new CameraPathSample(position, position.add(forward));
    }

    @Override
    public CameraPathClip copy() {
        CustomPathClip copy = new CustomPathClip(getName() + " (Copy)", getStartTime(), getEndTime());
        for (CameraKeyframe keyframe : keyframes) {
            copy.addKeyframe(copyKeyframe(keyframe));
        }
        copy.setEnabled(isEnabled());
        return copy;
    }

    private void sortAndMarkDirty() {
        keyframes.sort(Comparator.comparing(CameraKeyframe::getTime));
        dirty = true;
    }

    private void rebuildIfDirty() {
        if (!dirty) {
            return;
        }
        pathManager.setKeyframes(keyframes);
        dirty = false;
    }

    private static CameraKeyframe copyKeyframe(CameraKeyframe source) {
        CameraKeyframe copy = new CameraKeyframe(source.getName(), source.getTime());
        copy.setPosition(new org.joml.Vector3f(source.getPosition()));
        copy.setRotation(new org.joml.Vector3f(source.getRotation()));
        copy.setFov(source.getFov());
        copy.setNearPlane(source.getNearPlane());
        copy.setFarPlane(source.getFarPlane());
        copy.setKeyType(source.getKeyType());
        copy.setSegmentPathType(source.getSegmentPathType());
        copy.setRotationInterpolation(source.getRotationInterpolation());
        copy.setBezierInHandle(source.getBezierInHandle());
        copy.setBezierOutHandle(source.getBezierOutHandle());
        copy.setHandleMode(source.getHandleMode());
        copy.setDepthOfFieldEnabled(source.isDepthOfFieldEnabled());
        copy.setFocusDistance(source.getFocusDistance());
        copy.setAperture(source.getAperture());
        return copy;
    }
}
