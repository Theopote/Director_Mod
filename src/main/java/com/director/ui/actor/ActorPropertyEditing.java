package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.timeline.commands.*;

/** Undoable project actor edits; UI supplies preview refresh after a type change. */
public final class ActorPropertyEditing {
    private ActorPropertyEditing() {}

    public static void rename(TimelineModel model, Actor actor, String name) {
        if (model == null || name == null || name.isBlank()) return;
        String before = actor.getName(), after = name.strip();
        if (before.equals(after)) return;
        var tracks = ActorUsageQuery.findTimelineTracksUsingActor(model, actor.getActorId());
        var names = tracks.stream().map(TimelineTrack::getName).toList();
        var displayNames = tracks.stream().map(t -> t.getActorTrackPayload().getDisplayName()).toList();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Rename actor", () -> {
            actor.setName(before);
            for (int i = 0; i < tracks.size(); i++) {
                tracks.get(i).setName(names.get(i));
                tracks.get(i).getActorTrackPayload().setDisplayName(displayNames.get(i));
            }
        }, () -> {
            actor.setName(after);
            for (int i = 0; i < tracks.size(); i++) {
                // Only names still following the actor name are synchronized.
                if (before.equals(names.get(i))) tracks.get(i).setName(after);
                if (before.equals(displayNames.get(i))) tracks.get(i).getActorTrackPayload().setDisplayName(after);
            }
        }));
    }

    public static void changeType(TimelineModel model, Actor actor, ActorType type, Runnable refresh) {
        if (model == null || type == null || type == actor.getType()) return;
        if (!ActorAppearanceEditing.editable(model)) throw new IllegalArgumentException("请先暂停并完成当前草稿");
        var registry = ActorRegistryManager.getInstance();
        if (registry.getActor(actor.getActorId()).orElse(null) != actor)
            throw new IllegalArgumentException("演员已删除或替换，请重新选择");
        if (!com.director.actor.rig.ActorRigCompatibility.canChangeType(model, actor.getActorId(), type))
            throw new IllegalArgumentException("现有动画与目标演员类型不兼容");
        var before = actor.getType();
        var profile = actor.getRigProfile();
        // Capture the new default once so redo preserves the same profile instance.
        var nextProfile = new Actor("preview", type).getRigProfile();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Change actor type", () -> {
            checkTypeTarget(model, registry, actor, type, nextProfile, before);
            actor.setType(before);
            actor.setRigProfile(profile);
            refresh.run();
        }, () -> {
            checkTypeTarget(model, registry, actor, before, profile, type);
            actor.setType(type);
            actor.setRigProfile(nextProfile);
            refresh.run();
        }));
    }
    private static void checkTypeTarget(TimelineModel model, ActorRegistry registry, Actor actor,
            ActorType expectedType, com.director.actor.rig.ActorRigProfile expectedProfile, ActorType nextType) {
        if (!ActorAppearanceEditing.editable(model)) throw new IllegalArgumentException("请先暂停并完成当前草稿");
        if (ActorRegistryManager.getInstance() != registry || registry.getActor(actor.getActorId()).orElse(null) != actor)
            throw new IllegalArgumentException("工程或演员身份已变化，请重新选择");
        if (actor.getType() != expectedType || actor.getRigProfile() != expectedProfile)
            throw new IllegalArgumentException("演员类型或动作风格来源已变化");
        if (!com.director.actor.rig.ActorRigCompatibility.canChangeType(model, actor.getActorId(), nextType))
            throw new IllegalArgumentException("现有动画与目标演员类型不兼容");
    }

}
