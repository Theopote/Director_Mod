package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;
import java.util.*;

/** Scope shortcuts restrict existing channels; they never create keys or change asset data. */
public enum ActorPresetScope {
    FULL("全身"), UPPER("仅上半身"), LOWER("仅双腿");
    private final String label;
    ActorPresetScope(String label) { this.label=label; }
    public String label() { return label; }
    public boolean supported(RigDefinition rig) { return rig!=null && (this==FULL || HumanoidTopology.matches(rig)); }
    public ActorPresetBinding apply(ActorPresetBinding binding,RigDefinition rig) {
        Objects.requireNonNull(binding);
        if(!supported(rig)) throw new IllegalArgumentException("此骨架没有标准人形部位分组，请逐个选择部位");
        var blend=binding.blend();var bones=new LinkedHashSet<String>();
        if(this!=FULL) for(int i=0;i<rig.boneCount();i++) {
            boolean included=this==UPPER?descendant(rig,i,HumanoidRig.BODY):
                    descendant(rig,i,HumanoidRig.LEFT_LEG)||descendant(rig,i,HumanoidRig.RIGHT_LEG);
            if(included) bones.add(rig.bone(i).id());
        }
        return new ActorPresetBinding(binding.animationId(),binding.loop(),binding.speed(),
                new PoseBlendSettings(blend.mode(),blend.weight(),blend.channels(),this==FULL,bones,blend.fadeIn(),blend.fadeOut()));
    }
    private static boolean descendant(RigDefinition rig,int index,String ancestor) {
        for(int i=index;i>=0;i=rig.bone(i).parentIndex()) if(rig.bone(i).id().equals(ancestor)) return true;
        return false;
    }
    public static String currentLabel(ActorPresetBinding binding,RigDefinition rig) {
        if(binding.blend().allBones()) return FULL.label();
        for(var scope:new ActorPresetScope[]{UPPER,LOWER})
            if(scope.supported(rig) && scope.apply(binding,rig).blend().boneIds().equals(binding.blend().boneIds())) return scope.label();
        return "自定义部位";
    }
    public static boolean isLeg(String bone) {
        return Set.of(HumanoidRig.LEFT_LEG,HumanoidRig.RIGHT_LEG,ArticulatedPlayerRig.LEFT_SHIN,ArticulatedPlayerRig.RIGHT_SHIN).contains(bone);
    }
}
