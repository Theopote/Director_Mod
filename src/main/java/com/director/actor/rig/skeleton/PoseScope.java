package com.director.actor.rig.skeleton;

import java.util.*;

/** Rig-domain membership rules, independent of UI labels, clips and asset ownership. */
public enum PoseScope {
    FULL, UPPER, LOWER;
    public boolean supported(RigDefinition rig) { return rig!=null && (this==FULL || HumanoidTopology.matches(rig)); }
    public PoseBlendSettings apply(PoseBlendSettings settings,RigDefinition rig) {
        Objects.requireNonNull(settings);
        if(!supported(rig)) throw new IllegalArgumentException("此骨架没有标准人形部位分组，请逐个选择部位");
        var bones=new LinkedHashSet<String>();
        if(this!=FULL) for(int i=0;i<rig.boneCount();i++) if(includes(rig,i)) bones.add(rig.bone(i).id());
        return new PoseBlendSettings(settings.mode(),settings.weight(),settings.channels(),this==FULL,bones,settings.fadeIn(),settings.fadeOut());
    }
    /** Exact membership comparison, without constructing a replacement binding/settings/set. */
    public boolean matches(PoseBlendSettings settings,RigDefinition rig) {
        if(this==FULL) return settings.allBones();
        if(settings.allBones() || !supported(rig)) return false;
        int count=0;
        for(int i=0;i<rig.boneCount();i++) if(includes(rig,i)) {
            count++;if(!settings.boneIds().contains(rig.bone(i).id())) return false;
        }
        return count==settings.boneIds().size();
    }
    private boolean includes(RigDefinition rig,int index) {
        return this==UPPER?descendant(rig,index,HumanoidRig.BODY):
                descendant(rig,index,HumanoidRig.LEFT_LEG)||descendant(rig,index,HumanoidRig.RIGHT_LEG);
    }
    private static boolean descendant(RigDefinition rig,int index,String ancestor) {
        for(int i=index;i>=0;i=rig.bone(i).parentIndex()) if(rig.bone(i).id().equals(ancestor)) return true;
        return false;
    }
    public static boolean isLeg(String bone) {
        return HumanoidRig.LEFT_LEG.equals(bone)||HumanoidRig.RIGHT_LEG.equals(bone)
                ||ArticulatedPlayerRig.LEFT_SHIN.equals(bone)||ArticulatedPlayerRig.RIGHT_SHIN.equals(bone);
    }
}
