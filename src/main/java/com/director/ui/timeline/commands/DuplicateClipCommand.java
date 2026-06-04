package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;

/**
 * 复制片段命令（支持撤销）。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 * 注意：TimelineClip 的拷贝构造函数已经实现了深拷贝（包括 keyframes），
 * 因此无需额外处理。
 */
public class DuplicateClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateClipCommand.class);
    private static final float DUPLICATE_OFFSET = 0.5f;

    private final TimelineTrack track;
    private final TimelineClip originalClip;
    private TimelineClip duplicatedClip;

    public DuplicateClipCommand(TimelineModel model, TimelineTrack track, TimelineClip clip) {
        super(model, "clip_duplicated");
        this.track = track;
        this.originalClip = clip;
    }

    @Override
    protected void doExecute() {
        if (track == null || originalClip == null) {
            LOGGER.warn("[Timeline] 复制片段失败：track 或 originalClip 为 null");
            return;
        }
        if (originalClip.getBoundContainerId() != null
            && !(originalClip instanceof com.director.ui.timeline.model.audio.AudioTimelineClip)) {
            LOGGER.warn("[Timeline] 容器片段不支持直接复制，请从容器列表重新拖入或移动现有片段: {}",
                originalClip.getLabel());
            return;
        }

        // 创建副本（拷贝构造函数已实现深拷贝，包括 keyframes）
        duplicatedClip = new TimelineClip(originalClip);
        duplicatedClip.setLabel(originalClip.getLabel() + " (副本)");

        // 偏移时间以避免重叠
        float offset = originalClip.getLength() + DUPLICATE_OFFSET;
        duplicatedClip.offsetTime(offset);

        // 复制关键帧的插值信息
        for (float keyframeTime : originalClip.getKeyframes()) {
            com.director.ui.timeline.model.TimelineInterpolation interpolation = 
                ChangeKeyframeInterpolationCommand.getKeyframeInterpolation(originalClip, keyframeTime);
            if (interpolation != null && interpolation != com.director.ui.timeline.model.TimelineInterpolation.LINEAR) {
                // 计算新位置的关键帧时间
                float newKeyframeTime = keyframeTime + offset;
                ChangeKeyframeInterpolationCommand.setKeyframeInterpolationDirectly(
                    duplicatedClip, newKeyframeTime, interpolation);
            }
        }

        // 添加到轨道
        if (!track.addClip(duplicatedClip)) {
            duplicatedClip = null;
            LOGGER.warn("[Timeline] 复制片段失败：轨道拒绝接收 {}", originalClip.getLabel());
            return;
        }

        LOGGER.debug("[Timeline] 复制片段: {} -> {}", originalClip.getLabel(), duplicatedClip.getLabel());
    }

    @Override
    protected void doUndo() {
        if (duplicatedClip != null && track != null) {
            track.removeClip(duplicatedClip);
            duplicatedClip = null;
            LOGGER.debug("[Timeline] 撤销复制片段");
        }
    }

    @Override
    public String getDescription() {
        return originalClip != null ? "复制片段: " + originalClip.getLabel() : "复制片段";
    }

    @Override
    public boolean canUndo() {
        return duplicatedClip != null;
    }

    /**
     * 获取复制的片段（用于选中）
     */
    public TimelineClip getDuplicatedClip() {
        return duplicatedClip;
    }
}

