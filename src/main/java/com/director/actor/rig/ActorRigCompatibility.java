package com.director.actor.rig;

import com.director.actor.*;
import com.director.actor.animation.AnimationData;
import com.director.actor.animation.AnimationLibrary;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.TimelineModel;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import java.util.Optional;
import java.util.UUID;

/** Explicit authoring capabilities; procedural motion profiles are not skeleton bindings. */
public final class ActorRigCompatibility {
    public enum Family { HUMANOID, VILLAGER, QUADRUPED, CUSTOM }
    private ActorRigCompatibility() {}
    public static Family family(ActorType type) {
        if (type == null) return Family.CUSTOM;
        return switch (type) {
            case PLAYER, ZOMBIE, SKELETON -> Family.HUMANOID;
            case VILLAGER -> Family.VILLAGER;
            case COW, SHEEP -> Family.QUADRUPED;
            case CUSTOM -> Family.CUSTOM;
        };
    }
    public static boolean accepts(ActorType type, String rigId) {
        if (type == null) return false;
        return switch (type) {
            case VILLAGER -> VillagerRig.DEFINITION.id().equals(rigId);
            case COW, SHEEP -> QuadrupedRig.DEFINITION.id().equals(rigId);
            case PLAYER, ZOMBIE, SKELETON -> HumanoidRig.DEFINITION.id().equals(rigId)
                    || type == ActorType.PLAYER && ArticulatedPlayerRig.DEFINITION.id().equals(rigId);
            case CUSTOM -> false;
        };
    }
    /**
     * Instance-aware check. CUSTOM actors must match their bound rig;
     * built-in types reuse {@link #accepts(ActorType, String)} so PLAYER still accepts
     * both {@code director:humanoid} and {@code director:articulated_player}.
     */
    public static boolean accepts(Actor actor, String rigId) {
        if (actor == null) return false;
        if (actor.getType() == ActorType.CUSTOM) {
            return resolveRig(actor).map(rig -> rig.id().equals(rigId)).orElse(false);
        }
        return accepts(actor.getType(), rigId);
    }
    /** Rig this actor can currently author/play keyframe animation against, if any. */
    public static Optional<RigDefinition> resolveRig(Actor actor) {
        if (actor == null) return Optional.empty();
        Family fam = family(actor.getType());
        if (actor.getType() == ActorType.PLAYER) return Optional.of(com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION);
        if (actor.getType() == ActorType.VILLAGER) return Optional.of(VillagerRig.DEFINITION);
        if (fam == Family.QUADRUPED) return Optional.of(QuadrupedRig.DEFINITION);
        if (fam == Family.HUMANOID) return Optional.of(HumanoidRig.DEFINITION);
        if (fam == Family.CUSTOM) {
            String rigId = actor.getCustomRigId();
            return rigId == null ? Optional.empty() : RigDefinitions.find(rigId);
        }
        return Optional.empty();
    }
    public static boolean editable(UUID actorId) {
        return actorId != null && ActorRegistryManager.getInstance().getActor(actorId)
                .map(a -> resolveRig(a).isPresent()).orElse(false);
    }
    public static String explanation(ActorType type) {
        return switch (family(type)) {
            case HUMANOID -> "标准人形：支持身体、头、双臂与双腿关键帧。";
            case VILLAGER -> "村民型：支持头、交叉手臂与双腿关键帧。";
            case QUADRUPED -> "四足型：支持独立前后四肢、头和身体关键帧。";
            case CUSTOM -> "自定义类型：尚未绑定自定义骨架，不能使用关键帧动画。";
        };
    }
    /** Instance-aware variant: for CUSTOM actors, reports the actually-bound rig (if any). */
    public static String explanation(Actor actor) {
        if (actor == null) return explanation((ActorType) null);
        if (family(actor.getType()) == Family.CUSTOM) {
            return resolveRig(actor)
                    .map(rig -> "自定义类型：已绑定骨架 \"" + rig.displayName() + "\"，支持关键帧动画。")
                    .orElse("自定义类型：尚未绑定自定义骨架，请先在演员属性中选择一个骨架。");
        }
        return explanation(actor.getType());
    }
    public static void requireCompatible(UUID actorId, String rigId) {
        var actor = actorId == null ? null : ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        if (actor == null) throw new IllegalArgumentException("演员不存在，请重新绑定演员轨道");
        if (!accepts(actor, rigId)) throw new IllegalArgumentException(explanation(actor) + " 动画骨架：" + rigId);
    }
    /**
     * Built-in Pose library clips may partially remap onto non-humanoid rigs when they share
     * controlled bone ids (e.g. villager {@code left_leg}/{@code right_leg} from {@code walk}).
     */
    public static boolean acceptsBuiltin(RigDefinition rig, String animationId) {
        if (rig == null || animationId == null || animationId.isBlank()) return false;
        return AnimationLibrary.getInstance().get(animationId).map(data -> acceptsBuiltin(rig, data)).orElse(false);
    }

    public static boolean acceptsBuiltin(RigDefinition rig, AnimationData data) {
        if (rig == null || data == null) return false;
        if (HumanoidTopology.matches(rig)) return true;
        for (String bone : data.controlledBones()) {
            if (rig.findBoneIndex(bone) >= 0) return true;
        }
        return false;
    }

    public static Optional<RigDefinition> defaultRig(ActorType type) {
        if (type == null) return Optional.empty();
        return switch (type) {
            case PLAYER -> Optional.of(ArticulatedPlayerRig.DEFINITION);
            case VILLAGER -> Optional.of(VillagerRig.DEFINITION);
            case COW, SHEEP -> Optional.of(QuadrupedRig.DEFINITION);
            case ZOMBIE, SKELETON -> Optional.of(HumanoidRig.DEFINITION);
            case CUSTOM -> Optional.empty();
        };
    }

    public static boolean canChangeType(TimelineModel model, UUID actorId, ActorType type) {
        for (var track : model.getAllTracks()) {
            var payload = track.getActorTrackPayload();
            if (payload == null || payload.getActorRef() == null || !actorId.equals(payload.getActorRef().getActorId())) continue;
            for (String id:com.director.actor.query.ActorAnimationReferences.ids(payload)) {
                var asset=AnimationAssetLibrary.getInstance().find(id).orElse(null);
                if(asset!=null) {
                    if(!accepts(type,asset.rigId())) return false;
                } else {
                    var builtin = AnimationLibrary.getInstance().get(id);
                    if (builtin.isPresent() && defaultRig(type).map(rig -> !acceptsBuiltin(rig, builtin.get())).orElse(true))
                        return false;
                }
            }
        }
        return true;
    }
}
