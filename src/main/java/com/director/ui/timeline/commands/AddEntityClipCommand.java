package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds an entity clip and its timeline wrapper as one undoable operation.
 */
public class AddEntityClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddEntityClipCommand.class);

    private final TimelineTrack track;
    private final EntityClip entityClip;
    private ActorTrack actorTrack;
    private EntityTimelineClip timelineClip;
    private boolean added;

    public AddEntityClipCommand(TimelineModel model, TimelineTrack track, EntityClip entityClip) {
        super(model, "entity_clip_added");
        this.track = track;
        this.entityClip = entityClip;
    }

    @Override
    protected void doExecute() {
        if (track == null || entityClip == null) {
            LOGGER.warn("[Timeline] 添加实体片段失败：track 或 entityClip 为 null");
            return;
        }

        actorTrack = track.getOrCreateActorTrackPayload();
        if (timelineClip == null) {
            timelineClip = new EntityTimelineClip(entityClip, actorTrack.getColorRgba());
        }

        float length = Math.max(0.05f, entityClip.getEndTime() - entityClip.getStartTime());
        float resolvedStart = track.resolveClipStartTime(entityClip.getStartTime(), length, null);
        entityClip.setTimeRange(resolvedStart, resolvedStart + length);
        timelineClip.setTimeRange(resolvedStart, resolvedStart + length);

        if (track.addClip(timelineClip)) {
            actorTrack.addClip(entityClip);
            added = true;
            LOGGER.debug("[Timeline] 添加实体片段: {}", entityClip.getName());
        } else {
            LOGGER.warn("[Timeline] 添加实体片段失败：片段已存在或轨道拒绝添加: {}", entityClip.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (!added || track == null || entityClip == null || timelineClip == null) {
            return;
        }

        track.removeClip(timelineClip);
        if (actorTrack != null) {
            actorTrack.removeClip(entityClip.getId());
        }
        added = false;
        LOGGER.debug("[Timeline] 撤销添加实体片段: {}", entityClip.getName());
    }

    @Override
    public String getDescription() {
        return entityClip != null ? "添加实体片段: " + entityClip.getName() : "添加实体片段";
    }

    @Override
    public boolean canUndo() {
        return added;
    }
}
