package com.director.ui.timeline.interaction;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.ui.timeline.interaction.TimelineDragHandles.ActionPaintHandle;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.ActorActionClip;
import com.director.ui.timeline.model.entity.ActorTrack;
import com.director.ui.timeline.render.TimelineRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor Action 涂抹（画笔拖区间）逻辑。
 */
public final class TimelineActionPaintHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineActionPaintHandler.class);

    public interface DataChangeNotifier {
        void onDataChanged();
    }

    private final TimelineInteractionManager interactionManager;
    private final TimelineRenderer renderer;
    private final DataChangeNotifier dataChangeNotifier;

    public TimelineActionPaintHandler(
            TimelineInteractionManager interactionManager,
            TimelineRenderer renderer,
            DataChangeNotifier dataChangeNotifier) {
        this.interactionManager = interactionManager;
        this.renderer = renderer;
        this.dataChangeNotifier = dataChangeNotifier;
    }

    public boolean canStartActionPaint(TimelineTrack track) {
        if (track == null || track.getType() != TrackType.ACTOR) {
            return false;
        }

        ActorTrack actorTrack = track.getActorTrackPayload();
        if (actorTrack == null) {
            return false;
        }

        ActorActionType brushAction = ActionBrushState.get();
        if (brushAction == null) {
            return false;
        }

        var actorRef = actorTrack.getActorRef();
        if (actorRef == null) {
            return true;
        }

        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        var actorOpt = actorRegistry.getActor(actorRef.getActorId());
        if (actorOpt.isEmpty()) {
            return true;
        }

        Actor actor = actorOpt.get();
        ActorRigProfile rig = actor.getRigProfile();
        if (rig == null) {
            return true;
        }

        return rig.supports(brushAction);
    }

    public void startActionPaint(TimelineHitResult hit, float mouseX, float contentX) {
        TimelineTrack track = hit.track();
        if (track == null || track.getType() != TrackType.ACTOR) {
            return;
        }

        ActorTrack actorTrack = track.getActorTrackPayload();
        if (actorTrack == null) {
            actorTrack = track.getOrCreateActorTrackPayload();
        }

        ActorActionType actionType = ActionBrushState.get();
        if (actionType == null) {
            return;
        }

        float startTime = Math.max(0f, renderer.xToTime(mouseX, contentX));
        ActionPaintHandle handle = new ActionPaintHandle(track, actorTrack, actionType, startTime);

        interactionManager.startInteraction(
                InteractionMode.PAINT_ACTION,
                handle,
                mouseX, imgui.ImGui.getMousePosY(),
                startTime, startTime);

        LOGGER.debug("[Timeline] 开始 Action 涂抹: Action={}, StartTime={:.2f}s", actionType, startTime);
    }

    public void paintAction(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ActionPaintHandle handle)) {
            return;
        }

        float endTime = Math.max(0f, renderer.xToTime(mouseX, contentX));
        if (endTime < handle.startTime) {
            endTime = handle.startTime;
        }
        if (endTime - handle.startTime < 0.1f) {
            endTime = handle.startTime + 0.1f;
        }

        handle.currentEndTime = endTime;
    }

    public void applyActionPaint(ActionPaintHandle handle) {
        ActorTrack actorTrack = handle.actorTrack;
        ActorActionType actionType = handle.actionType;
        float startTime = handle.startTime;
        float endTime = handle.currentEndTime;

        if (endTime <= startTime) {
            LOGGER.debug("[Timeline] Action 涂抹取消: 时间段过短 ({}s -> {}s)", startTime, endTime);
            return;
        }

        var existingClips = actorTrack.getActionClips();
        var newClips = ActionClipIntervalResolver.resolve(existingClips, startTime, endTime, actionType);
        actorTrack.replaceActionClips(newClips);

        LOGGER.info("[Timeline] Action 涂抹完成: Action={}, Time=[{:.2f}s, {:.2f}s], Clips={}",
                actionType, startTime, endTime, newClips.size());

        dataChangeNotifier.onDataChanged();
    }
}
