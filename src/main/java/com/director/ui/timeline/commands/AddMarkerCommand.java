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

    public AddMarkerCommand(TimelineModel model, TimelineMarker marker) {
        super(model, "marker_added");
        this.marker = marker;
    }

    @Override
    protected void doExecute() {
        if (marker == null) {
            LOGGER.warn("[Timeline] 添加标记失败：marker 为空");
            return;
        }
        model.addMarker(marker);
    }

    @Override
    protected void doUndo() {
        if (marker == null) {
            return;
        }
        model.removeMarker(marker.getId());
    }

    @Override
    public String getDescription() {
        return marker != null ? "添加标记: " + marker.getName() : "添加标记";
    }
}
