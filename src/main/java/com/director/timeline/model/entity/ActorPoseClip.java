package com.director.timeline.model.entity;

/**
 * Per-actor local pose override clip.
 *
 * <p>This is intentionally narrower than a full animation system: it stores
 * director-authored offsets for broad body regions, then the runtime applies
 * them after RigProfile has produced the semantic base pose.</p>
 */
public final class ActorPoseClip extends EntityClip {
    private boolean overrideBody = false;
    private boolean overrideHead = true;
    private boolean overrideArms = false;
    private boolean overrideLegs = false;

    private float bodyPitch = 0f;
    private float bodyYaw = 0f;
    private float bodyRoll = 0f;
    private float headPitch = 0f;
    private float headYaw = 0f;
    private float leftArmPitch = 0f;
    private float rightArmPitch = 0f;
    private float leftLegPitch = 0f;
    private float rightLegPitch = 0f;

    public ActorPoseClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ACTOR_POSE;
    }

    @Override
    public TimelineClipVisualType getVisualType() {
        return TimelineClipVisualType.BLOCK;
    }

    public boolean isOverrideBody() {
        return overrideBody;
    }

    public void setOverrideBody(boolean overrideBody) {
        this.overrideBody = overrideBody;
    }

    public boolean isOverrideHead() {
        return overrideHead;
    }

    public void setOverrideHead(boolean overrideHead) {
        this.overrideHead = overrideHead;
    }

    public boolean isOverrideArms() {
        return overrideArms;
    }

    public void setOverrideArms(boolean overrideArms) {
        this.overrideArms = overrideArms;
    }

    public boolean isOverrideLegs() {
        return overrideLegs;
    }

    public void setOverrideLegs(boolean overrideLegs) {
        this.overrideLegs = overrideLegs;
    }

    public float getBodyPitch() {
        return bodyPitch;
    }

    public void setBodyPitch(float bodyPitch) {
        this.bodyPitch = bodyPitch;
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public void setBodyYaw(float bodyYaw) {
        this.bodyYaw = bodyYaw;
    }

    public float getBodyRoll() {
        return bodyRoll;
    }

    public void setBodyRoll(float bodyRoll) {
        this.bodyRoll = bodyRoll;
    }

    public float getHeadPitch() {
        return headPitch;
    }

    public void setHeadPitch(float headPitch) {
        this.headPitch = headPitch;
    }

    public float getHeadYaw() {
        return headYaw;
    }

    public void setHeadYaw(float headYaw) {
        this.headYaw = headYaw;
    }

    public float getLeftArmPitch() {
        return leftArmPitch;
    }

    public void setLeftArmPitch(float leftArmPitch) {
        this.leftArmPitch = leftArmPitch;
    }

    public float getRightArmPitch() {
        return rightArmPitch;
    }

    public void setRightArmPitch(float rightArmPitch) {
        this.rightArmPitch = rightArmPitch;
    }

    public float getLeftLegPitch() {
        return leftLegPitch;
    }

    public void setLeftLegPitch(float leftLegPitch) {
        this.leftLegPitch = leftLegPitch;
    }

    public float getRightLegPitch() {
        return rightLegPitch;
    }

    public void setRightLegPitch(float rightLegPitch) {
        this.rightLegPitch = rightLegPitch;
    }
}
