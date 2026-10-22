package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

/** Undoable project asset operations. Asset IDs remain stable when display names change. */
public final class ActorAnimationAssetEditing {
    private ActorAnimationAssetEditing() {}

    public static AnimationAsset importFile(TimelineModel model, AnimationAssetLibrary library,
                                            java.nio.file.Path file, String newId) throws java.io.IOException {
        requireEditing(model);
        Objects.requireNonNull(library, "library");
        AnimationAsset asset = AnimationAssetPersistence.importAsset(file);
        String id = newId == null || newId.isBlank() ? asset.id() : newId.trim();
        requireNewId(model, library, id);
        if (id.equals(asset.id())) {
            insert(model, library, asset);
            return asset;
        }
        AnimationAsset copy = copy(asset, id, asset.name());
        insert(model, library, copy);
        return copy;
    }

    public static AnimationAsset create(TimelineModel model, AnimationAssetLibrary library,
                                         String id, String name, float duration) {
        return create(model, library, id, name, duration, HumanoidRig.DEFINITION);
    }

    /** A selected unsupported/missing actor must never silently create a humanoid animation. */
    public static AnimationAsset createForActor(TimelineModel model, AnimationAssetLibrary library,
                                                String id, String name, float duration, UUID actorId) {
        if (actorId == null) return create(model, library, id, name, duration);
        var actor = com.director.actor.ActorRegistryManager.getInstance().getActor(actorId)
                .orElseThrow(() -> new IllegalArgumentException("演员不存在，请重新选择"));
        var rig = com.director.actor.rig.ActorRigCompatibility.resolveRig(actor)
                .orElseThrow(() -> new IllegalArgumentException(com.director.actor.rig.ActorRigCompatibility.explanation(actor)));
        return create(model, library, id, name, duration, rig);
    }

    /** Creates a new asset against an explicit rig (e.g. an actor's bound custom skeleton). */
    public static AnimationAsset create(TimelineModel model, AnimationAssetLibrary library,
                                         String id, String name, float duration,
                                         com.director.actor.rig.skeleton.RigDefinition rig) {
        requireNewId(model, library, id);
        AnimationAsset asset = new AnimationAsset(id, name, rig, duration, AnimationLoopMode.ONCE);
        insert(model, library, asset);
        return asset;
    }

    public static AnimationAsset duplicate(TimelineModel model, AnimationAssetLibrary library,
                                            String sourceId, String id, String name) {
        requireNewId(model, library, id);
        AnimationAsset source = requireAsset(library, sourceId);
        AnimationAsset copy = copy(source, id, name);
        insert(model, library, copy);
        return copy;
    }

    private static AnimationAsset copy(AnimationAsset source, String id, String name) {
        AnimationAsset copy = new AnimationAsset(id, name, source.rig(), source.duration(), source.loopMode());
        source.boneTracks().forEach((bone, track) -> {
            BoneTrack target = copy.getOrCreateBoneTrack(bone);
            track.translation().keyframes().forEach(k -> target.translation().put(k.time(), k.value(), k.interpolation()));
            track.rotation().keyframes().forEach(k -> target.rotation().put(k.time(), k.value(), k.interpolation()));
            track.scale().keyframes().forEach(k -> target.scale().put(k.time(), k.value(), k.interpolation()));
        });
        source.markers().forEach(copy::addMarker);
        return copy;
    }

    public static void rename(TimelineModel model, AnimationAssetLibrary library, String id, String name) {
        requireEditing(model);
        AnimationAsset asset = requireAsset(library, id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("名称不能为空");
        String before = asset.name();
        String after = name.trim();
        if (before.equals(after)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Rename animation asset",
                () -> { requireIdentity(library, asset); asset.setName(before); },
                () -> { requireIdentity(library, asset); asset.setName(after); }));
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
                if (clip instanceof com.director.timeline.model.entity.EntitySpawnClip spawn) {
                    if(id.equals(spawn.getBoneAnimationId())) count++;
                    if(spawn.getPreset()!=null && id.equals(spawn.getPreset().animationId())) count++;
                }
            }
        }
        return count;
    }

    public static void delete(TimelineModel model, AnimationAssetLibrary library, String id) {
        requireEditing(model);
        AnimationAsset asset = requireAsset(library, id);
        if (referenceCount(model, id) > 0) throw new IllegalArgumentException("动画仍被时间线引用，请先清除片段中的引用");
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete animation asset",
                () -> insertUnique(library, asset), () -> removeOwned(model, library, asset)));
    }

    public record AttachmentPreview(int overlappingClips, int references) {}

    /** Pure preflight shared by the UI and command. Existing animation intervals are not overwritten. */
    public static AttachmentPreview previewAttachment(TimelineModel model, AnimationAssetLibrary library,
                                                        String id, TimelineTrack track) {
        requireEditing(model);
        AnimationAsset asset = requireAsset(library, id);
        var target = Target.capture(model, track, asset);
        if (!Float.isFinite(model.getCurrentTime())) throw new IllegalArgumentException("播放头时间无效");
        float start = Math.max(0f, model.getCurrentTime());
        return previewAt(model, target, asset, start);
    }

    private static AttachmentPreview previewAt(TimelineModel model, Target target, AnimationAsset asset, float start) {
        float end = start + Math.max(0.1f, asset.duration());
        if (!Float.isFinite(end)) throw new IllegalArgumentException("动画片段时间范围无效");
        int overlaps = 0;
        for (var candidate : target.payload().getClips()) {
            if (!(candidate instanceof EntityAnimationClip animation)) continue;
            if (asset.id().equals(animation.getAnimationId()) && Math.abs(start - animation.getStartTime()) <= 1.0e-5f)
                throw new IllegalArgumentException("播放头已存在此动画的引用片段，请选择已有片段或移动播放头");
            if (!animation.isMuted() && start < animation.getEndTime() && end > animation.getStartTime()) overlaps++;
        }
        return new AttachmentPreview(overlaps, referenceCount(model, asset.id()));
    }

    public static EntityTimelineClip attach(TimelineModel model, AnimationAssetLibrary library, String id, TimelineTrack track) {
        previewAttachment(model, library, id, track);
        AnimationAsset asset = requireAsset(library, id);
        Target target = Target.capture(model, track, asset);
        var payload = target.payload();
        float start = Math.max(0f, model.getCurrentTime());
        EntityAnimationClip clip = new EntityAnimationClip(asset.name(), start, start + Math.max(0.1f, asset.duration()));
        clip.setAnimationId(id);
        clip.setSource(EntityAnimationClip.AnimationSource.CUSTOM);
        clip.setLoop(asset.loopMode() != AnimationLoopMode.ONCE);
        EntityTimelineClip wrapper = new EntityTimelineClip(clip, payload.getColorRgba());
        TimelineCommandBridge.execute(new ActorAnimationAssetCommand(model, "Attach animation asset",
                () -> {
                    target.validate(model, asset); requireIdentity(library, asset); previewAt(model, target, asset, start);
                    if (track.getClips().contains(wrapper) || payload.getClips().contains(clip))
                        throw new IllegalArgumentException("动画片段已经存在，无法重复添加");
                },
                List.of(new ActorAnimationAssetCommand.Change(() -> payload.removeClip(clip.getId()), () -> payload.addClip(clip)),
                        new ActorAnimationAssetCommand.Change(() -> {
                            track.removeClip(wrapper);
                            if (model.isSelected(wrapper)) model.selectItem(track, false);
                        }, () -> {
                            if (!track.addClip(wrapper)) throw new IllegalArgumentException("演员轨道无法接收动画片段");
                        }))));
        return wrapper;
    }

    /** Duplicate and rebind exactly this clip; its timing, events, wrapper and other references stay intact. */
    public static AnimationAsset makeIndependent(TimelineModel model, AnimationAssetLibrary library,
            TimelineTrack track, EntityAnimationClip clip, String id, String name) {
        requireNewId(model, library, id);
        if (clip == null) throw new IllegalArgumentException("请选择当前演员的动画片段");
        AnimationAsset source = requireAsset(library, clip.getAnimationId());
        Target target = Target.capture(model, track, source);
        if (!ActorEmbeddedAnimation.bound(target.payload(),clip)) throw new IllegalArgumentException("动画片段不属于此演员轨道");
        var visible=ActorEmbeddedAnimation.visibleOwner(track,clip);
        var spawnOwner=visible!=null && visible.getEntityClip() instanceof com.director.timeline.model.entity.EntitySpawnClip spawn?spawn:null;
        AnimationAsset copy = copy(source, id, name);
        var originalSource = clip.getSource();
        TimelineCommandBridge.execute(new ActorAnimationAssetCommand(model, "Make actor animation independent",
                () -> {
                    target.validate(model, source);
                    requireIdentity(library, source);
                    if (spawnOwner!=null
                            ? !target.payload().getClips().contains(spawnOwner) || !source.id().equals(spawnOwner.getBoneAnimationId())
                            : !ActorEmbeddedAnimation.bound(target.payload(),clip) || !source.id().equals(clip.getAnimationId()))
                        throw new IllegalArgumentException("动画片段的绑定已变化，请重新选择");
                    requireNewId(model, library, id);
                }, List.of(
                        new ActorAnimationAssetCommand.Change(() -> removeOwned(model, library, copy), () -> insertUnique(library, copy)),
                        new ActorAnimationAssetCommand.Change(() -> {
                            requireIdentity(library, source);
                            if(spawnOwner!=null) spawnOwner.setBoneAnimationId(source.id());
                            else { clip.setAnimationId(source.id()); clip.setSource(originalSource); }
                            model.invalidateActorEditContext();
                        }, () -> {
                            if(spawnOwner!=null) spawnOwner.setBoneAnimationId(copy.id());
                            else { clip.setAnimationId(copy.id()); clip.setSource(EntityAnimationClip.AnimationSource.CUSTOM); }
                            model.invalidateActorEditContext();
                        }))));
        return copy;
    }

    /** Duplicate the preset asset and rebind only this Spawn's lower layer. */
    public static AnimationAsset makePresetIndependent(TimelineModel model,AnimationAssetLibrary library,
            TimelineTrack track,com.director.timeline.model.entity.EntitySpawnClip spawn,String id,String name) {
        requireNewId(model,library,id);
        if(spawn==null || spawn.getPreset()==null) throw new IllegalArgumentException("当前片段没有预设");
        var before=spawn.getPreset();var source=requireAsset(library,before.animationId());
        var target=Target.capture(model,track,source);
        if(!target.payload().getClips().contains(spawn) || spawn.isMuted()) throw new IllegalArgumentException("预设所属片段不可编辑");
        var copy=copy(source,id,name);
        var after=new com.director.timeline.model.entity.ActorPresetBinding(id,before.loop(),before.speed(),before.blend());
        TimelineCommandBridge.execute(new ActorAnimationAssetCommand(model,"Make actor preset independent",()->{
            target.validate(model,source);requireIdentity(library,source);requireNewId(model,library,id);
            if(!target.payload().getClips().contains(spawn) || spawn.isMuted() || !Objects.equals(before,spawn.getPreset()))
                throw new IllegalArgumentException("预设绑定已变化");
        },List.of(new ActorAnimationAssetCommand.Change(()->removeOwned(model,library,copy),()->insertUnique(library,copy)),
                new ActorAnimationAssetCommand.Change(()->{
                    requireIdentity(library,source);spawn.setPreset(before);model.invalidateActorEditContext();
                },()->{spawn.setPreset(after);model.invalidateActorEditContext();}))));
        return copy;
    }

    /** Prefer an explicitly selected owner when an actor is present on multiple tracks. */
    public static TimelineTrack resolveActorTrack(TimelineModel model, UUID actorId) {
        Objects.requireNonNull(model, "model");
        if (actorId == null) throw new IllegalArgumentException("请先选择演员");
        var candidates = model.getAllTracks().stream().filter(track -> track.getType() == TrackType.ACTOR
                && track.getActorTrackPayload() != null && track.getActorTrackPayload().getActorRef() != null
                && actorId.equals(track.getActorTrackPayload().getActorRef().getActorId())).toList();
        var selected = model.getSelectedItems().stream().map(item -> item instanceof TimelineTrack track ? track
                : item instanceof TimelineClip clip ? clip.getParentTrack() : null)
                .filter(candidates::contains).distinct().toList();
        if (selected.size() == 1) return selected.getFirst();
        if (selected.isEmpty() && candidates.size() == 1) return candidates.getFirst();
        throw new IllegalArgumentException("请明确选择所需的演员轨道或该轨道上的片段");
    }

    public static String uniqueId(AnimationAssetLibrary library, String base) {
        String stem = base == null || base.isBlank() ? "project:animation" : base.trim();
        String candidate = stem;
        for (int suffix = 2; library.contains(candidate); suffix++) candidate = stem + "_" + suffix;
        return candidate;
    }

    private record Target(TimelineTrack track, ActorTrack payload, UUID actorId) {
        static Target capture(TimelineModel model, TimelineTrack track, AnimationAsset asset) {
            if (track == null || track.getActorTrackPayload() == null || track.getActorTrackPayload().getActorRef() == null)
                throw new IllegalArgumentException("请先创建并选择演员轨道");
            var target = new Target(track, track.getActorTrackPayload(), track.getActorTrackPayload().getActorRef().getActorId());
            target.validate(model, asset);
            return target;
        }
        void validate(TimelineModel model, AnimationAsset asset) {
            requireEditing(model);
            if (!model.getAllTracks().contains(track) || track.getType() != TrackType.ACTOR
                    || track.getActorTrackPayload() != payload || payload.getActorRef() == null
                    || !Objects.equals(actorId, payload.getActorRef().getActorId()))
                throw new IllegalArgumentException("演员轨道的绑定已变化，请重新选择");
            if (track.isLocked() || track.isMuted()) throw new IllegalArgumentException("演员轨道已锁定或禁用");
            com.director.actor.rig.ActorRigCompatibility.requireCompatible(actorId, asset.rigId());
        }
    }

    private static void requireNewId(TimelineModel model, AnimationAssetLibrary library, String id) {
        requireEditing(model);
        Objects.requireNonNull(library, "library");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("资产 ID 不能为空");
        if (library.contains(id)) throw new IllegalArgumentException("资产 ID 已存在，请填写新的 ID");
    }

    private static AnimationAsset requireAsset(AnimationAssetLibrary library, String id) {
        Objects.requireNonNull(library, "library");
        return library.find(id).orElseThrow(() -> new IllegalArgumentException("动画资产不存在"));
    }

    private static void insert(TimelineModel model, AnimationAssetLibrary library, AnimationAsset asset) {
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Create animation asset",
                () -> removeOwned(model, library, asset), () -> insertUnique(library, asset)));
    }

    private static void insertUnique(AnimationAssetLibrary library, AnimationAsset asset) {
        if (library.find(asset.id()).orElse(null) == asset) return;
        if (!library.addIfAbsent(asset)) throw new IllegalArgumentException("资产 ID 已存在：" + asset.id());
    }

    private static void requireEditing(TimelineModel model) {
        Objects.requireNonNull(model, "model");
        if (model.isPlaying()) throw new IllegalArgumentException("请先暂停播放再编辑动画资产");
    }

    private static void requireIdentity(AnimationAssetLibrary library, AnimationAsset asset) {
        if (library.find(asset.id()).orElse(null) != asset) throw new IllegalArgumentException("动画资产已被替换或删除");
    }

    private static void removeOwned(TimelineModel model, AnimationAssetLibrary library, AnimationAsset asset) {
        requireIdentity(library, asset);
        if (referenceCount(model, asset.id()) > 0) throw new IllegalArgumentException("动画仍被时间线引用，请先清除片段中的引用");
        library.remove(asset.id());
    }
}
