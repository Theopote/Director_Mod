package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

import java.util.Objects;

/** Undoable project asset operations. Asset IDs remain stable when display names change. */
public final class ActorAnimationAssetEditing {
    private ActorAnimationAssetEditing() {}

    public static AnimationAsset importFile(TimelineModel model, AnimationAssetLibrary library,
                                            java.nio.file.Path file, String newId) throws java.io.IOException {
        AnimationAsset asset = AnimationAssetPersistence.importAsset(file);
        String id = newId == null || newId.isBlank() ? asset.id() : newId.trim();
        requireNewId(model, library, id);
        if (id.equals(asset.id())) {
            insert(model, library, asset);
            return asset;
        }
        AnimationAsset copy = new AnimationAsset(id, asset.name(), asset.rig(), asset.duration(), asset.loopMode());
        asset.boneTracks().forEach((bone, track) -> {
            BoneTrack target = copy.getOrCreateBoneTrack(bone);
            track.translation().keyframes().forEach(k -> target.translation().put(k.time(), k.value(), k.interpolation()));
            track.rotation().keyframes().forEach(k -> target.rotation().put(k.time(), k.value(), k.interpolation()));
            track.scale().keyframes().forEach(k -> target.scale().put(k.time(), k.value(), k.interpolation()));
        });
        asset.markers().forEach(copy::addMarker);
        insert(model, library, copy);
        return copy;
    }

    public static AnimationAsset create(TimelineModel model, AnimationAssetLibrary library,
                                         String id, String name, float duration) {
        requireNewId(model, library, id);
        AnimationAsset asset = new AnimationAsset(id, name, HumanoidRig.DEFINITION, duration, AnimationLoopMode.ONCE);
        insert(model, library, asset);
        return asset;
    }

    public static AnimationAsset duplicate(TimelineModel model, AnimationAssetLibrary library,
                                            String sourceId, String id, String name) {
        requireNewId(model, library, id);
        AnimationAsset source = requireAsset(library, sourceId);
        AnimationAsset copy = new AnimationAsset(id, name, source.rig(), source.duration(), source.loopMode());
        source.boneTracks().forEach((bone, track) -> {
            BoneTrack target = copy.getOrCreateBoneTrack(bone);
            track.translation().keyframes().forEach(k -> target.translation().put(k.time(), k.value(), k.interpolation()));
            track.rotation().keyframes().forEach(k -> target.rotation().put(k.time(), k.value(), k.interpolation()));
            track.scale().keyframes().forEach(k -> target.scale().put(k.time(), k.value(), k.interpolation()));
        });
        source.markers().forEach(copy::addMarker);
        insert(model, library, copy);
        return copy;
    }

    public static void rename(TimelineModel model, AnimationAssetLibrary library, String id, String name) {
        Objects.requireNonNull(model, "model");
        AnimationAsset asset = requireAsset(library, id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("名称不能为空");
        String before = asset.name();
        String after = name.trim();
        if (before.equals(after)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Rename animation asset",
                () -> asset.setName(before), () -> asset.setName(after)));
    }

    /** Includes muted clips and disabled tracks: these are still persistent references. */
    public static int referenceCount(TimelineModel model, String id) {
        Objects.requireNonNull(model, "model");
        int count = 0;
        for (TimelineTrack track : model.getAllTracks()) {
            var payload = track.getActorTrackPayload();
            if (payload == null) continue;
            for (var clip : payload.getClips()) {
                if (clip instanceof EntityAnimationClip animation && id.equals(animation.getAnimationId())) count++;
            }
        }
        return count;
    }

    public static void delete(TimelineModel model, AnimationAssetLibrary library, String id) {
        Objects.requireNonNull(model, "model");
        AnimationAsset asset = requireAsset(library, id);
        if (referenceCount(model, id) > 0) throw new IllegalArgumentException("动画仍被时间线引用，请先移除引用片段");
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete animation asset",
                () -> insertUnique(library, asset), () -> {
                    if (referenceCount(model, id) > 0) throw new IllegalArgumentException("动画仍被时间线引用");
                    library.remove(id);
                }));
    }

    public static void attach(TimelineModel model, AnimationAssetLibrary library, String id, TimelineTrack track) {
        Objects.requireNonNull(model, "model");
        AnimationAsset asset = requireAsset(library, id);
        if (track == null || !model.getAllTracks().contains(track) || track.getActorTrackPayload() == null)
            throw new IllegalArgumentException("请先创建并选择演员轨道");
        var payload = track.getActorTrackPayload();
        float start = Math.max(0f, model.getCurrentTime());
        EntityAnimationClip clip = new EntityAnimationClip(asset.name(), start, start + Math.max(0.1f, asset.duration()));
        clip.setAnimationId(id);
        clip.setSource(EntityAnimationClip.AnimationSource.CUSTOM);
        clip.setLoop(asset.loopMode() != AnimationLoopMode.ONCE);
        EntityTimelineClip wrapper = new EntityTimelineClip(clip, payload.getColorRgba());
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Attach animation asset",
                () -> { payload.removeClip(clip.getId()); track.removeClip(wrapper); },
                () -> { payload.addClip(clip); track.addClip(wrapper); }));
    }

    private static void requireNewId(TimelineModel model, AnimationAssetLibrary library, String id) {
        Objects.requireNonNull(model, "model");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("资产 ID 不能为空");
        if (library.contains(id)) throw new IllegalArgumentException("资产 ID 已存在，请填写新的 ID");
    }

    private static AnimationAsset requireAsset(AnimationAssetLibrary library, String id) {
        return library.find(id).orElseThrow(() -> new IllegalArgumentException("动画资产不存在"));
    }

    private static void insert(TimelineModel model, AnimationAssetLibrary library, AnimationAsset asset) {
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Create animation asset",
                () -> library.remove(asset.id()), () -> insertUnique(library, asset)));
    }

    private static void insertUnique(AnimationAssetLibrary library, AnimationAsset asset) {
        if (!library.addIfAbsent(asset)) throw new IllegalArgumentException("资产 ID 已存在：" + asset.id());
    }
}
