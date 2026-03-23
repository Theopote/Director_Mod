package com.director.ui.timeline.model.entity;

import org.joml.Vector3f;

import java.util.UUID;

/**
 * EntityLookAtClip v1.0
 *
 * <p>LookAt：看向某个点/某个实体/摄像机。
 *
 * <p>目标类型：
 * <ul>
 *   <li>WORLD_POINT - 世界坐标点</li>
 *   <li>ENTITY_UUID - 其他实体</li>
 *   <li>CAMERA - 摄像机（看向镜头）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityLookAtClip extends EntityClip {

    public enum TargetMode {
        WORLD_POINT("世界坐标"),
        ENTITY_UUID("实体"),
        CAMERA("摄像机");

        private final String displayName;

        TargetMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private TargetMode mode = TargetMode.CAMERA;

    private final Vector3f worldPoint = new Vector3f();
    private UUID targetEntityUuid = null;

    /** 头部跟随强度（0-1） */
    private float strength = 1.0f;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityLookAtClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.LOOK_AT;
    }

    // ========== Getters and Setters ==========

    public TargetMode getMode() {
        return mode;
    }

    public void setMode(TargetMode mode) {
        this.mode = mode != null ? mode : TargetMode.CAMERA;
    }

    public Vector3f getWorldPoint() {
        return worldPoint;
    }

    public void setWorldPoint(float x, float y, float z) {
        worldPoint.set(x, y, z);
    }

    public UUID getTargetEntityUuid() {
        return targetEntityUuid;
    }

    public void setTargetEntityUuid(UUID targetEntityUuid) {
        this.targetEntityUuid = targetEntityUuid;
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = Math.max(0f, Math.min(1f, strength));
    }
}

