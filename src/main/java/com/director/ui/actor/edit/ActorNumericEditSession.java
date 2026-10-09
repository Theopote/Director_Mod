package com.director.ui.actor.edit;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.actor.gizmo.ActorTransformEditSession;
import com.director.ui.actor.lookat.ActorLookAtEditSession;
import org.joml.Vector3f;
import java.util.UUID;

/** One inspector gesture, bound to the actor/track/playhead at activation. */
public final class ActorNumericEditSession {
    public enum Kind { TRANSFORM, SCALE, LOOK_AT }
    private final Kind kind;
    private final ActorTransformEditSession transform;
    private final ActorLookAtEditSession look;
    private final ActorTransformEditService.TransformSnapshot initial;
    private final ActorTransformEditService.TransformSnapshot baselineFrom, baselineTo;
    private final EntityLookAtClip.TargetMode initialMode;
    private final Vector3f initialPoint;
    private final UUID initialTarget;
    private final float initialStrength;
    private boolean finished;
    private boolean explicitTarget;
    private boolean positionChanged, rotationChanged, scaleChanged;

    public ActorNumericEditSession(TimelineModel model, TimelineTrack track, Kind kind,
                                  ActorTransformEditService.TransformSnapshot sample) {
        this.kind = kind;
        if (kind != Kind.LOOK_AT) {
            initial = new ActorTransformEditService.TransformSnapshot(new Vector3f(sample.position()),
                    new Vector3f(sample.rotation()), new Vector3f(sample.scale()));
            var source = kind == Kind.SCALE ? null : ActorTransformEditSession.resolveSource(model, track);
            transform = new ActorTransformEditSession(model, track, source, kind == Kind.SCALE); look = null;
            var draft = transform.draft();
            if (source == null) {
                draft.setFromPos(initial.position().x, initial.position().y, initial.position().z);
                draft.setFromRot(initial.rotation().x, initial.rotation().y, initial.rotation().z);
                draft.setFromScale(initial.scale().x, initial.scale().y, initial.scale().z);
            } else if (model.getCurrentTime() < draft.getStartTime() || model.getCurrentTime() > draft.getEndTime()) {
                draft.setTimeRange(Math.min(draft.getStartTime(), model.getCurrentTime()),
                        Math.max(draft.getEndTime(), model.getCurrentTime() + 1));
            }
            if (source == null || !source.getKeyframes().isEmpty()) {
                draft.setToPos(initial.position().x, initial.position().y, initial.position().z);
                draft.setToRot(initial.rotation().x, initial.rotation().y, initial.rotation().z);
                draft.setToScale(initial.scale().x, initial.scale().y, initial.scale().z);
            }
            baselineFrom = new ActorTransformEditService.TransformSnapshot(new Vector3f(draft.getFromPos()),
                    new Vector3f(draft.getFromRot()), new Vector3f(draft.getFromScale()));
            baselineTo = new ActorTransformEditService.TransformSnapshot(new Vector3f(draft.getToPos()),
                    new Vector3f(draft.getToRot()), new Vector3f(draft.getToScale()));
            transform.arm(); initialMode = null; initialPoint = null; initialTarget = null; initialStrength = 0;
        } else {
            transform = null; initial = null; baselineFrom = null; baselineTo = null;
            look = new ActorLookAtEditSession(model, track);
            initialMode = look.draft().getMode(); initialPoint = new Vector3f(look.draft().getWorldPoint());
            initialTarget = look.draft().getTargetEntityUuid(); initialStrength = look.draft().getStrength();
        }
    }
    public boolean isCurrent(TimelineModel model, TimelineTrack owner, UUID actor) {
        return !finished && (transform != null ? transform.model() == model && transform.owner() == owner
                && transform.actorId().equals(actor) && transform.isCurrent()
                : look.model() == model && look.owner() == owner && look.actorId().equals(actor) && look.isCurrent());
    }
    public Kind kind() { return kind; }
    public void updateTransform(Vector3f position, Vector3f rotation, Vector3f scale) {
        if (finished || transform == null || !position.isFinite() || !rotation.isFinite() || !scale.isFinite())
            throw new IllegalArgumentException("变换数值无效，请输入有限数值");
        var draft = transform.draft();
        var boundedScale = new Vector3f(clamp(scale.x), clamp(scale.y), clamp(scale.z));
        positionChanged = !position.equals(initial.position()); rotationChanged = !rotation.equals(initial.rotation());
        scaleChanged = !boundedScale.equals(initial.scale());
        var nextPosition = positionChanged ? position : baselineTo.position();
        var nextRotation = rotationChanged ? rotation : baselineTo.rotation();
        var nextScale = scaleChanged ? boundedScale : baselineTo.scale();
        draft.setToPos(nextPosition.x, nextPosition.y, nextPosition.z);
        draft.setToRot(nextRotation.x, nextRotation.y, nextRotation.z);
        draft.setToScale(nextScale.x, nextScale.y, nextScale.z);
        if (transform.model().getCurrentTime() <= draft.getStartTime() + 1e-4f) {
            var fromPosition = positionChanged ? position : baselineFrom.position();
            var fromRotation = rotationChanged ? rotation : baselineFrom.rotation();
            var fromScale = scaleChanged ? boundedScale : baselineFrom.scale();
            draft.setFromPos(fromPosition.x, fromPosition.y, fromPosition.z);
            draft.setFromRot(fromRotation.x, fromRotation.y, fromRotation.z);
            draft.setFromScale(fromScale.x, fromScale.y, fromScale.z);
        }
    }
    public void updateStrength(float strength) {
        if (finished || look == null || !Float.isFinite(strength)) throw new IllegalArgumentException("注视强度无效");
        look.draft().setStrength(strength);
    }
    public void updateLookTarget(EntityLookAtClip.TargetMode mode, Vector3f point) {
        if (finished || look == null || mode == null || point == null || !point.isFinite())
            throw new IllegalArgumentException("注视目标无效");
        if (mode == EntityLookAtClip.TargetMode.ENTITY_UUID && look.draft().getTargetEntityUuid() == null)
            throw new IllegalArgumentException("请先在注视片段属性中指定目标实体");
        look.draft().setMode(mode); look.draft().setWorldPoint(point.x, point.y, point.z);
        if (mode != EntityLookAtClip.TargetMode.ENTITY_UUID) look.draft().setTargetEntityUuid(null);
        explicitTarget = true;
    }
    public void cancel() { finished = true; if (transform != null) transform.cancel(); else look.cancel(); }
    public void commit() {
        if (finished) return;
        if (transform != null) {
            if (!positionChanged && !rotationChanged && !scaleChanged) { cancel(); return; }
            transform.commit();
        } else {
            var draft = look.draft();
            if (!(explicitTarget && look.source() == null) && initialMode == draft.getMode() && initialPoint.equals(draft.getWorldPoint())
                    && java.util.Objects.equals(initialTarget, draft.getTargetEntityUuid())
                    && initialStrength == draft.getStrength()) { cancel(); return; }
            look.commit();
        }
        finished = true;
    }
    private static float clamp(float value) { return Math.max(.01f, Math.min(100, value)); }
}
