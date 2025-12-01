package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CameraTrackSampler FINAL v3.0
 *
 * <p>⭐ 关键修复：
 * <ul>
 *   <li>正确的 yaw/pitch 顺序（rotation.x=pitch, rotation.y=yaw, rotation.z=roll）</li>
 *   <li>正确的 prev/next 查找，避免跳帧</li>
 *   <li>完整的 FOV/near/far/dof 插值</li>
 * </ul>
 */
public final class CameraTrackSampler {

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
     * 在指定时间采样摄像机状态
     *
     * <p>⭐ 核心算法：
     * <ol>
     *   <li>找到 currentTime 前后的最近两个关键帧 (prev, next)</li>
     *   <li>如果 prev == next（恰好在帧上或只有一帧），直接返回该帧</li>
     *   <li>否则计算插值系数 t = (currentTime - prev.time) / (next.time - prev.time)</li>
     *   <li>对所有参数进行线性插值（角度使用 wrapDegrees 处理）</li>
     * </ol>
     *
     * @param timeSeconds 时间（秒）
     * @return 插值后的摄像机样本
     */
    public Optional<CameraSample> sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }

        // 单帧 → 直接返回
        if (keyframes.size() == 1) {
            return Optional.of(toSample(keyframes.getFirst()));
        }

        // ⭐ 有多帧 → 使用二分搜索找到 prev / next（性能优化）
        // 初始化为首尾帧（这样即使时间在左侧/右侧溢出也有正确的回退值）
        CameraKeyframe prev = keyframes.getFirst();
        CameraKeyframe next = keyframes.getLast();

        // ⭐ 性能优化：使用二分搜索（O(log n)）而不是线性搜索（O(n)）
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
        
        // 找到 next（第一个 >= timeSeconds 的帧）
        if (prevIndex + 1 < keyframes.size()) {
            next = keyframes.get(prevIndex + 1);
        } else {
            next = prev; // 时间在所有关键帧右侧
        }

        // prev == next 说明：
        // 1. 时间恰好在某个关键帧上
        // 2. 或时间在所有关键帧左侧（prev=first, next=first）
        // 3. 或时间在所有关键帧右侧（prev=last, next=last）
        if (prev == next) {
            return Optional.of(toSample(prev));
        }

        // 计算插值系数
        float t0 = prev.getTime();
        float t1 = next.getTime();
        if (t1 <= t0 + 1e-6f) {
            // 防止除以 0（理论上不会发生，因为已排序且 prev != next）
            return Optional.of(toSample(prev));
        }

        float t = (timeSeconds - t0) / (t1 - t0);
        t = Math.max(0f, Math.min(1f, t)); // clamp [0, 1]

        return Optional.of(interpolate(prev, next, t));
    }

    /**
     * 在两个关键帧之间进行插值
     *
     * @param a 前一个关键帧
     * @param b 后一个关键帧
     * @param t 插值系数 [0, 1]
     */
    private CameraSample interpolate(CameraKeyframe a, CameraKeyframe b, float t) {
        t = Math.max(0f, Math.min(1f, t)); // 确保 t 在 [0, 1]

        // 位置插值
        Vec3d posA = vec(a.getPosition());
        Vec3d posB = vec(b.getPosition());
        Vec3d position = new Vec3d(
            MathHelper.lerp(t, posA.x, posB.x),
            MathHelper.lerp(t, posA.y, posB.y),
            MathHelper.lerp(t, posA.z, posB.z)
        );

        // ⭐ 正确 yaw/pitch 插值（rotation.x=pitch, rotation.y=yaw, rotation.z=roll）
        Vector3f rotA = a.getRotation();
        Vector3f rotB = b.getRotation();
        float pitch = lerpAngle(rotA.x, rotB.x, t);  // x = pitch
        float yaw = lerpAngle(rotA.y, rotB.y, t);    // y = yaw
        float roll = lerpAngle(rotA.z, rotB.z, t);    // z = roll

        // ⭐ FOV 插值 + 防御
        float fov = MathHelper.lerp(t, safeFov(a.getFov()), safeFov(b.getFov()));

        // 近远裁剪面插值 + 防御
        float near = MathHelper.lerp(t, safeNear(a.getNearPlane()), safeNear(b.getNearPlane()));
        float far = MathHelper.lerp(t, safeFar(a.getNearPlane(), a.getFarPlane()), 
                                       safeFar(b.getNearPlane(), b.getFarPlane()));

        // ⭐ DOF: bool 使用 t > 0.5 判断，数值做插值
        boolean dofEnabled = t > 0.5f ? b.isDepthOfFieldEnabled() : a.isDepthOfFieldEnabled();
        float focus = MathHelper.lerp(t, a.getFocusDistance(), b.getFocusDistance());
        float aperture = MathHelper.lerp(t, a.getAperture(), b.getAperture());

        return new CameraSample(position, yaw, pitch, roll, fov, near, far, dofEnabled, focus, aperture);
    }

    /**
     * 从单个关键帧创建样本
     */
    private CameraSample toSample(CameraKeyframe keyframe) {
        Vec3d position = vec(keyframe.getPosition());
        Vector3f rotation = keyframe.getRotation();

        // ⭐ 使用安全方法
        float fov = safeFov(keyframe.getFov());
        float near = safeNear(keyframe.getNearPlane());
        float far = safeFar(keyframe.getNearPlane(), keyframe.getFarPlane());

        // ⭐ 正确 yaw/pitch 顺序（rotation.x=pitch, rotation.y=yaw, rotation.z=roll）
        return new CameraSample(
            position,
            rotation.y,   // yaw
            rotation.x,   // pitch
            rotation.z,   // roll
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

    /**
     * 插值角度，使用"最近路径"避免 359°→0° 走一圈
     */
    private float lerpAngle(float a, float b, float t) {
        float delta = MathHelper.wrapDegrees(b - a);
        return a + delta * t;
    }

    /**
     * 安全的 FOV 值（防御 NaN 和无效值）
     */
    private float safeFov(float fov) {
        return fov > 1f ? fov : 70f;
    }

    /**
     * 安全的近裁剪面值
     */
    private float safeNear(float n) {
        return n > 0.001f ? n : 0.1f;
    }

    /**
     * 安全的远裁剪面值
     */
    private float safeFar(float n, float f) {
        return f > n + 0.1f ? f : n + 1000f;
    }
}
