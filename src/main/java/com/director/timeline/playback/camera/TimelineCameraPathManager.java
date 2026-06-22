package com.director.timeline.playback.camera;

import com.director.timeline.model.camera.CameraKeyframe;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * TimelineCameraPathManager v1.0
 *
 * 负责：
 *  - 维护摄像机关键帧列表
 *  - 根据 CameraPathType 对关键帧进行插值
 *  - 生成用于调试渲染的轨迹采样点
 *  - 作为 Timeline / Renderer / DebugRenderer 之间的“相机轨迹单一真相源”
 */
public final class TimelineCameraPathManager {

    /** 轨迹类型（整条轨道共用） */
    public enum CameraPathType {
        LINEAR,
        CATMULL_ROM,
        BEZIER,
        STEP
    }

    /** 排序后的关键帧列表（时间线上的绝对真相） */
    private final List<CameraKeyframe> keyframes = new ArrayList<>();

    /** 全局默认轨迹类型 */
    private CameraPathType pathType = CameraPathType.CATMULL_ROM;

    // ======================= 可视化采样缓存 =======================
    /** 调试轨迹采样点缓存（世界坐标）。仅用于可视化渲染，不参与实际采样。 */
    private final List<Vec3d> cachedPathPoints = new ArrayList<>();
    /** 轨迹缓存是否失效 */
    private boolean pathDirty = true;
    /** 每一段的最小采样数量 */
    private static final int MIN_SEGMENT_SAMPLES = 8;
    /** 最大总采样数量（防止 debug 渲染过于夸张） */
    private static final int MAX_TOTAL_SAMPLES = 512;

    // ======================= 基础管理 =======================

    /**
     * 设置关键帧列表（会进行时间排序）。
     * 调用后会自动标记轨迹缓存为 dirty。
     */
    public void setKeyframes(Collection<CameraKeyframe> frames) {
        keyframes.clear();
        if (frames != null && !frames.isEmpty()) {
            keyframes.addAll(frames);
            keyframes.sort(Comparator.comparingDouble(CameraKeyframe::getTime));
        }
        pathDirty = true;
    }

    public List<CameraKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    // ======================= 采样主入口 =======================

    /**
     * 在给定时间采样摄像机状态。
     *
     * @param timeSeconds 时间（秒）
     * @return 若没有关键帧则返回 empty，否则返回插值后的 CameraSample
     */
    public Optional<CameraSample> sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }

        if (keyframes.size() == 1) {
            return Optional.of(toSample(keyframes.getFirst()));
        }

        // STEP 全局模式
        if (pathType == CameraPathType.STEP) {
            CameraKeyframe stepKey = findStepKeyframe(timeSeconds);
            return Optional.of(toSample(stepKey));
        }

        // 找到 time 所在的关键帧区间 [prev, next]
        int nextIndex = findNextKeyframeIndex(timeSeconds);
        int prevIndex = nextIndex - 1;

        if (prevIndex < 0) {
            // 在第一个关键帧之前，直接贴第一个
            return Optional.of(toSample(keyframes.getFirst()));
        }
        if (nextIndex >= keyframes.size()) {
            // 超出最后一帧之后，贴最后一帧
            return Optional.of(toSample(keyframes.getLast()));
        }

        CameraKeyframe k0 = keyframes.get(prevIndex);
        CameraKeyframe k1 = keyframes.get(nextIndex);

        float t0 = k0.getTime();
        float t1 = k1.getTime();
        if (t1 <= t0 + 1e-6f) {
            return Optional.of(toSample(k0));
        }

        float localT = (timeSeconds - t0) / (t1 - t0);
        localT = clamp01(localT);

        // ⭐ 每段使用"起始"关键帧（k0）的 SegmentPathType 决定插值方式
        // 每个关键帧可以独立设置插值类型（LINEAR / CATMULL_ROM / BEZIER / STEP / HOLD）
        // 如果关键帧的 segmentType 为 DEFAULT，则使用全局 pathType（默认为 CATMULL_ROM）
        CameraPathType segmentType = resolveSegmentType(k0);

        // HOLD：从当前关键帧起，强制阶梯（保持当前值）
        if (safeKeyType(k0) == CameraKeyframe.KeyType.HOLD || segmentType == CameraPathType.STEP) {
            return Optional.of(toSample(k0));
        }

        // ⭐ 根据关键帧的 SegmentPathType 选择不同的插值方法
        // 这确保了属性面板和右键菜单设置的插值类型会实际生效
        return switch (segmentType) {
            case LINEAR -> Optional.of(sampleLinear(k0, k1, localT));
            case CATMULL_ROM -> Optional.of(sampleCatmullRom(prevIndex, nextIndex, localT));
            case BEZIER -> Optional.of(sampleBezier(prevIndex, nextIndex, localT));
            default -> throw new IllegalStateException("Unexpected value: " + segmentType);
        };
    }

    // ======================= 各种采样实现 =======================

    /** 找到“当前时间处于其后方”的关键帧索引，若超出范围返回 keyframes.size()。 */
    private int findNextKeyframeIndex(float timeSeconds) {
        for (int i = 0; i < keyframes.size(); i++) {
            if (timeSeconds <= keyframes.get(i).getTime()) {
                return i;
            }
        }
        return keyframes.size();
    }

    /** STEP 模式：落在哪个区间就使用前一个关键帧；在第一个关键帧之前，则使用第一个。 */
    private CameraKeyframe findStepKeyframe(float timeSeconds) {
        CameraKeyframe previous = keyframes.getFirst();
        for (int i = 1; i < keyframes.size(); i++) {
            CameraKeyframe current = keyframes.get(i);
            if (timeSeconds < current.getTime()) {
                break;
            }
            previous = current;
        }
        return previous;
    }

    private CameraSample sampleLinear(CameraKeyframe k0, CameraKeyframe k1, float t) {
        Vec3d p0 = vec(k0.getPosition());
        Vec3d p1 = vec(k1.getPosition());
        Vec3d pos = lerp(p0, p1, t);

        Vector3f rot = interpolateRotation(k0, k1, t, resolveRotationInterpolation(k0), null, null);

        float fov0 = safeFov(k0.getFov());
        float fov1 = safeFov(k1.getFov());
        float fov = lerpFloat(fov0, fov1, t);

        float near = lerpFloat(safeNear(k0.getNearPlane()), safeNear(k1.getNearPlane()), t);
        float far  = lerpFloat(
                safeFar(k0.getNearPlane(), k0.getFarPlane()),
                safeFar(k1.getNearPlane(), k1.getFarPlane()),
                t
        );

        boolean dofEnabled = t > 0.5f ? k1.isDepthOfFieldEnabled() : k0.isDepthOfFieldEnabled();
        float focus = lerpFloat(k0.getFocusDistance(), k1.getFocusDistance(), t);
        float aperture = lerpFloat(k0.getAperture(), k1.getAperture(), t);

        return new CameraSample(pos, rot.y, rot.x, rot.z, fov, near, far, dofEnabled, focus, aperture);
    }

    /**
     * Catmull-Rom 样条插值。
     *
     * 使用 prevIndex/nextIndex 作为当前段，自动查找 p0/p3 作为前后控制点。
     */
    private CameraSample sampleCatmullRom(int prevIndex, int nextIndex, float t) {

        int i0 = Math.max(0, prevIndex - 1);
        int i3 = Math.min(keyframes.size() - 1, nextIndex + 1);

        CameraKeyframe p0 = keyframes.get(i0);
        CameraKeyframe p1 = keyframes.get(prevIndex);
        CameraKeyframe p2 = keyframes.get(nextIndex);
        CameraKeyframe p3 = keyframes.get(i3);

        // CORNER：在 p1/p2 处打断切线，形成锐角
        if (safeKeyType(p1) == CameraKeyframe.KeyType.CORNER) {
            p0 = p1;
        }
        if (safeKeyType(p2) == CameraKeyframe.KeyType.CORNER) {
            p3 = p2;
        }

        Vec3d pos0 = vec(p0.getPosition());
        Vec3d pos1 = vec(p1.getPosition());
        Vec3d pos2 = vec(p2.getPosition());
        Vec3d pos3 = vec(p3.getPosition());
        Vec3d pos = catmullRom(pos0, pos1, pos2, pos3, t);

        // 旋转：目前仍用 Slerp，只是多参考 p0/p3，可在未来升级 Squad
        Vector3f rot = interpolateRotation(p1, p2, t, resolveRotationInterpolation(p1), p0, p3);

        float fov1 = safeFov(p1.getFov());
        float fov2 = safeFov(p2.getFov());
        float fov = lerpFloat(fov1, fov2, t);

        float near = catmullRomFloat(
                safeNear(p0.getNearPlane()),
                safeNear(p1.getNearPlane()),
                safeNear(p2.getNearPlane()),
                safeNear(p3.getNearPlane()),
                t
        );

        float far = catmullRomFloat(
                safeFar(p0.getNearPlane(), p0.getFarPlane()),
                safeFar(p1.getNearPlane(), p1.getFarPlane()),
                safeFar(p2.getNearPlane(), p2.getFarPlane()),
                safeFar(p3.getNearPlane(), p3.getFarPlane()),
                t
        );

        boolean dofEnabled = t > 0.5f ? p2.isDepthOfFieldEnabled() : p1.isDepthOfFieldEnabled();
        float focus = catmullRomFloat(
                p0.getFocusDistance(),
                p1.getFocusDistance(),
                p2.getFocusDistance(),
                p3.getFocusDistance(),
                t
        );
        float aperture = catmullRomFloat(
                p0.getAperture(),
                p1.getAperture(),
                p2.getAperture(),
                p3.getAperture(),
                t
        );

        return new CameraSample(pos, rot.y, rot.x, rot.z, fov, near, far, dofEnabled, focus, aperture);
    }

    /**
     * Bezier 采样（自动控制柄）。
     *
     * 仅对位置进行 cubic Bezier 插值；旋转/FOV/DOF 使用线性插值。
     */
    private CameraSample sampleBezier(int prevIndex, int nextIndex, float t) {

        int i0 = Math.max(0, prevIndex - 1);
        int i3 = Math.min(keyframes.size() - 1, nextIndex + 1);

        CameraKeyframe p0 = keyframes.get(i0);
        CameraKeyframe p1 = keyframes.get(prevIndex);
        CameraKeyframe p2 = keyframes.get(nextIndex);
        CameraKeyframe p3 = keyframes.get(i3);

        Vec3d P0 = vec(p1.getPosition());
        Vec3d P3 = vec(p2.getPosition());

        Vec3d autoOut = computeAutoOutHandle(p0, p1, p2);
        Vec3d autoIn  = computeAutoInHandle(p1, p2, p3);

        // 手动控制柄优先（MANUAL_BEZIER）
        Vec3d C1 = resolveHandleOut(p0, p1, p2, autoOut);
        Vec3d C2 = resolveHandleIn(p1, p2, p3, autoIn);

        Vec3d pos = cubicBezier(P0, C1, C2, P3, t);

        // 旋转插值（使用四元数）
        Vector3f rot = interpolateRotation(p1, p2, t, resolveRotationInterpolation(p1), p0, p3);

        float fov0 = safeFov(p1.getFov());
        float fov1 = safeFov(p2.getFov());
        float fov = lerpFloat(fov0, fov1, t);

        float near = lerpFloat(safeNear(p1.getNearPlane()), safeNear(p2.getNearPlane()), t);
        float far  = lerpFloat(
                safeFar(p1.getNearPlane(), p1.getFarPlane()),
                safeFar(p2.getNearPlane(), p2.getFarPlane()),
                t
        );

        boolean dofEnabled = t > 0.5f ? p2.isDepthOfFieldEnabled() : p1.isDepthOfFieldEnabled();
        float focus = lerpFloat(p1.getFocusDistance(), p2.getFocusDistance(), t);
        float aperture = lerpFloat(p1.getAperture(), p2.getAperture(), t);

        return new CameraSample(pos, rot.y, rot.x, rot.z, fov, near, far, dofEnabled, focus, aperture);
    }

    // ======================= 调试轨迹采样 =======================

    /**
     * 获取用于调试渲染的路径采样点（世界坐标）。
     *
     * 该列表是内部缓存的不可修改视图；如关键帧或路径类型有变化，会自动重新生成。
     */
    public List<Vec3d> getPathPointsForDebug() {
        if (!pathDirty && !cachedPathPoints.isEmpty()) {
            return Collections.unmodifiableList(cachedPathPoints);
        }

        cachedPathPoints.clear();

        if (keyframes.size() < 2) {
            pathDirty = false;
            return Collections.unmodifiableList(cachedPathPoints);
        }

        float start = keyframes.getFirst().getTime();
        float end   = keyframes.getLast().getTime();
        float duration = Math.max(0.0001f, end - start);

        int segments = Math.max(MIN_SEGMENT_SAMPLES, (int) (duration * 16)); // 约每 1s 采 16 段
        segments = Math.min(segments, MAX_TOTAL_SAMPLES);

        for (int i = 0; i <= segments; i++) {
            float t = start + duration * (i / (float) segments);
            sample(t).ifPresent(sample -> cachedPathPoints.add(sample.position()));
        }

        pathDirty = false;
        return Collections.unmodifiableList(cachedPathPoints);
    }

    public void markPathDirty() {
        this.pathDirty = true;
    }

    /** 调试用：获取所有 Bezier 段的控制点（世界坐标）。 */
    public List<BezierDebugSegment> getBezierSegmentsForDebug() {
        List<BezierDebugSegment> list = new ArrayList<>();
        if (keyframes.size() < 2) {
            return list;
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            CameraKeyframe k0 = keyframes.get(i);
            CameraKeyframe k1 = keyframes.get(i + 1);
            CameraPathType seg = resolveSegmentType(k0);
            if (seg != CameraPathType.BEZIER) {
                continue;
            }
            int i0 = Math.max(0, i - 1);
            int i3 = Math.min(keyframes.size() - 1, i + 2);
            CameraKeyframe p0 = keyframes.get(i0);
            CameraKeyframe p3 = keyframes.get(i3);

            Vec3d P0 = vec(k0.getPosition());
            Vec3d P3 = vec(k1.getPosition());

            Vec3d autoOut = computeAutoOutHandle(p0, k0, k1);
            Vec3d autoIn  = computeAutoInHandle(k0, k1, p3);

            Vec3d C1 = resolveHandleOut(p0, k0, k1, autoOut);
            Vec3d C2 = resolveHandleIn(k0, k1, p3, autoIn);

            list.add(new BezierDebugSegment(P0, C1, C2, P3));
        }
        return list;
    }

    // ======================= 辅助（段类型选择 & 手柄） =======================

    /**
     * ⭐ 解析关键帧的段类型
     * 
     * <p>关键帧的 SegmentPathType 优先于全局 pathType：
     * <ul>
     *   <li>如果关键帧设置了 LINEAR / CATMULL_ROM / BEZIER / STEP / HOLD，使用该设置</li>
     *   <li>如果关键帧设置为 DEFAULT，则回退到全局 pathType（默认为 CATMULL_ROM）</li>
     * </ul>
     * 
     * <p>这确保了属性面板和右键菜单对插值类型的修改会立即生效。
     */
    private CameraPathType resolveSegmentType(CameraKeyframe k0) {
        if (k0 == null) return pathType;
        return switch (k0.getSegmentType()) {
            case DEFAULT -> this.pathType; // 使用全局默认类型
            case LINEAR -> CameraPathType.LINEAR;
            case CATMULL_ROM -> CameraPathType.CATMULL_ROM;
            case BEZIER -> CameraPathType.BEZIER;
            case STEP, HOLD -> CameraPathType.STEP;
        };
    }

    private CameraKeyframe.KeyType safeKeyType(CameraKeyframe kf) {
        try {
            CameraKeyframe.KeyType t = kf.getKeyType();
            return t != null ? t : CameraKeyframe.KeyType.STANDARD;
        } catch (Throwable ignored) {
            return CameraKeyframe.KeyType.STANDARD;
        }
    }

    private CameraKeyframe.RotationInterpolation safeRotType(CameraKeyframe k) {
        try {
            CameraKeyframe.RotationInterpolation t = k.getRotationInterpolation();
            return t != null ? t : CameraKeyframe.RotationInterpolation.DEFAULT;
        } catch (Throwable ignored) {
            return CameraKeyframe.RotationInterpolation.DEFAULT;
        }
    }

    /**
     * ⭐ 解析关键帧的旋转插值类型
     * 
     * <p>关键帧的 RotationInterpolation 优先：
     * <ul>
     *   <li>如果关键帧明确设置了旋转插值类型（LINEAR / BEZIER / CATMULL_ROM / STEP），使用该设置</li>
     *   <li>如果关键帧设置为 DEFAULT，则根据位置段类型（SegmentPathType）自动推导</li>
     * </ul>
     * 
     * <p>这确保了旋转插值与位置插值可以独立设置，也支持"跟随位置段类型"的默认行为。
     */
    private CameraKeyframe.RotationInterpolation resolveRotationInterpolation(CameraKeyframe k0) {
        CameraKeyframe.RotationInterpolation t = safeRotType(k0);
        if (t != CameraKeyframe.RotationInterpolation.DEFAULT) {
            return t; // 关键帧明确设置了旋转插值类型
        }
        // 默认行为：跟随位置段类型（如位置用 LINEAR，旋转也用 LINEAR）
        CameraPathType seg = resolveSegmentType(k0);
        return switch (seg) {
            case STEP -> CameraKeyframe.RotationInterpolation.STEP;
            case LINEAR -> CameraKeyframe.RotationInterpolation.LINEAR;
            case CATMULL_ROM -> CameraKeyframe.RotationInterpolation.CATMULL_ROM;
            case BEZIER -> CameraKeyframe.RotationInterpolation.BEZIER;
        };
    }

    private Vec3d computeAutoOutHandle(CameraKeyframe prev, CameraKeyframe self, CameraKeyframe next) {
        Vec3d p0 = vec(prev.getPosition());
        Vec3d p1 = vec(self.getPosition());
        Vec3d p2 = vec(next.getPosition());
        Vec3d v = p2.subtract(p0).multiply(1.0 / 6.0);
        return p1.add(v);
    }

    private Vec3d computeAutoInHandle(CameraKeyframe self, CameraKeyframe next, CameraKeyframe next2) {
        Vec3d p0 = vec(self.getPosition());
        Vec3d p1 = vec(next.getPosition());
        Vec3d p2 = vec(next2.getPosition());
        Vec3d v = p2.subtract(p0).multiply(1.0 / 6.0);
        return p1.subtract(v);
    }

    // ======================= 基础工具方法 =======================

    private CameraSample toSample(CameraKeyframe keyframe) {
        Vec3d pos = vec(keyframe.getPosition());
        Vector3f rot = keyframe.getRotation();

        float fov  = safeFov(keyframe.getFov());
        float near = safeNear(keyframe.getNearPlane());
        float far  = safeFar(keyframe.getNearPlane(), keyframe.getFarPlane());

        return new CameraSample(
                pos,
                rot.y,           // yaw
                rot.x,           // pitch
                rot.z,           // roll
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

    private float safeNear(float near) {
        return near > 0.0001f ? near : 0.01f;
    }

    private float safeFar(float near, float far) {
        return Math.max(far, near + 0.1f);
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private float lerpFloat(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    private Vec3d lerp(Vec3d a, Vec3d b, float t) {
        t = clamp01(t);
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    // ============= 旋转插值（四元数核心） =============

    /**
     * 统一的旋转插值入口。
     * 目前实现：
     *  - STEP        → 保持 k0.rotation
     *  - 其他（LINEAR/BEZIER/CATMULL_ROM）→ 使用 Quaternion Slerp(k0, k1)
     *    （未来可根据 neighbors 升级为 Squad）
     */
    private Vector3f interpolateRotation(CameraKeyframe k0,
                                         CameraKeyframe k1,
                                         float t,
                                         CameraKeyframe.RotationInterpolation mode,
                                         CameraKeyframe neighborPrev,
                                         CameraKeyframe neighborNext) {
        t = clamp01(t);

        if (mode == CameraKeyframe.RotationInterpolation.STEP) {
            return new Vector3f(k0.getRotation());
        }

        // 当前版本：统一使用 Slerp(k0, k1)
        Quaternionf q0 = eulerToQuat(k0.getRotation());
        Quaternionf q1 = eulerToQuat(k1.getRotation());
        Quaternionf q = new Quaternionf(q0).slerp(q1, t);

        // 未来可以在此使用 neighbors 做 Squad 插值
        // if (mode == RotationInterpolation.BEZIER || mode == RotationInterpolation.CATMULL_ROM) { ... }

        return quatToEuler(q);
    }

    private Quaternionf eulerToQuat(Vector3f rotDeg) {
        // rot.x = pitch, rot.y = yaw, rot.z = roll
        float pitchRad = (float) Math.toRadians(rotDeg.x);
        float yawRad   = (float) Math.toRadians(rotDeg.y);
        float rollRad  = (float) Math.toRadians(rotDeg.z);

        // 按 yaw(Y) → pitch(X) → roll(Z) 顺序
        return new Quaternionf()
                .rotateY(yawRad)
                .rotateX(pitchRad)
                .rotateZ(rollRad);
    }

    private Vector3f quatToEuler(Quaternionf q) {
        // 将四元数转换为欧拉角（pitch, yaw, roll），按 YXZ 内旋顺序
        // R = Ry(yaw) * Rx(pitch) * Rz(roll)，对应旋转矩阵元素：
        //   R[1][0] = 2(xy+wz),  R[1][1] = 1-2(x²+z²),  R[1][2] = 2(yz-wx)
        //   R[0][2] = 2(xz+wy),  R[2][2] = 1-2(x²+y²)

        // roll (Z) = atan2(R[1][0], R[1][1])
        float t0 = +2.0f * (q.w * q.z + q.x * q.y);
        float t1 = +1.0f - 2.0f * (q.x * q.x + q.z * q.z);
        float roll = (float) Math.toDegrees(Math.atan2(t0, t1));

        // pitch (X) = asin(-R[1][2]) = asin(2(wx - yz))
        float t2 = +2.0f * (q.w * q.x - q.z * q.y);
        t2 = Math.max(-1.0f, Math.min(1.0f, t2));
        float pitch = (float) Math.toDegrees(Math.asin(t2));

        // yaw (Y) = atan2(R[0][2], R[2][2])
        float t3 = +2.0f * (q.w * q.y + q.z * q.x);
        float t4 = +1.0f - 2.0f * (q.x * q.x + q.y * q.y);
        float yaw = (float) Math.toDegrees(Math.atan2(t3, t4));

        return new Vector3f(pitch, yaw, roll);
    }

    private float catmullRomFloat(float p0, float p1, float p2, float p3, float t) {
        t = clamp01(t);
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
                2f * p1 +
                        (-p0 + p2) * t +
                        (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                        (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        );
    }

    private Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        t = clamp01(t);
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * (
                2.0 * p1.x +
                        (-p0.x + p2.x) * t +
                        (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 +
                        (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3
        );

        double y = 0.5 * (
                2.0 * p1.y +
                        (-p0.y + p2.y) * t +
                        (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 +
                        (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3
        );

        double z = 0.5 * (
                2.0 * p1.z +
                        (-p0.z + p2.z) * t +
                        (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2 +
                        (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3
        );

        return new Vec3d(x, y, z);
    }

    private Vec3d cubicBezier(Vec3d P0, Vec3d C1, Vec3d C2, Vec3d P3, float t) {
        t = clamp01(t);
        double u = 1.0 - t;
        double u2 = u * u;
        double u3 = u2 * u;
        double t2 = t * t;
        double t3 = t2 * t;

        double x = u3 * P0.x +
                3.0 * u2 * t * C1.x +
                3.0 * u * t2 * C2.x +
                t3 * P3.x;

        double y = u3 * P0.y +
                3.0 * u2 * t * C1.y +
                3.0 * u * t2 * C2.y +
                t3 * P3.y;

        double z = u3 * P0.z +
                3.0 * u2 * t * C1.z +
                3.0 * u * t2 * C2.z +
                t3 * P3.z;

        return new Vec3d(x, y, z);
    }

    // ======================= 手柄解析 =======================

    private Vec3d resolveHandleOut(CameraKeyframe prev, CameraKeyframe self, CameraKeyframe next, Vec3d autoOut) {
        if (self.getKeyType() == CameraKeyframe.KeyType.MANUAL_BEZIER || self.getSegmentType() == CameraKeyframe.SegmentPathType.BEZIER) {
            if (self.getHandleMode() == CameraKeyframe.HandleMode.AUTO) {
                return autoOut;
            }
            Vec3d manual = vec(self.getBezierOut());
            if (manual.lengthSquared() < 1e-6) {
                manual = autoOut;
            }
            if (self.getHandleMode() == CameraKeyframe.HandleMode.ALIGNED) {
                // 与 in 对齐
                Vec3d in = vec(self.getBezierIn());
                if (in.lengthSquared() > 1e-6) {
                    double len = manual.length();
                    // 方向相反，长度对齐
                    manual = in.normalize().multiply(-len);
                }
            }
            return manual;
        }
        return autoOut;
    }

    private Vec3d resolveHandleIn(CameraKeyframe self, CameraKeyframe next, CameraKeyframe next2, Vec3d autoIn) {
        if (next.getKeyType() == CameraKeyframe.KeyType.MANUAL_BEZIER || next.getSegmentType() == CameraKeyframe.SegmentPathType.BEZIER) {
            if (next.getHandleMode() == CameraKeyframe.HandleMode.AUTO) {
                return autoIn;
            }
            Vec3d manual = vec(next.getBezierIn());
            if (manual.lengthSquared() < 1e-6) {
                manual = autoIn;
            }
            if (next.getHandleMode() == CameraKeyframe.HandleMode.ALIGNED) {
                Vec3d out = vec(next.getBezierOut());
                if (out.lengthSquared() > 1e-6) {
                    double len = manual.length();
                    // 对齐反向
                    manual = out.normalize().multiply(-len);
                }
            }
            return manual;
        }
        return autoIn;
    }

    /** 调试用 Bezier 段记录 */
    public record BezierDebugSegment(Vec3d p0, Vec3d c1, Vec3d c2, Vec3d p3) {}
}

