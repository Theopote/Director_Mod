package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.actor.query.ActorUsageQuery;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntitySpawnClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
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
            for (com.chronoblocks.ui.timeline.model.TimelineClip clip : track.getClips()) {
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
        if (actorId == null || hasSpawnClip(model, actorId)) {
            return;
        }

        ActorRegistry registry = ActorRegistryManager.getInstance();
        registry.getActor(actorId).ifPresent(actor -> {
            removedActor = actor;
            removedActorFromRegistry = registry.removeActor(actorId);
        });
    }

    private void restoreActorIfNeeded() {
        if (!removedActorFromRegistry || removedActor == null) {
            return;
        }
        ActorRegistry registry = ActorRegistryManager.getInstance();
        registry.registerActor(removedActor);
    }

    private boolean hasSpawnClip(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return false;
        }

        for (TimelineTrack t : model.getTracks()) {
            if (t == null || t.getType() != TrackType.ENTITY) {
                continue;
            }
            EntityTrack entityTrack = t.getEntityTrackPayload();
            if (entityTrack == null) {
                continue;
            }
            ActorRef ref = entityTrack.getActorRef();
            if (ref != null && actorId.equals(ref.getActorId())) {
                for (com.chronoblocks.ui.timeline.model.entity.EntityClip clip : entityTrack.getClips()) {
                    if (clip instanceof EntitySpawnClip) {
                        return true;
                    }
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


