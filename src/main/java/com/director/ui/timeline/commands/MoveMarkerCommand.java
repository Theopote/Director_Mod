package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineMarker;
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
        if (model == null || markerId == null) {
            LOGGER.warn("[Timeline] 移动标记失败：model 或 markerId 为 null");
            return;
        }
        apply(newTime);
        LOGGER.debug("[Command] MoveMarker execute {} -> {}", oldTime, newTime);
    }

    @Override
    protected void doUndo() {
        if (model == null || markerId == null) {
            LOGGER.warn("[Timeline] 撤销移动标记失败：model 或 markerId 为 null");
            return;
        }
        apply(oldTime);
        LOGGER.debug("[Command] MoveMarker undo {} <- {}", newTime, oldTime);
    }

    /**
     * 应用时间变化（Execute 和 Undo 共用）
     */
    private void apply(float time) {
        if (model == null || markerId == null) {
            return;
        }
        
        // 查找标记
        var markers = model.getMarkers();
        TimelineMarker marker = null;
        for (var m : markers) {
            if (m != null && m.getId().equals(markerId)) {
                marker = m;
                break;
            }
        }
        
        if (marker == null) {
            LOGGER.warn("[Timeline] 移动标记失败：找不到标记 {}", markerId);
            return;
        }
        
        // 更新标记时间
        marker.setTime(time);
    }

    @Override
    public String getDescription() {
        return String.format("移动标记: %.2fs -> %.2fs", oldTime, newTime);
    }
}
