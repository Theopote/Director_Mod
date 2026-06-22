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
    private TimelineMarker markerSnapshot; // 用于 Undo

    public DeleteMarkerCommand(TimelineModel model, TimelineMarker marker) {
        super(model, "marker_deleted");
        this.markerId = marker != null ? marker.getId() : null;
        // 保存标记快照用于撤销
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
            return;
        }
        // 在删除前保存完整的标记信息（如果还没有保存）
        if (markerSnapshot == null) {
            var markers = model.getMarkers();
            for (var m : markers) {
                if (m != null && m.getId().equals(markerId)) {
                    markerSnapshot = new TimelineMarker(m.getId(), m.getName(), m.getTime());
                    markerSnapshot.setColor(m.getColor());
                    markerSnapshot.setNote(m.getNote());
                    break;
                }
            }
        }
        model.removeMarker(markerId);
        LOGGER.debug("[Timeline] 删除标记: {}", markerId);
    }

    @Override
    protected void doUndo() {
        if (model == null || markerSnapshot == null) {
            LOGGER.warn("[Timeline] 恢复标记失败：model 或 markerSnapshot 为 null");
            return;
        }
        TimelineMarker restoredMarker = new TimelineMarker(markerSnapshot.getId(), markerSnapshot.getName(), markerSnapshot.getTime());
        restoredMarker.setColor(markerSnapshot.getColor());
        restoredMarker.setNote(markerSnapshot.getNote());
        model.addMarker(restoredMarker);
        LOGGER.debug("[Timeline] 恢复标记: {}", restoredMarker.getId());
    }

    @Override
    public String getDescription() {
        return markerSnapshot != null ? "删除标记: " + markerSnapshot.getName() : "删除标记";
    }
}
