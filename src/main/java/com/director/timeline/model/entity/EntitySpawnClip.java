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
    public static final float DEFAULT_DURATION_SECONDS = 5f;
    private com.director.actor.rig.skeleton.PoseBlendSettings blendSettings = com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT;
    public com.director.actor.rig.skeleton.PoseBlendSettings getBlendSettings() { return blendSettings; }
    public void setBlendSettings(com.director.actor.rig.skeleton.PoseBlendSettings settings) {
        blendSettings = java.util.Objects.requireNonNull(settings);
        syncBoneAnimation();
        samplingChanged();
    }


    private ActorPresetBinding preset;
    public ActorPresetBinding getPreset() { return preset; }
    private long presetRevision;
    public long getPresetRevision() { return presetRevision; }
    public void setPreset(ActorPresetBinding value) {
        if(!java.util.Objects.equals(preset,value)) { preset=value; presetRevision++; samplingChanged(); }
    }
    private ActorPresetBinding secondaryPreset;
    public ActorPresetBinding getSecondaryPreset() { return secondaryPreset; }
    public void setSecondaryPreset(ActorPresetBinding value) {
        if (!java.util.Objects.equals(secondaryPreset, value)) {
            secondaryPreset = value;
            presetRevision++;
            samplingChanged();
        }
    }
    private boolean boneAnimationMuted;
    public boolean isBoneAnimationMuted() {return boneAnimationMuted;}
    public void setBoneAnimationMuted(boolean value) {boneAnimationMuted=value;syncBoneAnimation();samplingChanged();}
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
        syncBoneAnimation();
        samplingChanged();
    }
    /** Pure read; derived state is synchronized when the owner changes. */
    public EntityAnimationClip getBoneAnimation() { return boneAnimationId==null?null:boneAnimation; }
    void syncBoneAnimation() {
        if(boneAnimationId==null || boneAnimation==null) return;
        boneAnimation.setBlendSettings(blendSettings);
        boneAnimation.setTimeRange(getStartTime(),getEndTime());boneAnimation.setMuted(isMuted() || boneAnimationMuted);
        if(getTimelineClip()!=null && boneAnimation.getTimelineClip()!=null)
            boneAnimation.getTimelineClip().setParentTrack(getTimelineClip().getParentTrack());
    }
    private final java.util.List<EntityLookAtClip> lookTargets = new java.util.ArrayList<>();

    public java.util.List<EntityLookAtClip> getLookTargets() { return java.util.List.copyOf(lookTargets); }

    public EntityLookAtClip findLookTargetAt(float time) {
        EntityLookAtClip best = null;
        for (var target : lookTargets) best = ActorTrack.preferredLookTarget(best, target, time);
        return best;
    }

    public void addLookTarget(EntityLookAtClip target) {
        if (lookTargets.contains(target)) return;
        lookTargets.add(target);
        samplingChanged();
    }

    public void removeLookTarget(EntityLookAtClip target) {
        if (lookTargets.remove(target)) samplingChanged();
    }

    public void editLookTarget(EntityLookAtClip target, java.util.function.Consumer<EntityLookAtClip> edit) {
        if (!lookTargets.contains(target)) throw new IllegalStateException("Missing spawn look target");
        edit.accept(target);
        samplingChanged();
    }

    private final java.util.List<EntityStateClip> stateIntervals = new java.util.ArrayList<>();

    public java.util.List<EntityStateClip> getStateIntervals() { return java.util.List.copyOf(stateIntervals); }

    public void addStateInterval(EntityStateClip state) {
        if (stateIntervals.contains(state)) return;
        stateIntervals.add(state);
        samplingChanged();
    }

    public void removeStateInterval(EntityStateClip state) {
        if (stateIntervals.remove(state)) samplingChanged();
    }

    public void editStateInterval(EntityStateClip state, java.util.function.Consumer<EntityClip> edit) {
        if (!stateIntervals.contains(state)) throw new IllegalStateException("Missing spawn state interval");
        edit.accept(state);
        samplingChanged();
    }

    private final java.util.List<ActorActionClip> actionIntervals = new java.util.ArrayList<>();

    public java.util.List<ActorActionClip> getActionIntervals() { return java.util.List.copyOf(actionIntervals); }

    public void replaceActionIntervals(java.util.List<ActorActionClip> expected, java.util.List<ActorActionClip> next) {
        if (!actionIntervals.equals(expected)) throw new IllegalStateException("Actor action intervals changed");
        actionIntervals.clear();
        actionIntervals.addAll(next);
        samplingChanged();
    }

    public void addActionInterval(ActorActionClip action) {
        if (actionIntervals.contains(action)) return;
        actionIntervals.add(action);
        samplingChanged();
    }

    public void removeActionInterval(ActorActionClip action) {
        if (actionIntervals.remove(action)) samplingChanged();
    }

    public void editActionInterval(ActorActionClip action, java.util.function.Consumer<EntityClip> edit) {
        if (!actionIntervals.contains(action)) throw new IllegalStateException("Missing spawn action interval");
        edit.accept(action);
        samplingChanged();
    }

    private final java.util.List<ActorPoseClip> poseIntervals = new java.util.ArrayList<>();

    public java.util.List<ActorPoseClip> getPoseIntervals() { return java.util.List.copyOf(poseIntervals); }

    public void addPoseInterval(ActorPoseClip pose) {
        if (poseIntervals.contains(pose)) return;
        poseIntervals.add(pose);
        samplingChanged();
    }

    public void removePoseInterval(ActorPoseClip pose) {
        if (poseIntervals.remove(pose)) samplingChanged();
    }

    public void editPoseInterval(ActorPoseClip pose, java.util.function.Consumer<? super ActorPoseClip> edit) {
        if (!poseIntervals.contains(pose)) throw new IllegalStateException("Missing spawn pose interval");
        edit.accept(pose);
        samplingChanged();
    }

    @Override public void setTimeRange(float start,float end) {
        float delta = start - getStartTime();
        boolean translated = Math.abs((end - start) - getLength()) < 1e-4f;
        super.setTimeRange(start,end);
        if (translated && delta != 0f) {
            for (var target : lookTargets) target.setTimeRange(target.getStartTime() + delta, target.getEndTime() + delta);
            for (var state : stateIntervals) state.setTimeRange(state.getStartTime() + delta, state.getEndTime() + delta);
            for (var action : actionIntervals) action.setTimeRange(action.getStartTime() + delta, action.getEndTime() + delta);
            for (var pose : poseIntervals) pose.setTimeRange(pose.getStartTime() + delta, pose.getEndTime() + delta);
        }
        syncBoneAnimation();
    }
    @Override public void setMuted(boolean value) { super.setMuted(value);syncBoneAnimation(); }
    @Override public void setTimelineClip(EntityTimelineClip clip) { super.setTimelineClip(clip);syncBoneAnimation(); }

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
        this(java.util.UUID.randomUUID(), name, startTime, endTime);
    }

    public EntitySpawnClip(java.util.UUID id, String name, float startTime, float endTime) {
        super(id, name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.SPAWN;
    }

    // ========== Getters and Setters ==========

    /** Defensive copy for UI / commands. Prefer {@link #positionView()} on hot sample paths. */
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    /** Live view of stored position; callers must not mutate. */
    public Vector3f positionView() {
        return position;
    }

    private transient boolean invalidPosition;
    private transient boolean invalidYaw;
    private transient boolean invalidPitch;
    private transient boolean invalidRoll;
    public boolean hasInvalidTransform() { return invalidPosition || invalidYaw || invalidPitch || invalidRoll; }
    /**
     * Rejects non-finite input without mutating stored values; sets {@link #hasInvalidTransform()}.
     * Draft commit paths must check the flag — setters do not throw.
     */
    public void setPosition(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) { invalidPosition = true; return; }
        invalidPosition = false;
        this.position.set(x, y, z);
        samplingChanged();
    }

    public float getYawDeg() {
        return yawDeg;
    }

    public void setYawDeg(float yawDeg) {
        if (!Float.isFinite(yawDeg)) { invalidYaw = true; return; }
        invalidYaw = false;
        this.yawDeg = yawDeg;
        samplingChanged();
    }

    public float getPitchDeg() {
        return pitchDeg;
    }

    public void setPitchDeg(float pitchDeg) {
        if (!Float.isFinite(pitchDeg)) { invalidPitch = true; return; }
        invalidPitch = false;
        this.pitchDeg = pitchDeg;
        samplingChanged();
    }

    private float rollDeg;
    private final Vector3f scale = new Vector3f(1);
    public float getRollDeg() { return rollDeg; }
    public void setRollDeg(float value) {
        if (!Float.isFinite(value)) { invalidRoll = true; return; }
        invalidRoll = false; rollDeg = value; samplingChanged();
    }
    /** Defensive copy for UI / commands. Prefer {@link #scaleView()} on hot sample paths. */
    public Vector3f getScale() { return new Vector3f(scale); }

    /** Live view of stored scale; callers must not mutate. */
    public Vector3f scaleView() { return scale; }

    public void setScale(float x, float y, float z) {
        if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && x > 0 && y > 0 && z > 0) {
            scale.set(x, y, z);
            samplingChanged();
        }
    }

    public boolean isVisibleOnSpawn() {
        return visibleOnSpawn;
    }

    public void setVisibleOnSpawn(boolean visibleOnSpawn) {
        this.visibleOnSpawn = visibleOnSpawn;
        samplingChanged();
    }
}

