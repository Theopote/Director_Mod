package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;

import java.util.List;

/**
 * 删除片段命令。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 * 修复类型安全问题：使用 TimelineClip 而非 Object。
 */
public class DeleteClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteClipCommand.class);

    private final TimelineClip clip;
    private final List<TimelineClip> clipList;
    private int removedIndex = -1;

    public DeleteClipCommand(TimelineModel model, TimelineClip clip, List<TimelineClip> clipList) {
        super(model, "clip_deleted");
        this.clip = clip;
        this.clipList = clipList;
    }

    @Override
    protected void doExecute() {
        if (clip == null || clipList == null) {
            LOGGER.warn("[Timeline] 删除片段失败：clip 或 clipList 为 null");
            return;
        }
        removedIndex = clipList.indexOf(clip);
        if (removedIndex >= 0) {
            clipList.remove(removedIndex);
            // 清理插值映射，避免内存泄漏
            ChangeKeyframeInterpolationCommand.clearInterpolationsForClip(clip);
            LOGGER.debug("[Timeline] 删除片段，索引: {}", removedIndex);
        } else {
            LOGGER.warn("[Timeline] 删除片段失败：片段不在列表中");
        }
    }

    @Override
    protected void doUndo() {
        if (clip == null || clipList == null) {
            LOGGER.warn("[Timeline] 恢复片段失败：clip 或 clipList 为 null");
            return;
        }
        if (removedIndex >= 0 && removedIndex <= clipList.size()) {
            clipList.add(removedIndex, clip);
            LOGGER.debug("[Timeline] 恢复片段到索引: {}", removedIndex);
        }
    }

    @Override
    public String getDescription() {
        return clip != null ? "删除片段: " + clip.getLabel() : "删除片段";
    }

    @Override
    public boolean canUndo() {
        return removedIndex >= 0;
    }
}


