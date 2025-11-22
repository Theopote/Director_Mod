package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

import java.util.List;

/**
 * 删除轨道命令（支持撤销）。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 * 修复类型安全问题：使用 TimelineTrack 而非 Object。
 */
public class DeleteTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteTrackCommand.class);

    private final TimelineTrack track;
    private final List<TimelineTrack> trackList;
    private int removedIndex = -1;

    public DeleteTrackCommand(TimelineModel model, TimelineTrack track, List<TimelineTrack> trackList) {
        super(model, "track_deleted");
        this.track = track;
        this.trackList = trackList;
    }

    @Override
    protected void doExecute() {
        if (track == null || trackList == null) {
            LOGGER.warn("[Timeline] 删除轨道失败：track 或 trackList 为 null");
            return;
        }
        removedIndex = trackList.indexOf(track);
        if (removedIndex >= 0) {
            // 清理轨道中所有 clips 的插值映射，避免内存泄漏
            for (com.chronoblocks.ui.timeline.model.TimelineClip clip : track.getClips()) {
                ChangeKeyframeInterpolationCommand.clearInterpolationsForClip(clip);
            }
            trackList.remove(removedIndex);
            LOGGER.info("[Timeline] 删除轨道，索引: {}", removedIndex);
        } else {
            LOGGER.warn("[Timeline] 删除轨道失败：轨道不在列表中");
        }
    }

    @Override
    protected void doUndo() {
        if (track == null || trackList == null) {
            LOGGER.warn("[Timeline] 恢复轨道失败：track 或 trackList 为 null");
            return;
        }
        if (removedIndex >= 0 && removedIndex <= trackList.size()) {
            trackList.add(removedIndex, track);
            LOGGER.info("[Timeline] 恢复轨道到索引: {}", removedIndex);
        }
    }

    @Override
    public String getDescription() {
        return track != null ? "删除轨道: " + track.getName() : "删除轨道";
    }

    @Override
    public boolean canUndo() {
        return removedIndex >= 0;
    }
}


