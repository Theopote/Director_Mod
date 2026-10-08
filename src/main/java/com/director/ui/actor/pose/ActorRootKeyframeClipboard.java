package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Detached whole-key clipboard. Group edits are all-or-nothing. */
public final class ActorRootKeyframeClipboard {
    private List<EntityClipKeyframe> snapshots = List.of();
    public boolean copy(EntityClip clip, EntityClipKeyframe key) {
        return key != null && copy(clip, List.of(key));
    }
    public boolean copy(EntityClip clip, Collection<EntityClipKeyframe> keys) {
        if (clip == null || keys == null || keys.isEmpty() || !clip.getKeyframes().containsAll(keys)) return false;
        var ordered = keys.stream().distinct().sorted(Comparator.comparingDouble(EntityClipKeyframe::getTime)).toList();
        if (ordered.stream().anyMatch(k -> !valid(k))) return false;
        snapshots = ordered.stream().map(EntityClipKeyframe::copy).toList();
        return true;
    }
    public EntityClipKeyframe paste(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        var keys = pasteAll(model, owner, clip); return keys.isEmpty() ? null : keys.getFirst();
    }
    public List<EntityClipKeyframe> pasteAll(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        if (snapshots.isEmpty() || !ActorRootKeyframeEditing.editable(model, owner, clip)) return List.of();
        if (clip instanceof EntityTransformClip && clip.getKeyframes().isEmpty()) return List.of();
        float offset = model.getCurrentTime() - snapshots.getFirst().getTime();
        List<EntityClipKeyframe> added = new ArrayList<>();
        for (var snapshot : snapshots) {
            var key = snapshot.copy(); key.setTime(snapshot.getTime() + offset);
            if (!valid(key) || key.getTime() < clip.getStartTime() || key.getTime() > clip.getEndTime()
                    || clip.getKeyframes().stream().anyMatch(k -> Math.abs(k.getTime() - key.getTime()) <= 1e-4f)
                    || added.stream().anyMatch(k -> Math.abs(k.getTime() - key.getTime()) <= 1e-4f)) return List.of();
            added.add(key);
        }
        var result = List.copyOf(added);
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paste actor root keyframes",
                () -> result.forEach(k -> clip.removeKeyframe(k.getId())), () -> result.forEach(clip::addKeyframe)));
        return result;
    }
    public static boolean delete(TimelineModel model, TimelineTrack owner, EntityClip clip, Collection<EntityClipKeyframe> keys) {
        if (!ActorRootKeyframeEditing.editable(model, owner, clip) || keys == null || keys.isEmpty()
                || !clip.getKeyframes().containsAll(keys)) return false;
        var removed = keys.stream().distinct().toList();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor root keyframes",
                () -> removed.forEach(clip::addKeyframe), () -> removed.forEach(k -> clip.removeKeyframe(k.getId()))));
        return true;
    }
    private static boolean valid(EntityClipKeyframe key) {
        return Float.isFinite(key.getTime()) && key.getPosition().isFinite() && key.getRotation().isFinite()
                && key.getScale().isFinite() && key.getScale().x > 0 && key.getScale().y > 0 && key.getScale().z > 0;
    }
}
