package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;

/** Presentation labels for shared rig-domain scopes. */
public enum ActorPresetScope {
    FULL("全身",PoseScope.FULL), UPPER("仅上半身",PoseScope.UPPER), LOWER("仅双腿",PoseScope.LOWER);
    private final String label;
    private final PoseScope scope;
    ActorPresetScope(String label,PoseScope scope) { this.label=label;this.scope=scope; }
    public String label() { return label; }
    public boolean supported(RigDefinition rig) { return scope.supported(rig); }
    public boolean matches(ActorPresetBinding binding,RigDefinition rig) { return scope.matches(binding.blend(),rig); }
    public ActorPresetBinding apply(ActorPresetBinding binding,RigDefinition rig) {
        return new ActorPresetBinding(binding.animationId(),binding.loop(),binding.speed(),scope.apply(binding.blend(),rig));
    }
    public static String currentLabel(ActorPresetBinding binding,RigDefinition rig) {
        if(FULL.matches(binding,rig)) return FULL.label;
        if(UPPER.matches(binding,rig)) return UPPER.label;
        if(LOWER.matches(binding,rig)) return LOWER.label;
        return "自定义部位";
    }
    public static ActorPresetBinding restoreChannelsAndBones(ActorPresetBinding binding) {
        var blend=binding.blend();
        return new ActorPresetBinding(binding.animationId(),binding.loop(),binding.speed(),new PoseBlendSettings(
                blend.mode(),blend.weight(),RigPose.ALL_CHANNELS,true,java.util.Set.of(),blend.fadeIn(),blend.fadeOut()));
    }
    public static boolean isLeg(String bone) { return PoseScope.isLeg(bone); }
}
