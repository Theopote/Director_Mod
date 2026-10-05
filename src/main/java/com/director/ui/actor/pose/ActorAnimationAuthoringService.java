package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.ui.timeline.commands.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Prepares mixed-channel edits off-model, then commits the entire selection once. */
public final class ActorAnimationAuthoringService {
    private static final float EPSILON = 1.0e-5f;
    private ActorAnimationAuthoringService() {}
    public enum Channel { TRANSLATION, ROTATION, SCALE }
    public record Key(String boneId, Channel channel, float time) {}
    public record Paste(String boneId, Channel channel, float time, Object value, InterpolationMode interpolation) {
        public Paste {
            if (value instanceof Vector3f vector) value = new Vector3f(vector);
            else if (value instanceof Quaternionf rotation) value = new Quaternionf(rotation);
        }
    }

    public static boolean keyPose(ActorAnimationEditContext context, RigPose pose,
                                  Collection<String> bones, InterpolationMode mode, int channels) {
        if (context == null || !context.isCurrent() || pose == null || bones == null
                || !pose.rig().id().equals(context.asset().rigId())) return false;
        List<Paste> keys = new ArrayList<>();
        for (String id : new LinkedHashSet<>(bones)) {
            if (!hasBone(context.asset(), id)) return false;
            BoneTransform value = pose.localTransform(id);
            if ((channels & RigPose.TRANSLATION) != 0 && supports(context.asset(), id, Channel.TRANSLATION))
                keys.add(new Paste(id, Channel.TRANSLATION, context.assetTime(), value.translation(), mode));
            if ((channels & RigPose.ROTATION) != 0 && supports(context.asset(), id, Channel.ROTATION))
                keys.add(new Paste(id, Channel.ROTATION, context.assetTime(), value.rotation(), mode));
            if ((channels & RigPose.SCALE) != 0 && supports(context.asset(), id, Channel.SCALE))
                keys.add(new Paste(id, Channel.SCALE, context.assetTime(), value.scale(), mode));
        }
        return write(context, keys, true, "Key actor pose");
    }

    public static boolean paste(ActorAnimationEditContext context, Collection<Paste> keys) {
        return write(context, keys, false, "Paste actor keyframes");
    }

    private static boolean write(ActorAnimationEditContext context, Collection<Paste> keys,
                                 boolean overwrite, String description) {
        if (context == null || !context.isCurrent() || keys == null || keys.isEmpty()) return false;
        Draft draft = new Draft(context.asset());
        List<Key> destinations = new ArrayList<>();
        boolean changed = false;
        for (Paste key : keys) {
            if (key == null || !valid(context.asset(), new Key(key.boneId(), key.channel(), key.time()))
                    || !validValue(key.channel(), key.value())) return false;
            Key destination = new Key(key.boneId(), key.channel(), key.time());
            if (destinations.stream().anyMatch(existing -> sameKey(existing, destination))) return false;
            destinations.add(destination);
            BoneTrack target = draft.track(key.boneId());
            var existing = frame(target, destination);
            if (!overwrite && existing != null) return false;
            InterpolationMode mode = key.interpolation() == null ? InterpolationMode.SMOOTH : key.interpolation();
            changed |= existing == null || !existing.value().equals(key.value()) || existing.interpolation() != mode;
            put(target, destination, key.value(), mode);
        }
        return changed && draft.commit(context, description,overwrite?null:destinations);
    }

    public static boolean delete(ActorAnimationEditContext context, Collection<Key> keys) {
        if (context == null || !context.isCurrent() || keys == null || keys.isEmpty()) return false;
        Draft draft = new Draft(context.asset());
        for (Key key : new LinkedHashSet<>(keys)) {
            if (!valid(context.asset(), key)) return false;
            BoneTrack target = draft.track(key.boneId());
            if (frame(target, key) == null) return false;
            channel(target, key.channel()).removeAt(key.time());
        }
        return draft.commit(context, "Delete actor keyframes",List.of());
    }

    /** Change outgoing interpolation only; no new keys, time shifts or value changes. */
    public static boolean setInterpolation(ActorAnimationEditContext context, Collection<Key> keys,
                                           InterpolationMode mode) {
        if(context==null || !context.isCurrent() || keys==null || keys.isEmpty() || mode==null) return false;
        Draft draft=new Draft(context.asset());
        boolean changed=false;
        for(var key:new LinkedHashSet<>(keys)) {
            if(!valid(context.asset(),key)) return false;
            var track=draft.track(key.boneId());
            var existing=frame(track,key);
            if(existing==null) return false;
            if(existing.interpolation()!=mode) {
                changed=true;
                put(track,new Key(key.boneId(),key.channel(),existing.time()),existing.value(),mode);
            }
        }
        return changed && draft.commit(context,"Change actor keyframe interpolation");
    }

    public static boolean moveTime(ActorAnimationEditContext context, Collection<Key> keys, float value) {
        if(context==null || !context.isCurrent() || keys==null || keys.isEmpty() || !Float.isFinite(value)
                || (context.loop() && context.loopMode()!=AnimationLoopMode.ONCE)) return false;
        var distinct=new LinkedHashSet<>(keys);
        if(distinct.stream().anyMatch(Objects::isNull)) return false;
        float delta=distinct.size()==1?value- distinct.getFirst().time():value;
        double limit=Math.min(context.duration(),(context.end()-context.start())*(double)Math.max(.01f,context.speed()));
        for(var key:distinct) {
            double destination=key.time()+(double)delta;
            if(!Double.isFinite(destination) || destination<0 || destination>limit) return false;
        }
        return move(context,distinct,delta);
    }

    public static boolean move(ActorAnimationEditContext context, Collection<Key> keys, float delta) {
        if (context == null || !context.isCurrent() || keys == null || keys.isEmpty()
                || !Float.isFinite(delta) || Math.abs(delta) <= EPSILON) return false;
        List<Key> source = new ArrayList<>(new LinkedHashSet<>(keys));
        Draft draft = new Draft(context.asset());
        List<Paste> moved = new ArrayList<>();
        for (Key key : source) {
            if (!valid(context.asset(), key)) return false;
            Key destination = new Key(key.boneId(), key.channel(), key.time() + delta);
            if (!valid(context.asset(), destination)) return false;
            BoneTrack target = draft.track(key.boneId());
            var before = frame(target, key);
            if (before == null) return false;
            if (frame(target, destination) != null
                    && source.stream().noneMatch(selected -> sameKey(selected, destination))) return false;
            if (moved.stream().anyMatch(paste -> sameKey(new Key(paste.boneId(), paste.channel(), paste.time()), destination)))
                return false;
            moved.add(new Paste(key.boneId(), key.channel(), destination.time(), before.value(), before.interpolation()));
        }
        for (Key key : source) channel(draft.track(key.boneId()), key.channel()).removeAt(key.time());
        for (Paste paste : moved) put(draft.track(paste.boneId()),
                new Key(paste.boneId(), paste.channel(), paste.time()), paste.value(), paste.interpolation());
        return draft.commit(context, "Move actor keyframes",moved.stream().map(p -> new Key(p.boneId(),p.channel(),p.time())).toList());
    }

    public static boolean mirror(ActorAnimationEditContext context, Collection<Float> times, InterpolationMode mode) {
        if (context == null || !context.isCurrent() || times == null || times.isEmpty()
                || !HumanoidTopology.matches(context.asset().rig())) return false;
        List<Paste> keys = new ArrayList<>();
        String[] target = {HumanoidRig.BODY, HumanoidRig.HEAD, HumanoidRig.LEFT_ARM,
                HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_LEG, HumanoidRig.RIGHT_LEG};
        String[] source = {HumanoidRig.BODY, HumanoidRig.HEAD, HumanoidRig.RIGHT_ARM,
                HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_LEG, HumanoidRig.LEFT_LEG};
        for (Float time : new LinkedHashSet<>(times)) {
            if (time == null || !Float.isFinite(time) || time < 0 || time > context.asset().duration()) return false;
            RigPose pose = context.asset().sample(time, AnimationLoopMode.ONCE);
            for (int i = 0; i < target.length; i++) {
                InterpolationMode interpolation = context.asset().findBoneTrack(source[i])
                        .flatMap(track -> track.rotation().findAt(time)).map(ChannelKeyframe::interpolation).orElse(mode);
                keys.add(new Paste(target[i], Channel.ROTATION, time,
                        ActorAnimationKeyframeEditing.mirrorAcrossX(pose.localTransform(source[i]).rotation()), interpolation));
            }
        }
        return write(context, keys, true, "Mirror actor pose");
    }

    private static boolean sameKey(Key a, Key b) {
        return Objects.equals(a.boneId(), b.boneId()) && a.channel() == b.channel()
                && Math.abs(a.time() - b.time()) <= EPSILON;
    }
    private static boolean hasBone(AnimationAsset asset, String id) {
        return id != null && asset.rig().bones().stream().anyMatch(bone -> bone.id().equals(id));
    }
    private static boolean valid(AnimationAsset asset, Key key) {
        return key != null && key.channel() != null && Float.isFinite(key.time()) && key.time() >= 0
                && key.time() <= asset.duration() && hasBone(asset, key.boneId())
                && supports(asset, key.boneId(), key.channel());
    }
    private static boolean supports(AnimationAsset asset, String id, Channel channel) {
        var capability = asset.rig().bone(asset.rig().requireBoneIndex(id)).capabilities();
        return switch (channel) {
            case TRANSLATION -> capability.translation();
            case SCALE -> capability.scale();
            case ROTATION -> capability.rotation()
                    && !(HumanoidTopology.matches(asset.rig()) && HumanoidRig.ROOT.equals(id));
        };
    }
    private static boolean validValue(Channel channel, Object value) {
        if (channel == Channel.ROTATION) return value instanceof Quaternionf q && q.isFinite() && q.lengthSquared() > 0;
        return value instanceof Vector3f v && v.isFinite()
                && (channel != Channel.SCALE || v.x > 0 && v.y > 0 && v.z > 0);
    }
    private static KeyframeTrack<?> channel(BoneTrack track, Channel channel) {
        return switch (channel) {
            case TRANSLATION -> track.translation();
            case ROTATION -> track.rotation();
            case SCALE -> track.scale();
        };
    }
    private static ChannelKeyframe<?> frame(BoneTrack track, Key key) {
        return channel(track, key.channel()).findAt(key.time()).orElse(null);
    }
    private static void put(BoneTrack track, Key key, Object value, InterpolationMode mode) {
        switch (key.channel()) {
            case TRANSLATION -> track.translation().put(key.time(), (Vector3f)value, mode);
            case ROTATION -> track.rotation().put(key.time(), (Quaternionf)value, mode);
            case SCALE -> track.scale().put(key.time(), (Vector3f)value, mode);
        }
    }

    private static BoneTrack copy(BoneTrack source) {
        BoneTrack target = new BoneTrack();
        if (source != null) copyInto(source, target);
        return target;
    }
    private static void copyInto(BoneTrack source, BoneTrack target) {
        copyChannel(source.translation(), target.translation());
        copyChannel(source.rotation(), target.rotation());
        copyChannel(source.scale(), target.scale());
    }
    private static <T> void copyChannel(KeyframeTrack<T> source, KeyframeTrack<T> target) {
        for (var key : List.copyOf(target.keyframes())) target.removeAt(key.time());
        for (var key : source.keyframes()) target.put(key.time(), key.value(), key.interpolation());
    }
    private static final class Draft {
        private final AnimationAsset asset;
        private final Map<String, BoneTrack> original = new LinkedHashMap<>();
        private final Map<String, BoneTrack> before = new LinkedHashMap<>();
        private final Map<String, BoneTrack> after = new LinkedHashMap<>();
        Draft(AnimationAsset asset) { this.asset = asset; }
        BoneTrack track(String id) {
            return after.computeIfAbsent(id, ignored -> {
                BoneTrack existing = asset.findBoneTrack(id).orElse(null);
                original.put(id, existing);
                before.put(id, copy(existing));
                return copy(existing);
            });
        }
        private void requireCommandSource(ActorAnimationEditContext context,Map<String,BoneTrack> expected,
                                          Map<String,BoneTrack> identities) {
            if(!context.isBound()) throw new IllegalArgumentException("Animation target changed or is read-only");
            for(String id:after.keySet()) {
                var actual=asset.findBoneTrack(id).orElse(null);
                var value=expected.get(id);
                if(actual!=identities.get(id) || (actual!=null && (value==null
                        || !actual.translation().keyframes().equals(value.translation().keyframes())
                        || !actual.rotation().keyframes().equals(value.rotation().keyframes())
                        || !actual.scale().keyframes().equals(value.scale().keyframes()))))
                    throw new IllegalArgumentException("Bone keyframes changed; refusing to overwrite them");
            }
        }
        private void restoreSelection(ActorAnimationEditContext context,List<Key> keys) {
            var selection=ActorKeyframeSelection.getInstance();
            var current=selection.snapshot(context.model());
            if(current!=null && current.focused() && current.owner()==context.track()
                    && current.clip()==context.clip() && current.asset()==asset)
                selection.replace(context.model(),context.track(),context.clip(),asset,keys,true);
        }
        boolean commit(ActorAnimationEditContext context, String description) { return commit(context,description,null); }
        boolean commit(ActorAnimationEditContext context, String description,Collection<Key> selectedAfter) {
            if (!context.isCurrent()) return false;
            var selection=ActorKeyframeSelection.getInstance();
            var selectedBefore=selection.snapshot(context.model());
            boolean ownsSelection=selectedBefore!=null && selectedBefore.owner()==context.track()
                    && selectedBefore.clip()==context.clip() && selectedBefore.asset()==asset && selectedBefore.focused();
            var beforeKeys=ownsSelection?selectedBefore.keys():List.<Key>of();
            var afterKeys=selectedAfter==null?beforeKeys:List.copyOf(selectedAfter);
            boolean[] initial={true};
            Map<String, BoneTrack> targets = new LinkedHashMap<>();
            after.forEach((id, value) -> targets.put(id, original.get(id) == null ? new BoneTrack() : original.get(id)));
            TimelineCommandBridge.execute(new StateChangeCommand(context.model(), description,
                    () -> { requireCommandSource(context,after,targets);
                        ActorAnimationEditingLifecycle.cancelTransientWork();
                        before.forEach((id, value) -> {
                        if (original.get(id) == null) asset.removeBoneTrack(id);
                        else { copyInto(value, targets.get(id)); asset.attachBoneTrack(id, targets.get(id)); }
                    }); if(ownsSelection) restoreSelection(context,beforeKeys); },
                    () -> { requireCommandSource(context,before,original);
                        if(!initial[0]) ActorAnimationEditingLifecycle.cancelTransientWork();
                        after.forEach((id, value) -> {
                        copyInto(value, targets.get(id));
                        asset.attachBoneTrack(id, targets.get(id));
                    }); if(ownsSelection) restoreSelection(context,afterKeys); initial[0]=false; }));
            return true;
        }
    }
}
