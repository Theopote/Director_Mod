package com.director.timeline.model.entity;

import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.clip.ClipKeyframe;
import org.joml.Vector3f;
import java.util.Objects;

/**
 * Clip-level keyframe for actor/entity timeline clips.
 *
 * <p>Stores transform + action state for an actor during its spawn clip.</p>
 */
public class EntityClipKeyframe extends ClipKeyframe {

    public static final int POSITION = 1, ROTATION = 2, SCALE = 4, ALL = 7;
    private int channels = ALL;
    private Runnable samplingTouch = () -> {};
    void bindSamplingTouch(Runnable touch) { samplingTouch = touch == null ? () -> {} : touch; }
    private void touched() { samplingTouch.run(); }
    public int getChannels() { return channels; }
    public void setChannels(int channels) { this.channels = channels & ALL; touched(); }
    @Override public void setTime(float time) { super.setTime(time); touched(); }
    @Override public void setInterpolation(TimelineInterpolation interpolation) { super.setInterpolation(interpolation); touched(); }
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

    public EntityClipKeyframe(java.util.UUID id, float time) {
        super(id, time, TimelineInterpolation.LINEAR);
    }

    @Override
    public String getType() {
        return "EntityClipKeyframe";
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
        touched();
    }

    public Vector3f getRotation() {
        return new Vector3f(rotation);
    }

    public void setRotation(float yaw, float pitch, float roll) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) return;
        this.rotation.set(yaw, pitch, roll);
        touched();
    }

    public Vector3f getScale() {
        return new Vector3f(scale);
    }

    public void setScale(float x, float y, float z) {
        this.scale.set(x, y, z);
        touched();
    }

    public EntityKeyframe.ActionType getActionType() {
        return actionType;
    }

    public void setActionType(EntityKeyframe.ActionType actionType) {
        this.actionType = actionType != null ? actionType : EntityKeyframe.ActionType.IDLE;
        touched();
    }

    public float getActionSpeed() {
        return actionSpeed;
    }

    public void setActionSpeed(float actionSpeed) {
        this.actionSpeed = Math.max(0.0f, Math.min(2.0f, actionSpeed));
        touched();
    }

    public boolean isActionLoop() {
        return actionLoop;
    }

    public void setActionLoop(boolean actionLoop) {
        this.actionLoop = actionLoop;
        touched();
    }

    public float getVisibility() {
        return visibility;
    }

    public void setVisibility(float visibility) {
        this.visibility = clamp01(visibility);
        touched();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        touched();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        touched();
    }

    public boolean isShowNameTag() {
        return showNameTag;
    }

    public void setShowNameTag(boolean showNameTag) {
        this.showNameTag = showNameTag;
        touched();
    }

    public EntityKeyframe.EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityKeyframe.EntityType entityType) {
        this.entityType = entityType != null ? entityType : EntityKeyframe.EntityType.CUSTOM;
        touched();
    }

    @Override
    public EntityClipKeyframe copy() {
        EntityClipKeyframe copy = new EntityClipKeyframe(getTime(), getInterpolation());
        copy.copyStateFrom(this);
        return copy;
    }

    @Override
    public EntityClipKeyframe snapshot() {
        EntityClipKeyframe copy = new EntityClipKeyframe(getId(), getTime());
        copy.setInterpolation(getInterpolation());
        copy.copyStateFrom(this);
        return copy;
    }

    @Override
    public boolean hasSameStateAs(ClipKeyframe other) {
        if (!super.hasSameStateAs(other)) return false;
        EntityClipKeyframe that = (EntityClipKeyframe) other;
        return channels == that.channels
                && position.equals(that.position)
                && rotation.equals(that.rotation)
                && scale.equals(that.scale)
                && actionType == that.actionType
                && Float.compare(actionSpeed, that.actionSpeed) == 0
                && actionLoop == that.actionLoop
                && Float.compare(visibility, that.visibility) == 0
                && active == that.active
                && Objects.equals(displayName, that.displayName)
                && showNameTag == that.showNameTag
                && entityType == that.entityType;
    }

    private void copyStateFrom(EntityClipKeyframe source) {
        channels = source.channels;
        position.set(source.position);
        rotation.set(source.rotation);
        scale.set(source.scale);
        actionType = source.actionType;
        actionSpeed = source.actionSpeed;
        actionLoop = source.actionLoop;
        visibility = source.visibility;
        active = source.active;
        displayName = source.displayName;
        showNameTag = source.showNameTag;
        entityType = source.entityType;
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        return Math.min(v, 1.0f);
    }
}
