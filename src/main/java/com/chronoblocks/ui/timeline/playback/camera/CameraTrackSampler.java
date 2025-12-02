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
 * CameraTrackSampler v4.0 - 摄像机轨道采样器
 *
 * <p>⭐ 核心特性：
 * <ul>
 *   <li>位置插值：使用 Catmull-Rom 样条，提供平滑的摄像机运动</li>
 *   <li>旋转插值：使用四元数 Squad（Spherical Quadrangle Interpolation），避免万向节死锁和抖动</li>
 *   <li>正确的 yaw/pitch 顺序（rotation.x=pitch, rotation.y=yaw, rotation.z=roll）</li>
 *   <li>完整的 FOV/near/far/dof 插值</li>
 * </ul>
 * 
 * <p>🎬 插值算法：
 * <ul>
 *   <li>位置：Catmull-Rom 样条（4 点控制，平滑曲线）</li>
 *   <li>旋转：四元数 Squad（避免欧拉角插值的万向节死锁问题）</li>
 *   <li>其他参数：Catmull-Rom 样条（FOV、近远裁剪面等）</li>
 * </ul>
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
        CameraKeyframe next;

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
            // ⭐ 关键修复：时间在所有关键帧右侧时，使用最后一个关键帧
            // 但这里应该明确：如果时间超出范围，是否应该返回最后一个关键帧？
            // 根据用户反馈，这可能导致"总是显示最后一帧"的问题
            next = prev; // 时间在所有关键帧右侧，使用最后一个关键帧
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 采样 @ {}s: 超出范围（>最后一个关键帧 @ {}s），使用最后一个关键帧", 
                    timeSeconds, prev.getTime());
            }
        }
        
        // ⭐ 关键修复：检查时间是否在所有关键帧左侧
        if (timeSeconds < keyframes.getFirst().getTime()) {
            prev = keyframes.getFirst();
            next = keyframes.getFirst();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 采样 @ {}s: 超出范围（<第一个关键帧 @ {}s），使用第一个关键帧", 
                    timeSeconds, prev.getTime());
            }
        }

        // prev == next 说明：
        // 1. 时间恰好在某个关键帧上
        // 2. 或时间在所有关键帧左侧（prev=first, next=first）
        // 3. 或时间在所有关键帧右侧（prev=last, next=last）
        if (prev == next) {
            CameraSample result = toSample(prev);
            // ⭐ 调试日志：帮助定位"总是返回最后一帧"的问题
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 采样 @ {}s: 单帧/边界，使用关键帧 @ {}s, FOV={}", 
                    timeSeconds, prev.getTime(), result.fov());
            }
            return Optional.of(result);
        }

        // 计算插值系数
        float t0 = prev.getTime();
        float t1 = next.getTime();
        if (t1 <= t0 + 1e-6f) {
            // 防止除以 0（理论上不会发生，因为已排序且 prev != next）
            CameraSample result = toSample(prev);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CameraTrackSampler] 采样 @ {}s: 时间间隔过小，使用关键帧 @ {}s, FOV={}", 
                    timeSeconds, prev.getTime(), result.fov());
            }
            return Optional.of(result);
        }

        float t = (timeSeconds - t0) / (t1 - t0);
        t = Math.max(0f, Math.min(1f, t)); // clamp [0, 1]

        // ⭐ Catmull-Rom 插值：需要获取前一个（p0）和后一个（p3）关键帧
        CameraKeyframe p0 = prev;  // 默认：如果不存在前一个，使用 prev
        CameraKeyframe p3 = next;  // 默认：如果不存在后一个，使用 next
        
        // 获取 p0（prev 的前一个关键帧）
        if (prevIndex > 0) {
            p0 = keyframes.get(prevIndex - 1);
        }
        
        // 获取 p3（next 的后一个关键帧）
        if (prevIndex + 2 < keyframes.size()) {
            p3 = keyframes.get(prevIndex + 2);
        }

        CameraSample result = interpolateCatmullRom(p0, prev, next, p3, t);
        // ⭐ 调试日志：帮助定位插值问题
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[CameraTrackSampler] 采样 @ {}s: Catmull-Rom 插值在 [{}, {}] 之间, t={}, FOV={} (从 {} 到 {})", 
                timeSeconds, t0, t1, t, result.fov(), 
                safeFov(prev.getFov()), safeFov(next.getFov()));
        }
        return Optional.of(result);
    }

    /**
     * 使用 Catmull-Rom 样条在两个关键帧之间进行平滑插值
     *
     * @param p0 前一个关键帧的前一个（如果不存在，使用 p1）
     * @param p1 前一个关键帧（prev）
     * @param p2 后一个关键帧（next）
     * @param p3 后一个关键帧的后一个（如果不存在，使用 p2）
     * @param t 插值系数 [0, 1]，在 p1 和 p2 之间
     */
    private CameraSample interpolateCatmullRom(CameraKeyframe p0, CameraKeyframe p1, 
                                                CameraKeyframe p2, CameraKeyframe p3, float t) {
        t = Math.max(0f, Math.min(1f, t)); // 确保 t 在 [0, 1]

        // 位置插值（Catmull-Rom）
        Vec3d pos0 = vec(p0.getPosition());
        Vec3d pos1 = vec(p1.getPosition());
        Vec3d pos2 = vec(p2.getPosition());
        Vec3d pos3 = vec(p3.getPosition());
        Vec3d position = catmullRom(t, pos0, pos1, pos2, pos3);

        // ⭐ 旋转插值：使用四元数球面插值（Slerp）避免万向节死锁
        // 将欧拉角转换为四元数，进行球面插值，再转换回欧拉角
        Vector3f rot0 = p0.getRotation();
        Vector3f rot1 = p1.getRotation();
        Vector3f rot2 = p2.getRotation();
        Vector3f rot3 = p3.getRotation();
        
        // 使用四元数进行平滑旋转插值
        float[] rotation = interpolateRotationWithQuaternion(
            rot0.x, rot0.y, rot0.z,  // pitch, yaw, roll
            rot1.x, rot1.y, rot1.z,
            rot2.x, rot2.y, rot2.z,
            rot3.x, rot3.y, rot3.z,
            t
        );
        
        float pitch = rotation[0];
        float yaw = rotation[1];
        float roll = rotation[2];

        // ⭐ FOV 插值（Catmull-Rom）
        float fov0 = safeFov(p0.getFov());
        float fov1 = safeFov(p1.getFov());
        float fov2 = safeFov(p2.getFov());
        float fov3 = safeFov(p3.getFov());
        float fov = catmullRomFloat(t, fov0, fov1, fov2, fov3);

        // 近远裁剪面插值（Catmull-Rom）
        float near0 = safeNear(p0.getNearPlane());
        float near1 = safeNear(p1.getNearPlane());
        float near2 = safeNear(p2.getNearPlane());
        float near3 = safeNear(p3.getNearPlane());
        float near = catmullRomFloat(t, near0, near1, near2, near3);
        
        float far0 = safeFar(p0.getNearPlane(), p0.getFarPlane());
        float far1 = safeFar(p1.getNearPlane(), p1.getFarPlane());
        float far2 = safeFar(p2.getNearPlane(), p2.getFarPlane());
        float far3 = safeFar(p3.getNearPlane(), p3.getFarPlane());
        float far = catmullRomFloat(t, far0, far1, far2, far3);

        // ⭐ DOF: bool 使用 t > 0.5 判断，数值做插值
        boolean dofEnabled = t > 0.5f ? p2.isDepthOfFieldEnabled() : p1.isDepthOfFieldEnabled();
        
        float focus0 = p0.getFocusDistance();
        float focus1 = p1.getFocusDistance();
        float focus2 = p2.getFocusDistance();
        float focus3 = p3.getFocusDistance();
        float focus = catmullRomFloat(t, focus0, focus1, focus2, focus3);
        
        float aperture0 = p0.getAperture();
        float aperture1 = p1.getAperture();
        float aperture2 = p2.getAperture();
        float aperture3 = p3.getAperture();
        float aperture = catmullRomFloat(t, aperture0, aperture1, aperture2, aperture3);

        return new CameraSample(position, yaw, pitch, roll, fov, near, far, dofEnabled, focus, aperture);
    }

    /**
     * Catmull-Rom 样条插值（3D 位置）
     * 
     * @param t 插值系数 [0, 1]，在 p1 和 p2 之间
     * @param p0 前一个点
     * @param p1 起始点
     * @param p2 结束点
     * @param p3 后一个点
     * @return 插值后的位置
     */
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

    /**
     * Catmull-Rom 样条插值（浮点数）
     * 
     * @param t 插值系数 [0, 1]
     * @param p0 前一个值
     * @param p1 起始值
     * @param p2 结束值
     * @param p3 后一个值
     * @return 插值后的值
     */
    private float catmullRomFloat(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;

        return 0.5f * ((2 * p1) + (-p0 + p2) * t + 
                      (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + 
                      (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /**
     * 使用四元数球面插值（Slerp）进行旋转插值，避免万向节死锁
     * 
     * @param pitch0, yaw0, roll0 前一个关键帧的旋转（欧拉角，度）
     * @param pitch1, yaw1, roll1 起始关键帧的旋转
     * @param pitch2, yaw2, roll2 结束关键帧的旋转
     * @param pitch3, yaw3, roll3 后一个关键帧的旋转
     * @param t 插值系数 [0, 1]，在 p1 和 p2 之间
     * @return [pitch, yaw, roll] 插值后的旋转（欧拉角，度）
     */
    private float[] interpolateRotationWithQuaternion(
            float pitch0, float yaw0, float roll0,
            float pitch1, float yaw1, float roll1,
            float pitch2, float yaw2, float roll2,
            float pitch3, float yaw3, float roll3,
            float t) {
        
        // 将欧拉角转换为四元数
        Quaternion q0 = eulerToQuaternion(pitch0, yaw0, roll0);
        Quaternion q1 = eulerToQuaternion(pitch1, yaw1, roll1);
        Quaternion q2 = eulerToQuaternion(pitch2, yaw2, roll2);
        Quaternion q3 = eulerToQuaternion(pitch3, yaw3, roll3);
        
        // ⭐ 使用四元数球面样条插值（Squad）进行平滑旋转
        // Squad 是 Catmull-Rom 样条在四元数空间的等价物，可以避免万向节死锁
        Quaternion result = quaternionSquad(q0, q1, q2, q3, t);
        
        // 将四元数转换回欧拉角
        return quaternionToEuler(result);
    }
    
    /**
     * 四元数结构（用于旋转插值）
     */
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
                return new Quaternion(1, 0, 0, 0);  // 单位四元数
            }
            return new Quaternion(w / len, x / len, y / len, z / len);
        }
        
        float dot(Quaternion other) {
            return w * other.w + x * other.x + y * other.y + z * other.z;
        }
    }
    
    /**
     * 将欧拉角（度）转换为四元数
     * Minecraft 使用 ZYX 顺序（Roll-Pitch-Yaw）
     */
    private Quaternion eulerToQuaternion(float pitchDeg, float yawDeg, float rollDeg) {
        float pitch = (float) Math.toRadians(pitchDeg);
        float yaw = (float) Math.toRadians(yawDeg);
        float roll = (float) Math.toRadians(rollDeg);
        
        // ZYX 顺序（Roll, Pitch, Yaw）
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
    
    /**
     * 将四元数转换为欧拉角（度）
     * 返回 [pitch, yaw, roll]
     */
    private float[] quaternionToEuler(Quaternion q) {
        q = q.normalize();
        
        // ZYX 顺序（Roll, Pitch, Yaw）
        float w = q.w, x = q.x, y = q.y, z = q.z;
        
        // Roll (绕 Z 轴)
        float sinr_cosp = 2 * (w * z + x * y);
        float cosr_cosp = 1 - 2 * (y * y + z * z);
        float roll = (float) Math.atan2(sinr_cosp, cosr_cosp);
        
        // Pitch (绕 Y 轴)
        float sinp = 2 * (w * y - z * x);
        float pitch;
        if (Math.abs(sinp) >= 1) {
            pitch = (float) Math.copySign(Math.PI / 2, sinp);  // 使用 90 度避免万向节死锁
        } else {
            pitch = (float) Math.asin(sinp);
        }
        
        // Yaw (绕 X 轴)
        float siny_cosp = 2 * (w * x + y * z);
        float cosy_cosp = 1 - 2 * (x * x + y * y);
        float yaw = (float) Math.atan2(siny_cosp, cosy_cosp);
        
        return new float[]{
            (float) Math.toDegrees(pitch),
            (float) Math.toDegrees(yaw),
            (float) Math.toDegrees(roll)
        };
    }
    
    /**
     * 四元数球面线性插值（Slerp）
     * 
     * @param q1 起始四元数
     * @param q2 结束四元数
     * @param t 插值系数 [0, 1]
     * @return 插值后的四元数
     */
    private Quaternion quaternionSlerp(Quaternion q1, Quaternion q2, float t) {
        q1 = q1.normalize();
        q2 = q2.normalize();
        
        float dot = q1.dot(q2);
        
        // 如果点积为负，取反其中一个四元数以选择最短路径
        if (dot < 0.0f) {
            q2 = new Quaternion(-q2.w, -q2.x, -q2.y, -q2.z);
            dot = -dot;
        }
        
        // 如果两个四元数非常接近，使用线性插值避免数值不稳定
        if (dot > 0.9995f) {
            float w = q1.w + (q2.w - q1.w) * t;
            float x = q1.x + (q2.x - q1.x) * t;
            float y = q1.y + (q2.y - q1.y) * t;
            float z = q1.z + (q2.z - q1.z) * t;
            return new Quaternion(w, x, y, z).normalize();
        }
        
        // 标准 Slerp
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
    
    /**
     * 四元数球面样条插值（Squad - Spherical Quadrangle Interpolation）
     * 这是 Catmull-Rom 样条在四元数空间的等价物，可以创建平滑的旋转曲线
     * 
     * @param q0 前一个控制点
     * @param q1 起始点
     * @param q2 结束点
     * @param q3 后一个控制点
     * @param t 插值系数 [0, 1]，在 q1 和 q2 之间
     * @return 插值后的四元数
     */
    private Quaternion quaternionSquad(Quaternion q0, Quaternion q1, Quaternion q2, Quaternion q3, float t) {
        q0 = q0.normalize();
        q1 = q1.normalize();
        q2 = q2.normalize();
        q3 = q3.normalize();
        
        // 计算中间控制点（s1 和 s2）
        // 这些点用于创建平滑的样条曲线
        Quaternion s1 = computeSquadIntermediate(q0, q1, q2);
        Quaternion s2 = computeSquadIntermediate(q1, q2, q3);
        
        // Squad 公式：Squad(q1, q2, s1, s2, t) = Slerp(Slerp(q1, q2, t), Slerp(s1, s2, t), 2t(1-t))
        Quaternion a = quaternionSlerp(q1, q2, t);
        Quaternion b = quaternionSlerp(s1, s2, t);
        float u = 2.0f * t * (1.0f - t);  // 样条混合系数
        
        return quaternionSlerp(a, b, u);
    }
    
    /**
     * 计算 Squad 的中间控制点
     * 这个点用于创建平滑的样条曲线，类似于 Catmull-Rom 样条的切线
     * 
     * @param q0 前一个点
     * @param q1 当前点
     * @param q2 下一个点
     * @return 中间控制点
     */
    private Quaternion computeSquadIntermediate(Quaternion q0, Quaternion q1, Quaternion q2) {
        // 简化的 Squad 中间点计算
        // 使用 log(q1^-1 * q2) 和 log(q1^-1 * q0) 的平均值
        // 但为了简化，我们使用 Slerp 来近似
        
        // 计算 q1 到 q2 的方向
        Quaternion q1ToQ2 = quaternionMultiply(quaternionInverse(q1), q2);
        Quaternion q1ToQ0 = quaternionMultiply(quaternionInverse(q1), q0);
        
        // 使用简化的方法：计算两个方向的平均值
        Quaternion avg = quaternionSlerp(q1ToQ0, q1ToQ2, 0.5f);
        
        // 将平均值应用到 q1
        return quaternionMultiply(q1, avg);
    }
    
    /**
     * 四元数乘法
     */
    private Quaternion quaternionMultiply(Quaternion a, Quaternion b) {
        float w = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z;
        float x = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y;
        float y = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x;
        float z = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w;
        return new Quaternion(w, x, y, z).normalize();
    }
    
    /**
     * 四元数共轭（逆旋转）
     */
    private Quaternion quaternionInverse(Quaternion q) {
        // 对于单位四元数，共轭就是逆
        return new Quaternion(q.w, -q.x, -q.y, -q.z);
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