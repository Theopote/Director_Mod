package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 添加标记命令（添加到 TimelineModel，而不是轨道）。
 */
public class AddMarkerCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddMarkerCommand.class);

    private final TimelineMarker marker;
    private boolean added;

    public AddMarkerCommand(TimelineModel model, TimelineMarker marker) {
        super(model, "marker_added");
        this.marker = marker;
    }

    @Override
    protected void doExecute() {
        if (model == null || marker == null) {
            LOGGER.warn("[Timeline] 添加标记失败：model 或 marker 为空");
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (model.getMarkers().stream().anyMatch(m -> m == marker || m.getId().equals(marker.getId()))) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        model.addMarker(marker);
        added = true;
    }

    @Override
    protected void doUndo() {
        if (!added) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        model.removeMarker(marker.getId());
        added = false;
    }

    @Override
    public String getDescription() {
        return marker != null ? "添加标记: " + marker.getName() : "添加标记";
    }

    @Override
    public boolean canUndo() {
        return added;
    }
}
