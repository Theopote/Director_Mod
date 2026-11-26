package com.director.ui.timeline.interaction;

import com.director.actor.ActorRegistryManager;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.interaction.TimelineDragHandles.ActionPaintHandle;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.ui.timeline.render.TimelineRenderer;

public final class TimelineActionPaintHandler {
    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    private final TimelineRenderer renderer;

    public TimelineActionPaintHandler(TimelineModel model, TimelineInteractionManager interactionManager,
                                      TimelineRenderer renderer) {
        this.model = model;
        this.interactionManager = interactionManager;
        this.renderer = renderer;
    }

    private static boolean editable(TimelineModel model, TimelineTrack track, ActorTrack payload,
                                    EntitySpawnClip spawn, ActorActionType action, float anchor) {
        if (track == null || payload == null || spawn == null || action == null
                || !Float.isFinite(anchor) || !model.getAllTracks().contains(track)
                || model.isPlaying() || model.isScrubbing() || track.getType() != TrackType.ACTOR
                || track.isLocked() || track.isMuted() || track.getActorTrackPayload() != payload
                || payload.getActorRef() == null || payload.findSpawnClipAt(anchor) != spawn
                || !payload.isPresentAt(anchor) || spawn.getTimelineClip() == null
                || !track.getClips().contains(spawn.getTimelineClip())) return false;
        return ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId())
                .map(actor -> com.director.actor.ActorBehaviorCapabilities.supportsAction(actor, action)).orElse(false);
    }

    public boolean startActionPaint(TimelineTrack track, float mouseX, float mouseY, float contentX) {
        if (track == null) return false;
        float anchor = renderer.xToTime(mouseX, contentX);
        var payload = track.getActorTrackPayload();
        var spawn = payload == null ? null : payload.findSpawnClipAt(anchor);
        var action = ActionBrushState.get();
        if (!editable(model, track, payload, spawn, action, anchor)) return false;
        var handle = new ActionPaintHandle(track, payload, spawn, action, anchor, model.getActorEditContextRevision());
        interactionManager.startInteraction(InteractionMode.PAINT_ACTION, handle, mouseX, mouseY, anchor, anchor);
        return true;
    }

    public void paintAction(float mouseX, float contentX) {
        if (!(interactionManager.getActiveObject() instanceof ActionPaintHandle handle)) return;
        if (handle.revision != model.getActorEditContextRevision()
                || !editable(model, handle.track, handle.actorTrack, handle.spawn, handle.actionType, handle.startTime)) {
            interactionManager.endInteraction();
            return;
        }
        float time = renderer.xToTime(mouseX, contentX);
        handle.currentEndTime = Math.clamp(time, handle.spawn.getStartTime(), handle.spawn.getEndTime());
    }

    public void applyActionPaint(ActionPaintHandle handle) {
        if (handle.revision == model.getActorEditContextRevision()
                && handle.track.getActorTrackPayload() == handle.actorTrack)
            paint(model, handle.track, handle.spawn, handle.actionType, handle.startTime, handle.currentEndTime);
    }

    static boolean paint(TimelineModel model, TimelineTrack track, EntitySpawnClip spawn,
                         ActorActionType action, float anchor, float end) {
        var payload = track.getActorTrackPayload();
        if (!editable(model, track, payload, spawn, action, anchor) || !Float.isFinite(end)) return false;
        float start = Math.max(spawn.getStartTime(), Math.min(anchor, end));
        float finish = Math.min(spawn.getEndTime(), Math.max(anchor, end));
        if (finish - start < .1f - 1e-5f || payload.getActionClips().stream().anyMatch(legacy ->
                !legacy.isMuted() && legacy.getEndTime() > start && legacy.getStartTime() < finish)) return false;

        var before = spawn.getActionIntervals();
        var after = ActionClipIntervalResolver.resolve(before, start, finish, action);
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paint actor action interval",
                () -> spawn.replaceActionIntervals(after, before), () -> spawn.replaceActionIntervals(before, after)));
        return true;
    }

}
