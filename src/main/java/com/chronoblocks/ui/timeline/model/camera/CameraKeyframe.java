package com.chronoblocks.ui.timeline.model.camera;

import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 摄像机关键帧
 * 
 * <p>表示时间线上的一个摄像机关键帧，包含摄像机的位置、旋转、FOV等信息。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>支持摄像机动画路径</li>
 *   <li>可以在时间线上选择和编辑</li>
 *   <li>属性面板可以显示摄像机相关属性</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class CameraKeyframe implements TimelineSelectable {
    public enum KeyType {
        STANDARD,   // 菱形
        CORNER,     // 三角形（转折）
        HOLD,       // 方形（停顿/保持）
        SMOOTH,     // 圆形（自动平滑）
        MANUAL_BEZIER // 六边形（手动贝塞尔）
    }

    public enum SegmentPathType {
        DEFAULT,      // 使用全局路径类型
        LINEAR,
        CATMULL_ROM,
        BEZIER,
        STEP,
        HOLD          // 等价 STEP（保持）
    }

    public enum HandleMode {
        AUTO,      // 自动平滑
        FREE,      // 独立自由控制
        ALIGNED    // 进出柄对齐（方向一致，长度可同）
    }

    /**
     * 旋转插值类型：控制「旋转」曲线
     * 
     * - DEFAULT   : 跟随 SegmentPathType（Linear/Bezier/CatmullRom → 使用 Slerp；Step/Hold → Step）
     * - LINEAR    : 使用 Quaternion Slerp
     * - BEZIER    : 预留（目前也是 Slerp，将来可升级为 Squad）
     * - CATMULL_ROM: 预留（目前也是 Slerp，将来可升级为 Squad）
     * - STEP      : 旋转不插值（保持上一帧）
     */
    public enum RotationInterpolation {
        DEFAULT,
        LINEAR,
        BEZIER,
        CATMULL_ROM,
        STEP
    }

    private final UUID id;
    private String name;
    private float time;
    private Vector3f position;
    private Vector3f rotation; // 偏航、俯仰、翻滚（度）
    private float fov;
    private float nearPlane;
    private float farPlane;
    private KeyType keyType = KeyType.STANDARD;
    private SegmentPathType segmentType = SegmentPathType.DEFAULT;
    private RotationInterpolation rotationInterpolation = RotationInterpolation.DEFAULT;
    private Vector3f bezierInHandle = new Vector3f(0, 0, 0);
    private Vector3f bezierOutHandle = new Vector3f(0, 0, 0);
    private HandleMode handleMode = HandleMode.AUTO;
    
    // 摄像机特效
    private boolean depthOfFieldEnabled;
    private float focusDistance;
    private float aperture;
    
    public CameraKeyframe(String name, float time) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.time = time;
        this.position = new Vector3f(0, 0, 0);
        this.rotation = new Vector3f(0, 0, 0);
        this.fov = 70.0f;
        this.nearPlane = 0.1f;
        this.farPlane = 1000.0f;
        this.depthOfFieldEnabled = false;
        this.focusDistance = 10.0f;
        this.aperture = 2.8f;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public float getStartTime() {
        return time;
    }
    
    @Override
    public float getEndTime() {
        return time; // 关键帧是瞬时点，开始和结束时间相同
    }
    
    @Override
    public String getType() {
        return "CameraKeyframe";
    }
    
    // Getters and Setters
    public float getTime() { return time; }
    public void setTime(float time) { this.time = time; }
    
    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f position) { this.position = position; }
    
    public Vector3f getRotation() { return rotation; }
    public void setRotation(Vector3f rotation) { this.rotation = rotation; }
    
    public float getFov() { return fov; }
    public void setFov(float fov) { this.fov = fov; }
    
    public float getNearPlane() { return nearPlane; }
    public void setNearPlane(float nearPlane) { this.nearPlane = nearPlane; }
    
    public float getFarPlane() { return farPlane; }
    public void setFarPlane(float farPlane) { this.farPlane = farPlane; }

    public KeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(KeyType keyType) {
        this.keyType = keyType == null ? KeyType.STANDARD : keyType;
    }

    public SegmentPathType getSegmentType() {
        return segmentType;
    }

    public void setSegmentType(SegmentPathType segmentType) {
        this.segmentType = segmentType == null ? SegmentPathType.DEFAULT : segmentType;
    }

    // === v2.0 兼容别名 ===
    public SegmentPathType getSegmentPathType() {
        return getSegmentType();
    }

    public void setSegmentPathType(SegmentPathType segmentPathType) {
        setSegmentType(segmentPathType);
    }

    public RotationInterpolation getRotationInterpolation() {
        return rotationInterpolation;
    }

    public void setRotationInterpolation(RotationInterpolation rotationInterpolation) {
        this.rotationInterpolation = rotationInterpolation == null ? RotationInterpolation.DEFAULT : rotationInterpolation;
    }

    public Vector3f getBezierIn() {
        return bezierInHandle;
    }

    public Vector3f getBezierOut() {
        return bezierOutHandle;
    }

    public Vector3f getBezierInHandle() {
        return bezierInHandle;
    }

    public Vector3f getBezierOutHandle() {
        return bezierOutHandle;
    }

    public void setBezierIn(Vector3f v) {
        if (v != null) {
            this.bezierInHandle = new Vector3f(v);
        }
    }

    public void setBezierOut(Vector3f v) {
        if (v != null) {
            this.bezierOutHandle = new Vector3f(v);
        }
    }

    public void setBezierInHandle(Vector3f v) {
        setBezierIn(v);
    }

    public void setBezierOutHandle(Vector3f v) {
        setBezierOut(v);
    }

    public HandleMode getHandleMode() {
        return handleMode;
    }

    public void setHandleMode(HandleMode mode) {
        this.handleMode = mode == null ? HandleMode.AUTO : mode;
    }

    // === v2.0 辅助判断 ===
    public boolean isManualBezier() {
        return keyType == KeyType.MANUAL_BEZIER || segmentType == SegmentPathType.BEZIER;
    }

    public boolean hasControlHandles() {
        return bezierInHandle.lengthSquared() > 1e-6f || bezierOutHandle.lengthSquared() > 1e-6f;
    }
    
    public boolean isDepthOfFieldEnabled() { return depthOfFieldEnabled; }
    public void setDepthOfFieldEnabled(boolean enabled) { this.depthOfFieldEnabled = enabled; }
    
    public float getFocusDistance() { return focusDistance; }
    public void setFocusDistance(float distance) { this.focusDistance = distance; }
    
    public float getAperture() { return aperture; }
    public void setAperture(float aperture) { this.aperture = aperture; }
}

