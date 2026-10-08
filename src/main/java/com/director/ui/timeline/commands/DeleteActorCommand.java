package com.director.ui.timeline.commands;

import com.director.actor.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.*;
import java.util.*;

/** Explicit project actor deletion. Keeps assets and object identity for undo. */
public final class DeleteActorCommand extends BaseTimelineCommand {
    private record TrackEntry(TimelineTrack track, int index) {}
    private final ActorRegistry registry;
    private final Actor actor;
    private final List<Actor> actorsBefore;
    private final List<TrackEntry> tracks;
    private final Map<ActorGroup, List<UUID>> groups = new LinkedHashMap<>();
    private final java.util.function.Consumer<Boolean> stateChanged;
    private boolean deleted;
    private final List<Object> selectionBefore;
    private final boolean ownsSelection;

    public DeleteActorCommand(TimelineModel model, ActorRegistry registry, UUID id) {
        this(model, registry, id, ignored -> {});
    }

    public DeleteActorCommand(TimelineModel model, ActorRegistry registry, UUID id,
                              java.util.function.Consumer<Boolean> stateChanged) {
        super(Objects.requireNonNull(model), "actor_deleted");
        this.registry = Objects.requireNonNull(registry);
        this.actor = registry.getActor(id).orElseThrow(() -> new IllegalArgumentException("演员不存在"));
        this.stateChanged = Objects.requireNonNull(stateChanged);
        actorsBefore = registry.getAllActors();
        tracks = ActorUsageQuery.findTimelineTracksUsingActor(model, id).stream()
                .map(track -> new TrackEntry(track, model.getTrackIndex(track))).toList();
        selectionBefore = List.copyOf(model.getSelectedItems());
        ownsSelection = selectionBefore.stream().anyMatch(item -> tracks.stream().anyMatch(entry ->
                item == entry.track() || item instanceof TimelineClip clip && clip.getParentTrack() == entry.track()));
        for (ActorGroup group : registry.getAllGroups()) {
            if (group.containsActor(id)) groups.put(group, group.getActorIds());
        }
    }

    @Override protected void doExecute() {
        if (deleted) throw new IllegalStateException("演员已经删除");
        if (registry.getActor(actor.getActorId()).orElse(null) != actor)
            throw new IllegalStateException("演员身份已变化");
        for (TrackEntry entry : tracks) {
            if (!model.getAllTracks().contains(entry.track())) throw new IllegalStateException("演员轨道已变化");
        }
        if (ownsSelection) model.clearSelection();
        for (TrackEntry entry : tracks) model.removeTrack(entry.track());
        registry.removeActor(actor.getActorId());
        deleted = true;
    }

    @Override protected void doUndo() {
        if (!deleted) throw new IllegalStateException("演员未删除");
        if (registry.contains(actor.getActorId())) throw new IllegalStateException("演员 ID 已被占用");
        // Restore browser ordering without removing groups or replacing other actors.
        List<Actor> current = registry.getAllActors();
        registry.registerActor(actor);
        List<Actor> ordered = new ArrayList<>();
        for (Actor value : actorsBefore)
            if (registry.getActor(value.getActorId()).orElse(null) == value) ordered.add(value);
        for (Actor value : current) if (!ordered.contains(value)) ordered.add(value);
        registry.reorderActors(ordered);
        for (var entry : groups.entrySet()) {
            if (registry.getGroup(entry.getKey().getGroupId()).orElse(null) == entry.getKey()) {
                List<UUID> members = entry.getKey().getActorIds();
                entry.getKey().clearActors();
                for (UUID id : entry.getValue())
                    if (id.equals(actor.getActorId()) || members.contains(id)) entry.getKey().addActor(id);
                for (UUID id : members) entry.getKey().addActor(id);
            }
        }
        for (TrackEntry entry : tracks) {
            model.addTrack(entry.track());
            model.moveTrack(model.getTrackIndex(entry.track()), Math.min(entry.index(), model.getAllTracks().size() - 1));
        }
        if (ownsSelection) {
            model.clearSelection();
            for (Object item : selectionBefore) model.selectItem(item, true);
        }
        deleted = false;
    }

    @Override protected void notifyModelChanged() {
        super.notifyModelChanged();
        stateChanged.accept(deleted);
    }
    @Override public boolean canUndo() { return deleted; }
    @Override public String getDescription() { return "删除演员: " + actor.getName(); }
}
