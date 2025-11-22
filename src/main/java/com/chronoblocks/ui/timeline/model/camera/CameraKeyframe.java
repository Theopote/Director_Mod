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
    
    private final UUID id;
    private String name;
    private float time;
    private Vector3f position;
    private Vector3f rotation; // 偏航、俯仰、翻滚（度）
    private float fov;
    private float nearPlane;
    private float farPlane;
    
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
    
    public boolean isDepthOfFieldEnabled() { return depthOfFieldEnabled; }
    public void setDepthOfFieldEnabled(boolean enabled) { this.depthOfFieldEnabled = enabled; }
    
    public float getFocusDistance() { return focusDistance; }
    public void setFocusDistance(float distance) { this.focusDistance = distance; }
    
    public float getAperture() { return aperture; }
    public void setAperture(float aperture) { this.aperture = aperture; }
}

