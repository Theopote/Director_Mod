package com.director.timeline.model.entity;

import org.joml.Vector3f;

/**
 * EntitySpawnClip v1.0
 *
 * <p>Spawn：入场（生成或绑定时的初始姿态）。
 *
 * <p>注意：真正的"绑定/生成"通常在 Track.binding 里，
 * 但 SpawnClip 能表达"何时入场 + 入场位置/朝向/可见性"等导演语义。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntitySpawnClip extends EntityClip {

    private final Vector3f position = new Vector3f();
    private float yawDeg = 0f;
    private float pitchDeg = 0f;

    private boolean visibleOnSpawn = true;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntitySpawnClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.SPAWN;
    }

    // ========== Getters and Setters ==========

    public Vector3f getPosition() {
        return position;
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    public float getYawDeg() {
        return yawDeg;
    }

    public void setYawDeg(float yawDeg) {
        this.yawDeg = yawDeg;
    }

    public float getPitchDeg() {
        return pitchDeg;
    }

    public void setPitchDeg(float pitchDeg) {
        this.pitchDeg = pitchDeg;
    }

    public boolean isVisibleOnSpawn() {
        return visibleOnSpawn;
    }

    public void setVisibleOnSpawn(boolean visibleOnSpawn) {
        this.visibleOnSpawn = visibleOnSpawn;
    }
}

