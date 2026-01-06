package com.chronoblocks.ui.timeline.model.entity;

import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import org.joml.Vector3f;

/**
 * EntityTransformClip v1.0
 *
 * <p>Transform：位移/旋转/缩放片段。
 *
 * <p>你后面一定会要"路径"，所以这里我先给两层：
 * <ul>
 *   <li>v1：from/to + 插值</li>
 *   <li>v2：可扩展 pathId（复用 CameraPath）</li>
 * </ul>
 *
 * <p>特点：
 * <ul>
 *   <li>不依赖 Minecraft AI（可控、可复现）</li>
 *   <li>支持路径插值（Linear, Bezier, CatmullRom）</li>
 *   <li>可复用 Camera Path 系统</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EntityTransformClip extends EntityClip {

    private TimelineInterpolation interpolation = TimelineInterpolation.SMOOTH;

    private final Vector3f fromPos = new Vector3f();
    private final Vector3f toPos = new Vector3f();

    private final Vector3f fromRot = new Vector3f(); // yaw,pitch,roll
    private final Vector3f toRot = new Vector3f();

    private final Vector3f fromScale = new Vector3f(1, 1, 1);
    private final Vector3f toScale = new Vector3f(1, 1, 1);

    /** 预留：复用相机路径/实体路径 */
    private String pathId = null;
    private boolean followPath = false;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityTransformClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.TRANSFORM;
    }

    // ========== Getters and Setters ==========

    public TimelineInterpolation getInterpolation() {
        return interpolation;
    }

    public void setInterpolation(TimelineInterpolation interpolation) {
        this.interpolation = interpolation != null ? interpolation : TimelineInterpolation.SMOOTH;
    }

    public Vector3f getFromPos() {
        return fromPos;
    }

    public Vector3f getToPos() {
        return toPos;
    }

    public void setFromPos(float x, float y, float z) {
        fromPos.set(x, y, z);
    }

    public void setToPos(float x, float y, float z) {
        toPos.set(x, y, z);
    }

    public Vector3f getFromRot() {
        return fromRot;
    }

    public Vector3f getToRot() {
        return toRot;
    }

    public void setFromRot(float yaw, float pitch, float roll) {
        fromRot.set(yaw, pitch, roll);
    }

    public void setToRot(float yaw, float pitch, float roll) {
        toRot.set(yaw, pitch, roll);
    }

    public Vector3f getFromScale() {
        return fromScale;
    }

    public Vector3f getToScale() {
        return toScale;
    }

    public void setFromScale(float x, float y, float z) {
        fromScale.set(x, y, z);
    }

    public void setToScale(float x, float y, float z) {
        toScale.set(x, y, z);
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public boolean isFollowPath() {
        return followPath;
    }

    public void setFollowPath(boolean followPath) {
        this.followPath = followPath;
    }
}

