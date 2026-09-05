 package com.director.actor.rig;

import com.director.actor.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.actor.animation.asset.AnimationAssetLibrary;
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
        return family(type) == Family.HUMANOID && HumanoidRig.DEFINITION.id().equals(rigId);
    }
    public static boolean editable(UUID actorId) {
        return actorId != null && ActorRegistryManager.getInstance().getActor(actorId)
                .map(a -> family(a.getType()) == Family.HUMANOID).orElse(false);
    }
    public static String explanation(ActorType type) {
        return switch (family(type)) {
            case HUMANOID -> "标准人形：支持身体、头、双臂与双腿关键帧。";
            case VILLAGER -> "村民型：尚未实现独立骨骼绑定，不能使用标准人形关键帧动画。";
            case QUADRUPED -> "四足型：尚未实现四足骨骼绑定，可继续使用现有过程化动作。";
            case CUSTOM -> "自定义类型：尚未声明兼容骨架，不能使用标准人形关键帧动画。";
        };
    }
    public static void requireCompatible(UUID actorId, String rigId) {
        var actor = actorId == null ? null : ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        if (actor == null) throw new IllegalArgumentException("演员不存在，请重新绑定演员轨道");
        if (!accepts(actor.getType(), rigId)) throw new IllegalArgumentException(explanation(actor.getType()) + " 动画骨架：" + rigId);
    }
    public static boolean canChangeType(TimelineModel model, UUID actorId, ActorType type) {
        for (var track : model.getAllTracks()) {
            var payload = track.getActorTrackPayload();
            if (payload == null || payload.getActorRef() == null || !actorId.equals(payload.getActorRef().getActorId())) continue;
            for (var clip : payload.getClips()) if (clip instanceof EntityAnimationClip animation) {
                var asset = AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).orElse(null);
                if (asset != null && !accepts(type, asset.rigId())) return false;
            }
        }
        return true;
    }
}
