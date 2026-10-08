package com.director.ui.timeline.commands;

import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
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
        orphanedActorId = null;
        
        // 如果是 ActorTrack，检查删除后 Actor 是否还在其他地方被使用
        if (track.getType() == TrackType.ACTOR) {
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack != null) {
                ActorRef actorRef = actorTrack.getActorRef();
                if (actorRef != null) {
                    UUID actorId = actorRef.getActorId();
                    
                    // 检查删除前，该 Actor 是否还在其他地方被使用
                    List<ActorTrack> tracksUsingActor = ActorUsageQuery.findTracksUsingActor(model, actorId);
                    
                    // 如果只有当前 Track 使用该 Actor，删除后 Actor 将变为"未使用"
                    if (tracksUsingActor.size() == 1 && tracksUsingActor.contains(actorTrack)) {
                        orphanedActorId = actorId;
                        LOGGER.info("[DeleteTrackCommand] 删除轨道后，Actor {} 将不再被任何轨道使用", actorId);
                    }
                }
            }
        }
        
        removedIndex = trackList.indexOf(track);
        if (removedIndex >= 0) {
            // 清理轨道中所有 clips 的插值映射，避免内存泄漏
            for (com.director.timeline.model.TimelineClip clip : track.getClips()) {
                if (track.getType() == TrackType.CONTAINER && clip.getBoundContainerId() != null) {
                    com.director.ui.timeline.audio.TimelineAudioService.getInstance().onClipRemoved(clip);
                }
                ChangeKeyframeInterpolationCommand.clearInterpolationsForClip(clip);
            }
            trackList.remove(removedIndex);
            // Removing an animation owner must not delete the project actor instance.
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


