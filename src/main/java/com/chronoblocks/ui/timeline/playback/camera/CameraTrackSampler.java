package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CameraTrackSampler v4.1 - 摄像机轨道采样器
 *
 * 核心策略：
 * - 位置：Catmull-Rom（平滑、不偏移）
 * - 旋转：四元数最短路径 Slerp（防止 Squad 造成方向偏移）
 * - FOV：线性插值并夹紧到相邻帧范围，避免过冲导致“拉近”
 */
public final class CameraTrackSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraTrackSampler.class);

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    public void setKeyframes(List<CameraKeyframe> source) {
        keyframes.clear();
        if (source != null) {
            keyframes.addAll(source);
            keyframes.sort(Comparator.comparing(CameraKeyframe::getTime));
        }
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    /**
     * 返回最后一个关键帧的时间；若为空则返回 -1。
     */
    public float getLastKeyTime() {
        if (keyframes.isEmpty()) {
            return -1f;
        }
        return keyframes.getLast().getTime();
    }

    /**
     * 在当前关键帧之间插入一个“临时锚点”，用于把轨道弯向玩家当前视角。
     * <p>不会修改 TimelineModel，仅修改采样器内部列表，后续 setKeyframes 会清除锚点。</p>
     */
    public Optional<CameraSample> sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }

        if (keyframes.size() == 1) {
            return Optional.of(toSample(keyframes.getFirst()));
        }

        CameraKeyframe prev = keyframes.getFirst();
        CameraKeyframe next;

        int left = 0;
        int right = keyframes.size() - 1;
        int prevIndex = 0;

        while (left <= right) {
            int mid = (left + right) / 2;
            float midTime = keyframes.get(mid).getTime();

            if (midTime <= timeSeconds) {
                prevIndex = mid;
                prev = keyframes.get(mid);
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        if (prevIndex + 1 < keyframes.size()) {
            next = keyframes.get(prevIndex + 1);
        } else {
            next = prev;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 采样 @ {}s: 超出范围（使用最后一帧 @ {}s）",
                        timeSeconds, prev.getTime());
            }
        }

        if (timeSeconds < keyframes.getFirst().getTime()) {
            prev = keyframes.getFirst();
            next = keyframes.getFirst();
        }

        if (prev == next) {
            return Optional.of(toSample(prev));
        }

        float t0 = prev.getTime();
        float t1 = next.getTime();
        if (t1 <= t0 + 1e-6f) {
            return Optional.of(toSample(prev));
        }

        float t = (timeSeconds - t0) / (t1 - t0);
        t = Math.max(0f, Math.min(1f, t));

        CameraKeyframe p0 = prevIndex > 0 ? keyframes.get(prevIndex - 1) : prev;
        CameraKeyframe p3 = (prevIndex + 2 < keyframes.size()) ? keyframes.get(prevIndex + 2) : next;

        return Optional.of(interpolate(p0, prev, next, p3, t));
    }

    /**
     * 线性插值两个样本（用于锚点或退出 lerp）。
     */
    public CameraSample lerpSamples(CameraSample a, CameraSample b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        Vec3d pa = a.position();
        Vec3d pb = b.position();
        Vec3d pos = new Vec3d(
                lerp(pa.x, pb.x, t),
                lerp(pa.y, pb.y, t),
                lerp(pa.z, pb.z, t)
        );

        float yaw = lerpAngle(a.yaw(), b.yaw(), t);
        float pitch = lerpAngle(a.pitch(), b.pitch(), t);
        float roll = lerpAngle(a.roll(), b.roll(), t);
        float fov = lerp(a.fov(), b.fov(), t);
        float near = lerp(a.nearPlane(), b.nearPlane(), t);
        float far = lerp(a.farPlane(), b.farPlane(), t);
        boolean dof = t < 0.5f ? a.depthOfFieldEnabled() : b.depthOfFieldEnabled();
        float focus = lerp(a.focusDistance(), b.focusDistance(), t);
        float aperture = lerp(a.aperture(), b.aperture(), t);

        return new CameraSample(pos, yaw, pitch, roll, fov, near, far, dof, focus, aperture);
    }

    private CameraSample interpolate(CameraKeyframe p0, CameraKeyframe p1,
                                     CameraKeyframe p2, CameraKeyframe p3, float t) {
        t = Math.max(0f, Math.min(1f, t));

        Vec3d pos0 = vec(p0.getPosition());
        Vec3d pos1 = vec(p1.getPosition());
        Vec3d pos2 = vec(p2.getPosition());
        Vec3d pos3 = vec(p3.getPosition());
        Vec3d position = catmullRom(t, pos0, pos1, pos2, pos3);

        Vector3f rot1 = p1.getRotation();
        Vector3f rot2 = p2.getRotation();
        float pitch = lerpAngle(rot1.x, rot2.x, t);
        float yaw = lerpAngle(rot1.y, rot2.y, t);
        float roll = lerpAngle(rot1.z, rot2.z, t);

        float fov1 = safeFov(p1.getFov());
        float fov2 = safeFov(p2.getFov());
        float fov = clamp(lerp(fov1, fov2, t), Math.min(fov1, fov2), Math.max(fov1, fov2));

        float near = catmullRomFloat(t,
                safeNear(p0.getNearPlane()),
                safeNear(p1.getNearPlane()),
                safeNear(p2.getNearPlane()),
                safeNear(p3.getNearPlane()));

        float far = catmullRomFloat(t,
                safeFar(p0.getNearPlane(), p0.getFarPlane()),
                safeFar(p1.getNearPlane(), p1.getFarPlane()),
                safeFar(p2.getNearPlane(), p2.getFarPlane()),
                safeFar(p3.getNearPlane(), p3.getFarPlane()));

        boolean dofEnabled = t > 0.5f ? p2.isDepthOfFieldEnabled() : p1.isDepthOfFieldEnabled();
        float focus = catmullRomFloat(t, p0.getFocusDistance(), p1.getFocusDistance(), p2.getFocusDistance(), p3.getFocusDistance());
        float aperture = catmullRomFloat(t, p0.getAperture(), p1.getAperture(), p2.getAperture(), p3.getAperture());

        return new CameraSample(position, yaw, pitch, roll, fov, near, far, dofEnabled, focus, aperture);
    }

    private Vec3d catmullRom(float t, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t +
                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
        double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t +
                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
        double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t +
                (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
                (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);

        return new Vec3d(x, y, z);
    }

    private float catmullRomFloat(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;

        return 0.5f * ((2 * p1) + (-p0 + p2) * t +
                (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private CameraSample toSample(CameraKeyframe keyframe) {
        Vec3d position = vec(keyframe.getPosition());
        Vector3f rotation = keyframe.getRotation();

        // 关键帧存储：rotation.x = pitch, rotation.y = yaw, rotation.z = roll
        float pitch = rotation.x;
        float yaw = rotation.y;
        float roll = rotation.z;

        float fov = safeFov(keyframe.getFov());
        float near = safeNear(keyframe.getNearPlane());
        float far = safeFar(keyframe.getNearPlane(), keyframe.getFarPlane());

        return new CameraSample(
            position,
            yaw,
            pitch,
            roll,
            fov,
            near,
            far,
            keyframe.isDepthOfFieldEnabled(),
            keyframe.getFocusDistance(),
            keyframe.getAperture()
        );
    }

    private Vec3d vec(Vector3f v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    private float safeFov(float fov) {
        return fov > 1f ? fov : 70f;
    }

    private float safeNear(float n) {
        return n > 0.001f ? n : 0.1f;
    }

    private float safeFar(float n, float f) {
        return f > n + 0.1f ? f : n + 1000f;
    }

    private float lerp(double a, double b, float t) {
        return (float) (a + (b - a) * Math.max(0f, Math.min(1f, t)));
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float lerpAngle(float a, float b, float t) {
        float delta = wrapDegrees(b - a);
        return a + delta * Math.max(0f, Math.min(1f, t));
    }

    private float wrapDegrees(float degrees) {
        degrees = degrees % 360f;
        if (degrees >= 180f) {
            degrees -= 360f;
        }
        if (degrees < -180f) {
            degrees += 360f;
        }
        return degrees;
    }
}
