package com.director.timeline.model.entity;

import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.clip.ClipKeyframe;
import org.joml.Vector3f;

/**
 * Clip-level keyframe for actor/entity timeline clips.
 *
 * <p>Stores transform + action state for an actor during its spawn clip.</p>
 */
public class EntityClipKeyframe extends ClipKeyframe {

    public static final int POSITION = 1, ROTATION = 2, SCALE = 4, ALL = 7;
    private int channels = ALL;
    public int getChannels() { return channels; }
    public void setChannels(int channels) { this.channels = channels & ALL; }
    public boolean hasChannel(int channel) { return (channels & channel) != 0; }

    private final Vector3f position = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private final Vector3f scale = new Vector3f(1f, 1f, 1f);

    private EntityKeyframe.ActionType actionType = EntityKeyframe.ActionType.IDLE;
    private float actionSpeed = 1.0f;
    private boolean actionLoop = true;
    private float visibility = 1.0f;
    private boolean active = true;

    private String displayName;
    private boolean showNameTag = false;

    private EntityKeyframe.EntityType entityType = EntityKeyframe.EntityType.CUSTOM;

    public EntityClipKeyframe(float time) {
        super(time);
    }

    public EntityClipKeyframe(float time, TimelineInterpolation interpolation) {
        super(time, interpolation);
    }

    @Override
    public String getType() {
        return "EntityClipKeyframe";
    }

    public Vector3f getPosition() {
        return position;
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(float yaw, float pitch, float roll) {
        this.rotation.set(yaw, pitch, roll);
    }

    public Vector3f getScale() {
        return scale;
    }

    public void setScale(float x, float y, float z) {
        this.scale.set(x, y, z);
    }

    public EntityKeyframe.ActionType getActionType() {
        return actionType;
    }

    public void setActionType(EntityKeyframe.ActionType actionType) {
        this.actionType = actionType != null ? actionType : EntityKeyframe.ActionType.IDLE;
    }

    public float getActionSpeed() {
        return actionSpeed;
    }

    public void setActionSpeed(float actionSpeed) {
        this.actionSpeed = Math.max(0.0f, Math.min(2.0f, actionSpeed));
    }

    public boolean isActionLoop() {
        return actionLoop;
    }

    public void setActionLoop(boolean actionLoop) {
        this.actionLoop = actionLoop;
    }

    public float getVisibility() {
        return visibility;
    }

    public void setVisibility(float visibility) {
        this.visibility = clamp01(visibility);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isShowNameTag() {
        return showNameTag;
    }

    public void setShowNameTag(boolean showNameTag) {
        this.showNameTag = showNameTag;
    }

    public EntityKeyframe.EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityKeyframe.EntityType entityType) {
        this.entityType = entityType != null ? entityType : EntityKeyframe.EntityType.CUSTOM;
    }

    @Override
    public EntityClipKeyframe copy() {
        EntityClipKeyframe copy = new EntityClipKeyframe(getTime(), getInterpolation());
        copy.channels = channels;
        copy.position.set(position);
        copy.rotation.set(rotation);
        copy.scale.set(scale);
        copy.actionType = actionType;
        copy.actionSpeed = actionSpeed;
        copy.actionLoop = actionLoop;
        copy.visibility = visibility;
        copy.active = active;
        copy.displayName = displayName;
        copy.showNameTag = showNameTag;
        copy.entityType = entityType;
        return copy;
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        return Math.min(v, 1.0f);
    }
}
