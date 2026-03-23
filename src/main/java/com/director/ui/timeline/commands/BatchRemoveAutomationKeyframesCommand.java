package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineSelectable;
import com.director.ui.timeline.model.TimelineTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 批量删除自动化关键帧（支持一次 Undo/Redo）
 *
 * <p>用于摄像机/天气/时间等轨道级关键帧的多选删除。</p>
 */
public class BatchRemoveAutomationKeyframesCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchRemoveAutomationKeyframesCommand.class);

    public record Entry(TimelineTrack track, TimelineSelectable keyframe) {}

    private final List<Entry> entries = new ArrayList<>();

    public BatchRemoveAutomationKeyframesCommand(TimelineModel model, List<Entry> entries) {
        super(model, "automation_keyframes_removed_batch");
        if (entries != null) {
            for (Entry e : entries) {
                if (e == null || e.track() == null || e.keyframe() == null) continue;
                this.entries.add(new Entry(Objects.requireNonNull(e.track()), Objects.requireNonNull(e.keyframe())));
            }
        }
    }

    @Override
    protected void doExecute() {
        for (Entry e : entries) {
            try {
                e.track().removeAutomationKeyframe(e.keyframe().getId());
            } catch (Exception ex) {
                LOGGER.warn("[Timeline] 批量删除自动化关键帧失败: {}", ex.getMessage());
            }
        }
    }

    @Override
    protected void doUndo() {
        // 还原：重新加入并排序（addAutomationKeyframe 内部会排序）
        for (Entry e : entries) {
            try {
                e.track().addAutomationKeyframe(e.keyframe());
            } catch (Exception ex) {
                LOGGER.warn("[Timeline] 撤销批量删除自动化关键帧失败: {}", ex.getMessage());
            }
        }
    }

    @Override
    public String getDescription() {
        return "删除自动化关键帧（批量）";
    }
}


