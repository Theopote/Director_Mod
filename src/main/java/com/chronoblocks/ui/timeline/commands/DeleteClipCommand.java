package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.core.container.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntityClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    private Actor removedActor;
    private boolean removedActorFromRegistry;

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
        removedActor = null;
        removedActorFromRegistry = false;
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
            removeActorIfOrphaned();
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
            restoreActorIfNeeded();
            if (linkedEntityTrack != null && linkedEntityClip != null) {
                linkedEntityTrack.addClip(linkedEntityClip);
            }
            LOGGER.debug("[Timeline] 恢复片段到索引: {}", removedIndex);
        }
    }

    /**
     * 检查并删除孤立的演员
     * <p>如果删除片段后，该演员没有任何片段了，则从注册表中删除该演员
     */
    private void removeActorIfOrphaned() {
        // 只处理实体片段（EntityTimelineClip）
        if (!(clip instanceof EntityTimelineClip) || linkedEntityTrack == null || model == null) {
            return;
        }

        ActorRef actorRef = linkedEntityTrack.getActorRef();
        if (actorRef == null) {
            return;
        }

        UUID actorId = actorRef.getActorId();
        if (actorId == null) {
            return;
        }

        // 检查该演员是否还有任何片段（不限于 SpawnClip）
        if (hasAnyClip(model, actorId)) {
            return;
        }

        // 如果没有片段了，删除该演员
        ActorRegistry registry = ActorRegistryManager.getInstance();
        registry.getActor(actorId).ifPresent(actor -> {
            removedActor = actor;
            removedActorFromRegistry = registry.removeActor(actorId);
            LOGGER.info("[DeleteClipCommand] 演员 {} 的所有片段已删除，已从注册表中移除", actor.getName());
        });
    }

    private void restoreActorIfNeeded() {
        if (!removedActorFromRegistry || removedActor == null) {
            return;
        }
        ActorRegistry registry = ActorRegistryManager.getInstance();
        registry.registerActor(removedActor);
    }

    /**
     * 检查指定演员是否还有任何片段
     * 
     * @param model TimelineModel
     * @param actorId 演员ID
     * @return 如果还有片段则返回 true
     */
    private boolean hasAnyClip(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return false;
        }

        // 遍历所有轨道，查找该演员的轨道
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ENTITY) {
                continue;
            }
            EntityTrack entityTrack = track.getEntityTrackPayload();
            if (entityTrack == null) {
                continue;
            }
            ActorRef ref = entityTrack.getActorRef();
            // 找到该演员的轨道，检查是否还有片段
            if (ref != null && actorId.equals(ref.getActorId())) {
                if (entityTrack.hasClips()) {
                    return true;
                }
            }
        }

        return false;
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


