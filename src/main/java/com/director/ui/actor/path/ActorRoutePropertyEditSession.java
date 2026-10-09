package com.director.ui.actor.path;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import java.util.UUID;

/** Detached inspector gesture. Playback duration and travel speed are independent. */
public final class ActorRoutePropertyEditSession {
    private record State(ActorPathEditing.Geometry geometry, float start, float end, float speed,
                         ActorRouteClip.LoopMode loop, boolean align, float jumpStart,
                         float jumpDuration, float jumpHeight, boolean smoothTravel) {
        static State capture(ActorRouteClip clip) {
            return new State(new ActorPathEditing.Geometry(clip.getWaypoints(), clip.getInterpolationMode()),
                    clip.getStartTime(), clip.getEndTime(), clip.getSpeed(), clip.getLoopMode(),
                    clip.isAlignRotationToRoute(), clip.getJumpStart(), clip.getJumpDuration(), clip.getJumpHeight(), clip.isSmoothTravel());
        }
        void validate() {
            if (!Float.isFinite(start) || start < 0 || !Float.isFinite(end) || end - start < .1f
                    || !Float.isFinite(speed) || speed < .05f || speed > 20 || loop == null
                    || !Float.isFinite(jumpStart) || jumpStart < 0
                    || !Float.isFinite(jumpDuration) || jumpDuration < .1f || jumpDuration > .8f
                    || !Float.isFinite(jumpHeight) || jumpHeight < 0 || jumpHeight > 1.25f)
                throw new IllegalArgumentException("路线属性无效，请检查时长、速度和跳跃范围");
        }
        void apply(ActorRouteClip clip) {
            if (!geometry.matches(clip.getWaypoints(), clip.getInterpolationMode())) geometry.apply(clip);
            clip.setTimeRange(start, end); clip.setSpeed(speed); clip.setLoopMode(loop);
            clip.setAlignRotationToRoute(align); clip.setJumpStart(jumpStart);
            clip.setJumpDuration(jumpDuration); clip.setJumpHeight(jumpHeight); clip.setSmoothTravel(smoothTravel);
        }
    }
    private final TimelineModel model;
    private final TimelineTrack owner;
    private final ActorTrack payload;
    private final UUID actorId;
    private final ActorRouteClip source, draft;
    private final ActorPathEditing.Target target;
    private final State before;
    private final long contextRevision;
    private boolean finished;

    public ActorRoutePropertyEditSession(TimelineModel model, TimelineTrack owner, ActorRouteClip source) {
        target = ActorPathEditing.route(model, owner, source);
        this.model = model; this.owner = owner; this.source = source;
        contextRevision = model.getActorEditContextRevision();
        payload = owner.getActorTrackPayload(); actorId = payload.getActorRef().getActorId();
        before = State.capture(source);
        draft = new ActorRouteClip(source.getName(), before.start, before.end); before.apply(draft);
    }
    public ActorRouteClip draft() { return draft; }
    public boolean isCurrent(TimelineModel currentModel, ActorRouteClip currentSource) {
        return !finished && contextRevision == model.getActorEditContextRevision() && model == currentModel && source == currentSource && target.isCurrent()
                && source.getTimelineClip().getParentTrack() == owner;
    }
    public void cancel() { finished = true; }
    public boolean commit() {
        if (!isCurrent(model, source)) { cancel(); return false; }
        State after = State.capture(draft); after.validate();
        if (before.equals(after)) { cancel(); return true; }
        ActorPathEditing.rejectOverlap(payload, source, after.start, after.end);
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Edit route properties",
                () -> apply(after, before), () -> apply(before, after)));
        finished = true;
        return true;
    }
    private void apply(State expected, State next) {
        ActorPathEditing.requireEditing(model);
        if (!ActorPathEditing.validOwner(model, owner, payload, actorId) || source.isMuted()
                || !payload.getClips().contains(source) || source.getTimelineClip() == null
                || source.getTimelineClip().getParentTrack() != owner
                || !owner.getClips().contains(source.getTimelineClip()) || !expected.equals(State.capture(source)))
            throw new IllegalArgumentException("路线目标已变化，请重新选择");
        next.validate(); ActorPathEditing.rejectOverlap(payload, source, next.start, next.end);
        next.apply(source);
    }
}
