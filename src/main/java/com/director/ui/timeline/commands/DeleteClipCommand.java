package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.preview.ActorPreviewManager;
import com.director.core.container.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.EntityClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.ActorTrack;

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
    private TimelineTrack ownerTrack;
    private EntityClip linkedEntityClip;
    private ActorTrack linkedActorTrack;
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
        linkedActorTrack = null;
        removedActor = null;
        removedActorFromRegistry = false;
        ownerTrack = clip.getParentTrack();
        if (ownerTrack == null && model != null) {
            ownerTrack = model.findTrackForClip(clip);
        }
        removedIndex = clipList.indexOf(clip);
        if (removedIndex >= 0) {
            // 记录容器ID（删除前）
            java.util.UUID containerId = clip.getBoundContainerId();
            
            // 移除片段关联的声音事件（在删除片段前）
            com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onClipRemoved(clip);

            if (clip instanceof EntityTimelineClip entityTimelineClip) {
                linkedEntityClip = entityTimelineClip.getEntityClip();
                TimelineTrack parentTrack = clip.getParentTrack();
                linkedActorTrack = parentTrack != null ? parentTrack.getActorTrackPayload() : null;
                if (linkedActorTrack != null) {
                    linkedActorTrack.removeClip(linkedEntityClip.getId());
                }
            }

            if (ownerTrack != null) {
                ownerTrack.removeClip(clip);
            } else {
                clipList.remove(removedIndex);
            }
            removeActorIfOrphaned();
            // 清理插值映射，避免内存泄漏
            ChangeKeyframeInterpolationCommand.clearInterpolationsForClip(clip);
            LOGGER.debug("[Timeline] 删除片段，索引: {}", removedIndex);
            
            // ★ 功能：删除片段后，如果这是该容器的最后一个片段，允许容器显示
            if (containerId != null) {
                var timelineService = DirectorServices.get().containerTimeline();
                boolean hasOtherClips = timelineService.isContainerOnTimeline(containerId);
                
                if (!hasOtherClips) {
                    // 这是该容器的最后一个片段，允许容器显示
                    com.director.core.container.Container container = 
                        DirectorServices.get().containers().getContainer(containerId);
                    if (container != null && container.getState() == com.director.core.container.ContainerState.HIDDEN) {
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
            if (ownerTrack != null) {
                ownerTrack.addClip(clip);
            } else {
                clipList.add(removedIndex, clip);
            }
            restoreActorIfNeeded();
            if (linkedActorTrack != null && linkedEntityClip != null) {
                linkedActorTrack.addClip(linkedEntityClip);
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
        if (!(clip instanceof EntityTimelineClip) || linkedActorTrack == null || model == null) {
            return;
        }

        ActorRef actorRef = linkedActorTrack.getActorRef();
        if (actorRef == null) {
            return;
        }

        UUID actorId = actorRef.getActorId();

        // 检查该演员是否还有任何片段（不限于 SpawnClip）
        if (hasAnyClip(model, actorId)) {
            return;
        }

        // 如果没有片段了，删除该演员
        ActorRegistry registry = ActorRegistryManager.getInstance();
        registry.getActor(actorId).ifPresent(actor -> {
            removedActor = actor;
            removedActorFromRegistry = registry.removeActor(actorId);
            
            // 清理 Minecraft 世界中的预览实体
            ActorPreviewManager.getInstance().removePreview(actorId);
            
            // 如果删除的是当前选中的演员，清除选择（包括方块选区）
            com.director.ui.actor.selection.ActorSelectionHub.clearSelection();
            
            LOGGER.info("[DeleteClipCommand] 演员 {} 的所有片段已删除，已从注册表中移除，预览实体和选区已清理", actor.getName());
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
            if (track == null || track.getType() != TrackType.ACTOR) {
                continue;
            }
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }
            ActorRef ref = actorTrack.getActorRef();
            // 找到该演员的轨道，检查是否还有片段
            if (ref != null && actorId.equals(ref.getActorId())) {
                if (actorTrack.hasClips()) {
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

