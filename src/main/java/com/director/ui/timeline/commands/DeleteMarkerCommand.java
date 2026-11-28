package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 删除标记命令（可撤销）。
 */
public class DeleteMarkerCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteMarkerCommand.class);

    private final UUID markerId;
    private TimelineMarker markerSnapshot;
    private boolean removed;

    public DeleteMarkerCommand(TimelineModel model, TimelineMarker marker) {
        super(model, "marker_deleted");
        this.markerId = marker != null ? marker.getId() : null;
        if (marker != null) {
            this.markerSnapshot = new TimelineMarker(marker.getId(), marker.getName(), marker.getTime());
            this.markerSnapshot.setColor(marker.getColor());
            this.markerSnapshot.setNote(marker.getNote());
        }
    }

    @Override
    protected void doExecute() {
        if (model == null || markerId == null) {
            LOGGER.warn("[Timeline] 删除标记失败：model 或 markerId 为 null");
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        TimelineMarker existing = model.getMarkers().stream()
                .filter(m -> m != null && markerId.equals(m.getId()))
                .findFirst().orElse(null);
        if (existing == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        markerSnapshot = new TimelineMarker(existing.getId(), existing.getName(), existing.getTime());
        markerSnapshot.setColor(existing.getColor());
        markerSnapshot.setNote(existing.getNote());
        model.removeMarker(markerId);
        removed = true;
        LOGGER.debug("[Timeline] 删除标记: {}", markerId);
    }

    @Override
    protected void doUndo() {
        if (!removed || model == null || markerSnapshot == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        if (model.getMarkers().stream().anyMatch(m -> markerId.equals(m.getId()))) {
            throw new IllegalStateException("Marker ID conflict: " + markerId);
        }
        TimelineMarker restoredMarker = new TimelineMarker(markerSnapshot.getId(), markerSnapshot.getName(), markerSnapshot.getTime());
        restoredMarker.setColor(markerSnapshot.getColor());
        restoredMarker.setNote(markerSnapshot.getNote());
        model.addMarker(restoredMarker);
        removed = false;
        LOGGER.debug("[Timeline] 恢复标记: {}", restoredMarker.getId());
    }

    @Override
    public String getDescription() {
        return markerSnapshot != null ? "删除标记: " + markerSnapshot.getName() : "删除标记";
    }

    @Override
    public boolean canUndo() {
        return removed;
    }
}
