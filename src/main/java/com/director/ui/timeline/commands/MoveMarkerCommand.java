package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 移动标记命令（支持撤销/重做）
 * 
 * <p>用于移动时间线上的标记（TimelineMarker）。
 * 标记是全局的，不属于任何轨道。
 * 
 * @author Director Team
 * @version 1.0
 */
public class MoveMarkerCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveMarkerCommand.class);

    private final UUID markerId;
    private final float oldTime;
    private final float newTime;
    private boolean moved;

    /**
     * 创建移动标记命令
     * 
     * @param model Timeline 模型
     * @param marker 要移动的标记
     * @param oldTime 原始时间
     * @param newTime 新时间
     */
    public MoveMarkerCommand(
            TimelineModel model,
            TimelineMarker marker,
            float oldTime,
            float newTime) {
        super(model, "marker_moved");
        this.markerId = marker != null ? marker.getId() : null;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        TimelineMarker marker = findMarker();
        if (model == null || markerId == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (marker == null || Float.compare(marker.getTime(), newTime) == 0) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        marker.setTime(newTime);
        moved = true;
        LOGGER.debug("[Command] MoveMarker execute {} -> {}", oldTime, newTime);
    }

    @Override
    protected void doUndo() {
        TimelineMarker marker = findMarker();
        if (marker == null) {
            throw new IllegalStateException("Timeline marker is missing: " + markerId);
        }
        marker.setTime(oldTime);
        moved = false;
        LOGGER.debug("[Command] MoveMarker undo {} <- {}", newTime, oldTime);
    }

    /**
     * 应用时间变化（Execute 和 Undo 共用）
     */
    private TimelineMarker findMarker() {
        if (model == null || markerId == null) {
            return null;
        }
        return model.getMarkers().stream()
                .filter(m -> m != null && markerId.equals(m.getId()))
                .findFirst().orElse(null);
    }

    @Override
    public String getDescription() {
        return String.format("移动标记: %.2fs -> %.2fs", oldTime, newTime);
    }

    @Override
    public boolean canUndo() {
        return moved;
    }
}
