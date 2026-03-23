package com.director.ui.timeline.commands;

import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.query.ActorUsageQuery;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.EntityTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

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
    
    // 记录删除 Track 后，Actor 是否还在其他地方被使用
    private UUID orphanedActorId = null; // 如果删除 Track 后 Actor 未被使用，记录其 ID
    private Actor removedActor;
    private boolean removedActorFromRegistry;

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
        removedActor = null;
        removedActorFromRegistry = false;
        
        // 如果是 EntityTrack，检查删除后 Actor 是否还在其他地方被使用
        if (track.getType() == TrackType.ENTITY) {
            EntityTrack entityTrack = track.getEntityTrackPayload();
            if (entityTrack != null) {
                ActorRef actorRef = entityTrack.getActorRef();
                if (actorRef != null) {
                    UUID actorId = actorRef.getActorId();
                    
                    // 检查删除前，该 Actor 是否还在其他地方被使用
                    List<EntityTrack> tracksUsingActor = ActorUsageQuery.findTracksUsingActor(model, actorId);
                    
                    // 如果只有当前 Track 使用该 Actor，删除后 Actor 将变为"未使用"
                    if (tracksUsingActor.size() == 1 && tracksUsingActor.contains(entityTrack)) {
                        orphanedActorId = actorId;
                        LOGGER.info("[DeleteTrackCommand] 删除轨道后，Actor {} 将不再被任何轨道使用", actorId);
                    }
                }
            }
        }
        
        removedIndex = trackList.indexOf(track);
        if (removedIndex >= 0) {
            // 清理轨道中所有 clips 的插值映射，避免内存泄漏
            for (com.director.ui.timeline.model.TimelineClip clip : track.getClips()) {
                ChangeKeyframeInterpolationCommand.clearInterpolationsForClip(clip);
            }
            trackList.remove(removedIndex);
            removeActorIfOrphaned();
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
            restoreActorIfNeeded();
            LOGGER.info("[Timeline] 恢复轨道到索引: {}", removedIndex);
        }
    }

    /**
     * 检查并删除孤立的演员
     * <p>如果删除轨道后，该演员没有任何轨道和片段了，则从注册表中删除该演员
     */
    private void removeActorIfOrphaned() {
        if (track == null || track.getType() != TrackType.ENTITY || model == null) {
            return;
        }

        EntityTrack entityTrack = track.getEntityTrackPayload();
        if (entityTrack == null) {
            return;
        }

        ActorRef actorRef = entityTrack.getActorRef();
        if (actorRef == null) {
            return;
        }

        UUID actorId = actorRef.getActorId();

        // 检查该演员是否还有任何轨道和片段（不限于 SpawnClip）
        if (hasAnyClip(model, actorId)) {
            return;
        }

        // 如果没有轨道和片段了，删除该演员
        ActorRegistry registry = ActorRegistryManager.getInstance();
        registry.getActor(actorId).ifPresent(actor -> {
            removedActor = actor;
            removedActorFromRegistry = registry.removeActor(actorId);
            
            // 清理 Minecraft 世界中的预览实体
            ActorPreviewManager.getInstance().removePreview(actorId);
            
            // 如果删除的是当前选中的演员，清除选择（包括方块选区）
            com.director.ui.actor.selection.ActorSelectionHub.clearSelection();
            
            LOGGER.info("[DeleteTrackCommand] 演员 {} 的所有轨道已删除，已从注册表中移除，预览实体和选区已清理", actor.getName());
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
     * 检查指定演员是否还有任何轨道和片段
     * 
     * @param model TimelineModel
     * @param actorId 演员ID
     * @return 如果还有轨道和片段则返回 true
     */
    private boolean hasAnyClip(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return false;
        }

        // 遍历所有轨道，查找该演员的轨道
        for (TimelineTrack t : model.getTracks()) {
            if (t == null || t.getType() != TrackType.ENTITY) {
                continue;
            }
            EntityTrack entityTrack = t.getEntityTrackPayload();
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
        return track != null ? "删除轨道: " + track.getName() : "删除轨道";
    }

    @Override
    public boolean canUndo() {
        return removedIndex >= 0;
    }
    
    /**
     * 获取删除 Track 后变为"未使用"的 Actor ID
     * <p>如果删除 Track 后，该 Actor 没有被任何其他 Track 使用，返回其 ID
     * <p>注意：此方法不会自动删除 Actor，只是提供信息供 UI 提示用户
     *
     * @return Actor ID，如果不存在则返回 null
     */
    public UUID getOrphanedActorId() {
        return orphanedActorId;
    }
}


