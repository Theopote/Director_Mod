package com.director.timeline.model.entity;

import java.util.UUID;

/**
 * ActorRelationClip
 *
 * <p>定义演员和目标演员之间的相对关系（对话、跟随、编队偏移）。</p>
 */
public class ActorRelationClip extends EntityClip {

    public enum RelationType {
        FACE_TO_FACE,
        FOLLOW,
        CIRCLE_AROUND,
        MIRROR,
        PASS_OBJECT,
        RELATIVE_POSITION
    }

    private UUID targetActorId;
    private RelationType relationType = RelationType.FACE_TO_FACE;
    private float distance = 2.0f;
    private float angle = 0f;
    private float heightOffset = 0f;
    private boolean maintainLookAt = true;

    public ActorRelationClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ACTOR_RELATION;
    }

    @Override
    public TimelineClipVisualType getVisualType() {
        return TimelineClipVisualType.BLOCK;
    }

    public UUID getTargetActorId() {
        return targetActorId;
    }

    public void setTargetActorId(UUID targetActorId) {
        this.targetActorId = targetActorId;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType != null ? relationType : RelationType.FACE_TO_FACE;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = Math.max(0.1f, distance);
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public float getHeightOffset() {
        return heightOffset;
    }

    public void setHeightOffset(float heightOffset) {
        this.heightOffset = heightOffset;
    }

    public boolean isMaintainLookAt() {
        return maintainLookAt;
    }

    public void setMaintainLookAt(boolean maintainLookAt) {
        this.maintainLookAt = maintainLookAt;
    }
}
