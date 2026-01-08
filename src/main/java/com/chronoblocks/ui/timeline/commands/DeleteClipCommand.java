package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.core.container.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;

import java.util.List;
import java.util.Objects;

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
    private EntityClip linkedEntityClip;
    private EntityTrack linkedEntityTrack;

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
        linkedEntityClip = null;
        linkedEntityTrack = null;
        removedIndex = clipList.indexOf(clip);
        if (removedIndex >= 0) {
            // 记录容器ID（删除前）
            java.util.UUID containerId = clip.getBoundContainerId();
            
            // 移除片段关联的声音事件（在删除片段前）
            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onClipRemoved(clip);

            if (clip instanceof EntityTimelineClip entityTimelineClip) {
                linkedEntityClip = entityTimelineClip.getEntityClip();
                TimelineTrack parentTrack = clip.getParentTrack();
                linkedEntityTrack = parentTrack != null ? parentTrack.getEntityTrackPayload() : null;
                if (linkedEntityTrack != null) {
                    linkedEntityTrack.removeClip(linkedEntityClip.getId());
                }
            }

            clipList.remove(removedIndex);
            // 清理插值映射，避免内存泄漏
            ChangeKeyframeInterpolationCommand.clearInterpolationsForClip(clip);
            LOGGER.debug("[Timeline] 删除片段，索引: {}", removedIndex);
            
            // ★ 功能：删除片段后，如果这是该容器的最后一个片段，允许容器显示
            if (containerId != null) {
                var timelineService = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance();
                boolean hasOtherClips = timelineService.isContainerOnTimeline(containerId);
                
                if (!hasOtherClips) {
                    // 这是该容器的最后一个片段，允许容器显示
                    com.chronoblocks.core.container.Container container = 
                        Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
                    if (container != null && container.getState() == com.chronoblocks.core.container.ContainerState.HIDDEN) {
                        // 容器当前是隐藏状态，允许用户切换显示（但不在删除时自动显示）
                        LOGGER.info("[Timeline] 容器 {} 的所有片段已删除，现在可以切换显示状态", container.getName());
                    }
                }
            }
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
            if (linkedEntityTrack != null && linkedEntityClip != null) {
                linkedEntityTrack.addClip(linkedEntityClip);
            }
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


