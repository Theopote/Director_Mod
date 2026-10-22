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
    private com.director.actor.rig.skeleton.PoseBlendSettings blendSettings = com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT;
    public com.director.actor.rig.skeleton.PoseBlendSettings getBlendSettings() { return blendSettings; }
    public void setBlendSettings(com.director.actor.rig.skeleton.PoseBlendSettings settings) { blendSettings = java.util.Objects.requireNonNull(settings); }


    private ActorPresetBinding preset;
    public ActorPresetBinding getPreset() { return preset; }
    public void setPreset(ActorPresetBinding value) { preset=value; }
    private String boneAnimationId;
    private EntityAnimationClip boneAnimation;
    public String getBoneAnimationId() {return boneAnimationId;}
    public void setBoneAnimationId(String id) {
        boneAnimationId=id;
        if(id!=null && (boneAnimation==null || !id.equals(boneAnimation.getAnimationId()))) {
            boneAnimation=new EntityAnimationClip("骨骼动作",getStartTime(),getEndTime());
            boneAnimation.setAnimationId(id);boneAnimation.setSource(EntityAnimationClip.AnimationSource.CUSTOM);boneAnimation.setLoop(false);
            new EntityTimelineClip(boneAnimation,0);
        }
    }
    public EntityAnimationClip getBoneAnimation() {
        if(boneAnimationId==null)return null;
        boneAnimation.setBlendSettings(blendSettings);
        boneAnimation.setTimeRange(getStartTime(),getEndTime());boneAnimation.setMuted(isMuted());
        if(getTimelineClip()!=null)boneAnimation.getTimelineClip().setParentTrack(getTimelineClip().getParentTrack());
        return boneAnimation;
    }
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

    private float rollDeg;
    private final Vector3f scale = new Vector3f(1);
    public float getRollDeg() { return rollDeg; }
    public void setRollDeg(float value) { if (Float.isFinite(value)) rollDeg = value; }
    public Vector3f getScale() { return scale; }
    public void setScale(float x, float y, float z) {
        if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && x > 0 && y > 0 && z > 0)
            scale.set(x, y, z);
    }

    public boolean isVisibleOnSpawn() {
        return visibleOnSpawn;
    }

    public void setVisibleOnSpawn(boolean visibleOnSpawn) {
        this.visibleOnSpawn = visibleOnSpawn;
    }
}

