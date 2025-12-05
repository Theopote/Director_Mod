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
    private static final float ANCHOR_BLEND_T = 0.9f;
    private static final float EPSILON = 1e-4f;

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    /** 临时锚点关键帧（仅存在于采样器内部，不写回模型） */
    private CameraKeyframe anchorKeyframe;

    public void setKeyframes(List<CameraKeyframe> source) {
        clearAnchor();
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
     * 在当前关键帧之间插入一个“临时锚点”，用于把轨道弯向玩家当前视角。
     * <p>不会修改 TimelineModel，仅修改采样器内部列表，后续 setKeyframes 会清除锚点。</p>
     */
    public void ensureAnchorAt(float timeSeconds, CameraSample playerSample) {
        clearAnchor();
        if (playerSample == null || keyframes.isEmpty()) {
            return;
        }

        // 先用当前轨道采样一次，得到原始轨道的镜头
        CameraSample trackSample = sample(timeSeconds).orElse(null);
        if (trackSample == null) {
            trackSample = toSample(keyframes.getFirst());
        }

        // 按权重融合，向玩家视角弯折
        CameraSample blended = lerpSamples(trackSample, playerSample, ANCHOR_BLEND_T);

        CameraKeyframe anchor = new CameraKeyframe("CameraAnchor", timeSeconds);
        applySampleToKeyframe(anchor, blended);

        // 插入到有序列表（保持时间排序）
        int insertIndex = 0;
        while (insertIndex < keyframes.size() && keyframes.get(insertIndex).getTime() < timeSeconds - EPSILON) {
            insertIndex++;
        }
        keyframes.add(insertIndex, anchor);
        anchorKeyframe = anchor;
    }

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

    private void clearAnchor() {
        if (anchorKeyframe != null) {
            keyframes.remove(anchorKeyframe);
            anchorKeyframe = null;
        }
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
        float[] rotation = interpolateRotationSlerp(
            rot1.x, rot1.y, rot1.z,
            rot2.x, rot2.y, rot2.z,
            t
        );
        float pitch = rotation[0];
        float yaw = rotation[1];
        float roll = rotation[2];

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

    private float[] interpolateRotationSlerp(
            float pitch1, float yaw1, float roll1,
            float pitch2, float yaw2, float roll2,
            float t) {

        Quaternion q1 = eulerToQuaternion(pitch1, yaw1, roll1);
        Quaternion q2 = eulerToQuaternion(pitch2, yaw2, roll2);

        Quaternion result = quaternionSlerp(q1, q2, t);
        return quaternionToEuler(result);
    }

    private static class Quaternion {
        float w, x, y, z;

        Quaternion(float w, float x, float y, float z) {
            this.w = w;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Quaternion normalize() {
            float len = (float) Math.sqrt(w * w + x * x + y * y + z * z);
            if (len < 1e-6f) {
                return new Quaternion(1, 0, 0, 0);
            }
            return new Quaternion(w / len, x / len, y / len, z / len);
        }

        float dot(Quaternion other) {
            return w * other.w + x * other.x + y * other.y + z * other.z;
        }
    }

    private Quaternion eulerToQuaternion(float pitchDeg, float yawDeg, float rollDeg) {
        float pitch = (float) Math.toRadians(pitchDeg);
        float yaw = (float) Math.toRadians(yawDeg);
        float roll = (float) Math.toRadians(rollDeg);

        float cy = (float) Math.cos(yaw * 0.5f);
        float sy = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f);
        float sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f);
        float sr = (float) Math.sin(roll * 0.5f);

        float w = cr * cp * cy + sr * sp * sy;
        float x = sr * cp * cy - cr * sp * sy;
        float y = cr * sp * cy + sr * cp * sy;
        float z = cr * cp * sy - sr * sp * cy;

        return new Quaternion(w, x, y, z).normalize();
    }

    private float[] quaternionToEuler(Quaternion q) {
        q = q.normalize();

        float w = q.w, x = q.x, y = q.y, z = q.z;

        float sinr_cosp = 2 * (w * z + x * y);
        float cosr_cosp = 1 - 2 * (y * y + z * z);
        float roll = (float) Math.atan2(sinr_cosp, cosr_cosp);

        float sinp = 2 * (w * y - z * x);
        float pitch;
        if (Math.abs(sinp) >= 1) {
            pitch = (float) Math.copySign(Math.PI / 2, sinp);
        } else {
            pitch = (float) Math.asin(sinp);
        }

        float siny_cosp = 2 * (w * x + y * z);
        float cosy_cosp = 1 - 2 * (x * x + y * y);
        float yaw = (float) Math.atan2(siny_cosp, cosy_cosp);

        return new float[]{
            (float) Math.toDegrees(pitch),
            (float) Math.toDegrees(yaw),
            (float) Math.toDegrees(roll)
        };
    }

    private Quaternion quaternionSlerp(Quaternion q1, Quaternion q2, float t) {
        q1 = q1.normalize();
        q2 = q2.normalize();

        float dot = q1.dot(q2);

        if (dot < 0.0f) {
            q2 = new Quaternion(-q2.w, -q2.x, -q2.y, -q2.z);
            dot = -dot;
        }

        if (dot > 0.9995f) {
            float w = q1.w + (q2.w - q1.w) * t;
            float x = q1.x + (q2.x - q1.x) * t;
            float y = q1.y + (q2.y - q1.y) * t;
            float z = q1.z + (q2.z - q1.z) * t;
            return new Quaternion(w, x, y, z).normalize();
        }

        float theta = (float) Math.acos(Math.max(-1.0f, Math.min(1.0f, dot)));
        float sinTheta = (float) Math.sin(theta);
        float w1 = (float) (Math.sin((1 - t) * theta) / sinTheta);
        float w2 = (float) (Math.sin(t * theta) / sinTheta);

        float w = q1.w * w1 + q2.w * w2;
        float x = q1.x * w1 + q2.x * w2;
        float y = q1.y * w1 + q2.y * w2;
        float z = q1.z * w1 + q2.z * w2;

        return new Quaternion(w, x, y, z).normalize();
    }

    private CameraSample toSample(CameraKeyframe keyframe) {
        Vec3d position = vec(keyframe.getPosition());
        Vector3f rotation = keyframe.getRotation();

        float fov = safeFov(keyframe.getFov());
        float near = safeNear(keyframe.getNearPlane());
        float far = safeFar(keyframe.getNearPlane(), keyframe.getFarPlane());

        return new CameraSample(
            position,
            rotation.y,
            rotation.x,
            rotation.z,
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

    private void applySampleToKeyframe(CameraKeyframe kf, CameraSample sample) {
        kf.setPosition(new Vector3f(
                (float) sample.position().x,
                (float) sample.position().y,
                (float) sample.position().z
        ));
        kf.setRotation(new Vector3f(
                sample.pitch(),
                sample.yaw(),
                sample.roll()
        ));
        kf.setFov(sample.fov());
        kf.setNearPlane(sample.nearPlane());
        kf.setFarPlane(sample.farPlane());
        kf.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        kf.setFocusDistance(sample.focusDistance());
        kf.setAperture(sample.aperture());
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
