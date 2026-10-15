package com.director.actor.rig.skeleton;
import java.util.ArrayList;

/** Player-only elbow and knee joints, with six-pixel lower segments. */
public final class ArticulatedPlayerRig {
    public static final String LEFT_FOREARM="left_forearm", RIGHT_FOREARM="right_forearm";
    public static final String LEFT_SHIN="left_shin", RIGHT_SHIN="right_shin";
    public static final RigDefinition DEFINITION;
    static {
        var bones=new ArrayList<>(HumanoidRig.DEFINITION.bones());
        bones.set(1,new BoneDefinition(HumanoidRig.BODY,"身体（腰）",0,BoneTransform.translation(0,.75f,0),BoneCapabilities.rotationAndScale()));
        bones.set(2,new BoneDefinition(HumanoidRig.HEAD,"头",1,BoneTransform.translation(0,.75f,0),BoneCapabilities.rotationAndScale()));
        bones.set(3,new BoneDefinition(HumanoidRig.LEFT_ARM,"左上臂",1,BoneTransform.translation(.3125f,.625f,0),BoneCapabilities.rotationAndScale()));
        bones.set(4,new BoneDefinition(HumanoidRig.RIGHT_ARM,"右上臂",1,BoneTransform.translation(-.3125f,.625f,0),BoneCapabilities.rotationAndScale()));
        bones.add(new BoneDefinition(LEFT_FOREARM,"左前臂（肘）",3,BoneTransform.translation(0,-.25f,0),BoneCapabilities.rotationAndScale()));
        bones.add(new BoneDefinition(RIGHT_FOREARM,"右前臂（肘）",4,BoneTransform.translation(0,-.25f,0),BoneCapabilities.rotationAndScale()));
        bones.add(new BoneDefinition(LEFT_SHIN,"左小腿（膝）",5,BoneTransform.translation(0,-.375f,0),BoneCapabilities.rotationAndScale()));
        bones.add(new BoneDefinition(RIGHT_SHIN,"右小腿（膝）",6,BoneTransform.translation(0,-.375f,0),BoneCapabilities.rotationAndScale()));
        DEFINITION=new RigDefinition("director:articulated_player","可弯曲玩家",bones);
    }
    private ArticulatedPlayerRig() {}
}
