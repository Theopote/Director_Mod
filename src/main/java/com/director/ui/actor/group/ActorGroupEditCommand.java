package com.director.ui.actor.group;

import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.BaseTimelineCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import java.util.List;

/** One group operation; a failed member restores every member already attempted. */
final class ActorGroupEditCommand extends BaseTimelineCommand {
    record Change(Runnable before, Runnable after) {}
    private static final com.google.gson.Gson SNAPSHOT_JSON = new com.google.gson.Gson();
    private final List<Change> changes;
    private final String description;
    private final com.director.actor.ActorRegistry registry = com.director.actor.ActorRegistryManager.getInstance();
    private record TrackState(com.director.timeline.model.TimelineTrack track,
            com.director.timeline.model.entity.ActorTrack payload, com.director.actor.ActorRef actorRef, com.director.actor.Actor actor,
            List<com.director.timeline.model.TimelineClip> wrappers,
            List<com.director.timeline.model.entity.EntityClip> clips,
            List<com.google.gson.JsonElement> values) {}
    private List<TrackState> expected;
    private List<TrackState> snapshot() {
        return model.getAllTracks().stream().filter(t -> t.getActorTrackPayload() != null)
                .map(t -> new TrackState(t, t.getActorTrackPayload(), t.getActorTrackPayload().getActorRef(),
                        t.getActorTrackPayload().getActorRef() == null ? null : registry.getActor(t.getActorTrackPayload().getActorRef().getActorId()).orElse(null),
                        List.copyOf(t.getClips()), List.copyOf(t.getActorTrackPayload().getClips()),
                        t.getActorTrackPayload().getClips().stream().map(c -> SNAPSHOT_JSON.toJsonTree(
                                com.director.timeline.persistence.TimelineModelPersistence.entityClipToDTO(c))).toList())).toList();
    }
    private void validate() {
        if (model.isPlaying() || model.isScrubbing()) throw new IllegalArgumentException("请先暂停播放或时间拖动");
        if (registry != com.director.actor.ActorRegistryManager.getInstance() || !expected.equals(snapshot()))
            throw new IllegalArgumentException("群组操作的工程、轨道绑定或片段已变化，无法覆盖新数据");
    }
    private ActorGroupEditCommand(TimelineModel model, String description, List<Change> changes) {
        super(model, "actor_group_edit");
        this.description = description;
        this.changes = List.copyOf(changes);
        this.expected = snapshot();
    }
    static void execute(TimelineModel model, String description, List<Change> changes) {
        if (!changes.isEmpty()) TimelineCommandBridge.execute(new ActorGroupEditCommand(model, description, changes));
    }
    @Override protected void doExecute() {
        validate();
        int attempted = 0;
        try {
            for (var change : changes) { attempted++; change.after().run(); }
        } catch (RuntimeException failure) {
            for (int i = attempted - 1; i >= 0; i--) {
                try { changes.get(i).before().run(); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        expected = snapshot();
    }
    @Override protected void doUndo() {
        validate();
        int attempted = changes.size();
        try {
            for (int i = changes.size() - 1; i >= 0; i--) { attempted = i; changes.get(i).before().run(); }
        } catch (RuntimeException failure) {
            for (int i = attempted; i < changes.size(); i++) {
                try { changes.get(i).after().run(); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        expected = snapshot();
    }
    @Override public String getDescription() { return description; }
}
